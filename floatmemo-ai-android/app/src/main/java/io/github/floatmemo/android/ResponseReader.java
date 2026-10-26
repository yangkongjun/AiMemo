package io.github.floatmemo.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Single HTTP body reader: bounded JSON or incremental Chat Completions SSE. No reconnect. */
public final class ResponseReader {
    public static final int MAX_CONTENT=2*1024*1024,MAX_EVENT=256*1024,MAX_WIRE=32*1024*1024;
    public static final class Stats {
        final long start=System.nanoTime();
        public String mode="尚未读取正文",finishReason="",serviceCode="";
        public long wireBytes,firstBodyMs=-1,firstEventMs=-1,lastEventMs=-1,reasoningChars;
        public int events,heartbeats,visibleChars;
        public boolean doneMarker;
        public JSONObject usage;
        long elapsed(){return (System.nanoTime()-start)/1000000;}
        public void record(DiagnosticRecord d){d.put("实际接收方式",mode);d.put("响应正文字节数",wireBytes);d.put("首段响应正文毫秒",firstBodyMs);
            if(mode.equals("SSE")){d.put("首个流式事件毫秒",firstEventMs);d.put("最后流式事件毫秒",lastEventMs);d.put("流式事件数",events);d.put("流式心跳行数",heartbeats);d.put("已收到回答字符数",visibleChars);d.put("已收到推理字符数",reasoningChars);d.put("收到DONE标记",doneMarker);if(!finishReason.isEmpty())d.put("finish_reason",finishReason);if(!serviceCode.isEmpty())d.put("流式服务错误代码",serviceCode);}
        }
    }
    public static final class WireLimit extends IOException {WireLimit(){super("响应传输量超过本机32MiB限制，未保存不完整结果");}}
    private static final class Limited extends FilterInputStream {
        final Stats stats;final long limit;
        Limited(InputStream in,Stats s,long limit){super(in);stats=s;this.limit=limit;}
        void count(int n)throws IOException{if(n<=0)return;if(stats.firstBodyMs<0)stats.firstBodyMs=stats.elapsed();stats.wireBytes+=n;if(stats.wireBytes>limit)throw new WireLimit();}
        @Override public int read()throws IOException{int n=in.read();count(n<0?0:1);return n;}
        @Override public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);count(n);return n;}
    }
    public static String read(InputStream source,String contentType,Stats stats)throws Exception{
        if(source==null)return "";
        try(BufferedInputStream in=new BufferedInputStream(new Limited(source,stats,MAX_WIRE))){
            in.mark(4096);int c=in.read();if(c==0xef){int second=in.read(),third=in.read();if(second==0xbb&&third==0xbf)c=in.read();}int skipped=0;while(c>=0&&c<=32&&skipped++<4000)c=in.read();in.reset();
            boolean json=c=='{'||c=='[';boolean sse=!json&&((contentType!=null&&contentType.toLowerCase(Locale.ROOT).contains("text/event-stream"))||c=='d'||c==':'||c=='e'||c=='i'||c=='r');
            if(sse){stats.mode="SSE";return stream(in,stats);}
            stats.mode="JSON";try(ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>MAX_CONTENT)throw failure("RESPONSE_TOO_LARGE","返回正文超过本机2MiB限制");out.write(b,0,n);}String body=out.toString("UTF-8");return body.startsWith("\uFEFF")?body.substring(1):body;}
        }
    }
    private static String stream(InputStream input,Stats stats)throws Exception{
        BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)));
        State state=new State(stats);StringBuilder event=new StringBuilder();String line;boolean first=true;
        while((line=line(reader))!=null){if(first){first=false;if(line.startsWith("\uFEFF"))line=line.substring(1);}
            if(line.isEmpty()){if(event.length()>0){String payload=event.substring(0,event.length()-1);event.setLength(0);if(state.event(payload))break;}continue;}
            if(line.startsWith(":")){stats.heartbeats++;continue;}
            int colon=line.indexOf(':');String field=colon<0?line:line.substring(0,colon);if(!field.equals("data"))continue;
            String value=colon<0?"":line.substring(colon+1);if(value.startsWith(" "))value=value.substring(1);
            if(event.length()+value.length()+1>MAX_EVENT)throw failure("STREAM_EVENT_TOO_LARGE","单个流式事件过大，未保存不完整结果");event.append(value).append('\n');
        }
        // A final un-delimited event is not dispatched; partial data is never promoted to a result.
        if(event.length()>0||stats.finishReason.isEmpty())throw failure("STREAM_INCOMPLETE","流式回答未完整结束；原文保留，请查看已收到片段数量与断连阶段");
        return state.envelope();
    }
    private static String line(BufferedReader reader)throws Exception{
        StringBuilder out=new StringBuilder();int c;while((c=reader.read())!=-1){if(c=='\n')return out.toString();if(c=='\r'){reader.mark(1);if(reader.read()!='\n')reader.reset();return out.toString();}if(out.length()>=MAX_EVENT)throw failure("STREAM_EVENT_TOO_LARGE","流式行超过本机大小限制");out.append((char)c);}return out.length()==0?null:out.toString();
    }
    private static final class State {
        final Stats stats;final StringBuilder content=new StringBuilder();int contentBytes;
        State(Stats stats){this.stats=stats;}
        boolean event(String payload)throws Exception{
            stats.events++;if(stats.events>100000)throw failure("STREAM_TOO_MANY_EVENTS","流式事件过多，已停止读取");long now=stats.elapsed();if(stats.firstEventMs<0)stats.firstEventMs=now;stats.lastEventMs=now;
            if(payload.trim().isEmpty()){stats.heartbeats++;return false;}
            if(payload.trim().equals("[DONE]")){stats.doneMarker=true;return true;}
            JSONObject root;try{root=new JSONObject(payload);}catch(JSONException e){throw failure("INVALID_STREAM","流式事件不是有效JSON，未保存结果；可关闭流式接收检查接口兼容性");}
            if(root.has("error")&&!root.isNull("error")){JSONObject error=root.optJSONObject("error");String code=error==null?"":error.optString("code","");if(code.matches("[A-Za-z0-9_.-]{1,80}"))stats.serviceCode=code;throw failure("STREAM_SERVICE_ERROR","服务在流式回答中返回错误；未保存不完整结果，请查看诊断");}
            JSONObject usage=root.optJSONObject("usage");if(usage!=null){JSONObject clean=new JSONObject();for(String key:new String[]{"prompt_tokens","completion_tokens","total_tokens"})if(usage.has(key)&&usage.optLong(key,-1)>=0)clean.put(key,usage.optLong(key));stats.usage=clean;}
            JSONArray choices=root.optJSONArray("choices");if(choices==null){if(usage!=null)return false;throw failure("INVALID_STREAM","流式事件缺少choices，可能不是Chat Completions协议");}
            for(int i=0;i<choices.length();i++){JSONObject choice=choices.optJSONObject(i);if(choice==null)throw failure("INVALID_STREAM","流式choices格式无效");if(choice.optInt("index",0)!=0)continue;
                JSONObject delta=choice.optJSONObject("delta"),message=choice.optJSONObject("message");
                Object value=delta==null?null:delta.opt("content");boolean snapshot=false;
                if((value==null||value==JSONObject.NULL)&&message!=null){value=message.opt("content");snapshot=true;}
                if(value!=null&&value!=JSONObject.NULL&&!(value instanceof String))throw failure("INVALID_STREAM","流式content必须是文字");
                String text=value instanceof String?(String)value:"";if(!text.isEmpty()){
                    if(!stats.finishReason.isEmpty())throw failure("INVALID_STREAM","结束标记后仍出现新回答片段");
                    if(snapshot&&content.length()>0){String prior=content.toString();if(!text.startsWith(prior))throw failure("INVALID_STREAM","流式完整消息与已收到片段不一致");text=text.substring(prior.length());}
                    contentBytes+=text.getBytes(StandardCharsets.UTF_8).length;if(contentBytes>MAX_CONTENT)throw failure("RESPONSE_TOO_LARGE","累积回答超过本机2MiB限制");content.append(text);stats.visibleChars=content.length();
                }
                if(delta!=null){Object reasoning=delta.opt("reasoning_content");if(reasoning instanceof String)stats.reasoningChars+=((String)reasoning).length();}
                String finish=choice.optString("finish_reason","");if(!finish.isEmpty()&&!finish.equals("null")){if(!finish.equals("stop")&&!finish.equals("length"))throw failure("STREAM_FINISH_REASON","流式回答以非正常原因结束，未保存结果");if(!stats.finishReason.isEmpty()&&!stats.finishReason.equals(finish))throw failure("INVALID_STREAM","流式结束原因前后不一致，未保存结果");stats.finishReason=finish;}
            }return false;
        }
        String envelope()throws Exception{return new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("finish_reason",stats.finishReason).put("message",new JSONObject().put("role","assistant").put("content",content.toString())))).put("usage",stats.usage==null?JSONObject.NULL:stats.usage).toString();}
    }
    private static ApiResponse.Failure failure(String kind,String hint){return new ApiResponse.Failure(kind,hint);}
}

package io.github.floatmemo.android;

import org.json.*;
import java.util.*;

/** Bound and redact before persistence; never collect request bodies or headers. */
public final class DiagnosticRecord {
    public static final int PREVIEW_LIMIT=4000;
    public final JSONObject value=new JSONObject();
    private final long start=System.nanoTime();
    public DiagnosticRecord(String operation){
        put("记录ID",UUID.randomUUID().toString());put("时间戳",System.currentTimeMillis());
        put("操作",operation);put("阶段","配置检查");put("HTTP状态",-1);
    }
    public void put(String name,Object item){try{value.put(name,item);}catch(Exception ignored){}}
    public void stage(String name){put("阶段",name);}
    public void end(boolean ok,String code,String hint){put("结果",ok?"成功":"失败");put("错误类型",code);put("判断线索",hint);put("耗时毫秒",(System.nanoTime()-start)/1000000);}
    public static String bounded(String s,int limit){
        if(s==null)return "";
        if(s.length()<=limit)return s;
        int end=limit;if(end>0&&Character.isHighSurrogate(s.charAt(end-1)))end--;
        return s.substring(0,end)+"…[已截断]";
    }
    public static String redact(String text,String key,String... privateText){
        String s=text==null?"":text;
        if(key!=null&&!key.isEmpty()){
            s=s.replace(key,"[密钥已隐藏]");
            s=s.replace(JSONObject.quote(key).substring(1,JSONObject.quote(key).length()-1),"[密钥已隐藏]");
        }
        for(String secret:privateText)if(secret!=null&&secret.length()>4){
            s=s.replace(secret,"[请求内容已隐藏]");
            String quoted=JSONObject.quote(secret);s=s.replace(quoted.substring(1,quoted.length()-1),"[请求内容已隐藏]");
        }
        s=s.replaceAll("(?i)Bearer\\s+[^\\s\\\"'<>]+","Bearer [已隐藏]");
        s=s.replaceAll("(?i)([\\\"']?(?:api[_-]?key|authorization|access[_-]?token|token|secret|password)[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\s\\\"',}<>]+","$1[已隐藏]");
        s=s.replaceAll("\\bsk-[A-Za-z0-9_-]{6,}\\b","[密钥已隐藏]");
        return s;
    }
    private static boolean privateField(String name){
        String n=name.toLowerCase(Locale.ROOT).replace("_","").replace("-","");
        return Arrays.asList("authorization","apikey","key","token","accesstoken","secret","password",
            "content","reasoningcontent","reasoning","messages","prompt","input","text","originalnote","originaltitle","query","notes","title","tags",
            "quote","summary","summaries","points","examples","advice","questions").contains(n);
    }
    private static Object clean(Object object,int depth,String key,String... privateText)throws Exception{
        if(depth>6)return "[深层内容已省略]";
        if(object instanceof JSONObject){JSONObject in=(JSONObject)object,out=new JSONObject();Iterator<String> names=in.keys();
            int count=0;while(names.hasNext()&&count++<40){String name=names.next();out.put(redact(name,key,privateText),privateField(name)?"[已隐藏]":clean(in.get(name),depth+1,key,privateText));}return out;}
        if(object instanceof JSONArray){JSONArray out=new JSONArray(),in=(JSONArray)object;for(int i=0;i<Math.min(in.length(),20);i++)out.put(clean(in.get(i),depth+1,key,privateText));if(in.length()>20)out.put("[其余项已省略]");return out;}
        if(object instanceof String)return redact((String)object,key,privateText);
        return object;
    }
    public static String responsePreview(String body,String key,String... privateText){
        String result;
        try{result=((JSONObject)clean(new JSONObject(body),0,key,privateText)).toString(2);}
        catch(Exception ignored){result=redact(body,key,privateText);}
        return bounded(result,PREVIEW_LIMIT);
    }
}

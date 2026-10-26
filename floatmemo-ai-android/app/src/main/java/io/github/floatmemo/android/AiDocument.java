package io.github.floatmemo.android;
import org.json.*;
import java.util.*;

/** Validates source quotes before generated results become visible. */
public final class AiDocument {
    private AiDocument(){}
    public static JSONObject parse(String response,String source)throws Exception{
        String s=response.trim();if(s.startsWith("```")){int start=s.indexOf('\n'),end=s.lastIndexOf("```");if(start>=0&&end>start)s=s.substring(start+1,end);}
        JSONObject in=new JSONObject(s),out=new JSONObject();
        String title=in.optString("title","").trim();if(title.length()>120)throw new Exception("标题过长");out.put("title",title);
        JSONArray summaries=new JSONArray();if(in.has("summary")&&!in.isNull("summary"))summaries.put(item(in.getJSONObject("summary"),source));
        out.put("summaries",summaries);
        for(String key:new String[]{"points","examples","advice"}){
            JSONArray a=in.optJSONArray(key),values=new JSONArray();if(a!=null){if(a.length()>20)throw new Exception("输出条目过多");for(int i=0;i<a.length();i++)values.put(item(a.getJSONObject(i),source));}out.put(key,values);
        }
        if(summaries.length()==0&&out.getJSONArray("points").length()==0)throw new Exception("缺少有来源的概括或要点");
        String f=in.optString("folder","").trim();if(f.length()>60)throw new Exception("主题名称过长");out.put("folder",f);
        JSONArray tags=new JSONArray();LinkedHashSet<String> seen=new LinkedHashSet<>();JSONArray raw=in.optJSONArray("tags");
        if(raw!=null)for(int i=0;i<raw.length()&&tags.length()<8;i++){String t=raw.getString(i).trim();if(!t.isEmpty()&&t.length()<=30&&seen.add(t.toLowerCase(Locale.ROOT)))tags.put(t);}out.put("tags",tags);
        JSONArray questions=new JSONArray();JSONArray q=in.optJSONArray("questions");if(q!=null)for(int i=0;i<q.length()&&i<8;i++){String v=q.getString(i).trim();if(!v.isEmpty()&&v.length()<250)questions.put(v);}out.put("questions",questions);
        return out;
    }
    private static JSONObject item(JSONObject j,String raw)throws Exception{
        String text=j.getString("text").trim(),quote=j.getString("quote").trim();
        if(text.isEmpty()||text.length()>1200||quote.isEmpty()||!raw.contains(quote))throw new Exception("要点缺少有效原文依据，请重试或检查原文");
        return new JSONObject().put("text",text).put("quote",quote).put("manual",false);
    }
    public static boolean validQuotes(String json,String raw){
        try{JSONObject j=new JSONObject(json);for(String key:new String[]{"summaries","points","examples","advice"}){JSONArray a=j.optJSONArray(key);if(a!=null)for(int i=0;i<a.length();i++)if(NoteBodyParser.locate(raw,a.getJSONObject(i).getString("quote"))<0)return false;}return true;}catch(Exception e){return false;}
    }
    public static boolean current(Note n){return !n.aiJson.isEmpty()&&!n.aiSourceHash.isEmpty()&&n.aiSourceHash.equals(NoteBodyParser.fingerprint(n))&&validQuotes(n.aiJson,n.content);}
    public static void migrateFingerprint(Note n){if(n.aiSourceHash.isEmpty()&&n.aiRevision==n.revision&&!n.aiJson.isEmpty()&&validQuotes(n.aiJson,n.content))n.aiSourceHash=NoteBodyParser.fingerprint(n);}
    public static List<String> chunks(String text){
        List<String> result=new ArrayList<>();int start=0;
        while(start<text.length()){int end=Math.min(start+10000,text.length());
            if(end<text.length()){int line=text.lastIndexOf('\n',end);if(line>start+5000)end=line+1;else if(Character.isHighSurrogate(text.charAt(end-1)))end--;}
            result.add(text.substring(start,end));start=end;}return result;
    }
    public static JSONObject combine(List<JSONObject> parts)throws Exception{
        JSONObject out=new JSONObject(parts.get(0).toString());LinkedHashSet<String> tags=new LinkedHashSet<>();
        for(String key:new String[]{"summaries","points","examples","advice","questions"}){JSONArray a=new JSONArray();for(JSONObject p:parts){JSONArray list=p.getJSONArray(key);for(int i=0;i<list.length();i++)a.put(list.get(i));}out.put(key,a);}
        for(JSONObject p:parts){JSONArray a=p.getJSONArray("tags");for(int i=0;i<a.length();i++)tags.add(a.getString(i));}
        JSONArray a=new JSONArray();for(String t:tags){if(a.length()==8)break;a.put(t);}out.put("tags",a).put("parts",parts.size());return out;
    }
}

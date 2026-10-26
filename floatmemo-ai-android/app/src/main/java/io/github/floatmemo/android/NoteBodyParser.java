package io.github.floatmemo.android;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Original offsets are kept; an AI quote must fit within one contiguous text span. */
public final class NoteBodyParser {
    private static final Pattern IMAGE=Pattern.compile("!\\[[^\\]\\r\\n]*\\]\\(attachment://([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})\\)");
    public static final class Part {
        public final int start,end;public final String imageId,text;
        private Part(int start,int end,String id,String raw){this.start=start;this.end=end;imageId=id;text=id.isEmpty()?raw.substring(start,end):"";}
    }
    public static List<Part> parts(String raw){
        List<Part> out=new ArrayList<>();Matcher m=IMAGE.matcher(raw);int cursor=0;
        while(m.find()){if(m.start()>cursor)out.add(new Part(cursor,m.start(),"",raw));out.add(new Part(m.start(),m.end(),m.group(1),raw));cursor=m.end();}
        if(cursor<raw.length())out.add(new Part(cursor,raw.length(),"",raw));return out;
    }
    public static List<String> spans(String raw,int start,int end){List<String> spans=new ArrayList<>();int at=0;for(Part p:parts(raw))if(p.imageId.isEmpty()){int left=Math.max(start,at),right=Math.min(end,at+p.text.length());if(right>left)spans.add(p.text.substring(left-at,right-at));at+=p.text.length();}return spans;}
    public static String text(String raw){return IMAGE.matcher(raw).replaceAll("");}
    public static void ensureAiSafe(String serialized){if(serialized.replace("\\/","/").contains("attachment://"))throw new IllegalArgumentException("文字含有未完整的图片占位符或附件地址，请先删除完整标记再重试；本次未发送请求");}
    public static Set<String> ids(String raw){Set<String> out=new LinkedHashSet<>();Matcher m=IMAGE.matcher(raw);while(m.find())out.add(m.group(1));return out;}
    public static int locate(String raw,String quote){
        if(quote==null||quote.trim().isEmpty())return -1;
        for(Part p:parts(raw))if(p.imageId.isEmpty()){int at=p.text.indexOf(quote);if(at>=0)return p.start+at;}return -1;
    }
    public static int floorBoundary(String value,int at){int p=Math.max(0,Math.min(at,value.length()));if(p>0&&p<value.length()&&Character.isLowSurrogate(value.charAt(p))&&Character.isHighSurrogate(value.charAt(p-1)))p--;return p;}
    public static String bounded(String value,int max){return value.substring(0,floorBoundary(value,Math.min(value.length(),max)));}
    public static String hash(String value){
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static String fingerprint(Note n){return hash(n.title.length()+":"+n.title+text(n.content));}
    public static List<Attachment> referenced(Note n){
        Set<String> ids=ids(n.content);List<Attachment> out=new ArrayList<>();for(Attachment a:n.attachments)if(ids.contains(a.id))out.add(a);return out;
    }
    public static void validate(Note n){
        Map<String,Attachment> known=new HashMap<>();for(Attachment a:n.attachments){if(known.put(a.id,a)!=null)throw new IllegalArgumentException("重复图片标识");}
        int count=0;for(Part part:parts(n.content))if(!part.imageId.isEmpty())count++;Set<String> ids=ids(n.content);if(count>Attachment.MAX_PER_NOTE)throw new IllegalArgumentException("每篇最多20张图片");
        long total=0;for(String id:ids){Attachment a=known.get(id);if(a==null)throw new IllegalArgumentException("图片引用缺少附件信息，请先恢复完整备份");total+=a.bytes;}
        if(total>Attachment.MAX_NOTE_BYTES)throw new IllegalArgumentException("每篇原图合计最多100MiB");
    }
}

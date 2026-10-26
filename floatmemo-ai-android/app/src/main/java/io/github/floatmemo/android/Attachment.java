package io.github.floatmemo.android;

import org.json.*;
import java.util.*;

/** Immutable metadata; private filenames are derived solely from validated UUIDs. */
public final class Attachment {
    public static final long MAX_BYTES=20L*1024*1024, MAX_NOTE_BYTES=100L*1024*1024;
    public static final int MAX_PER_NOTE=20;
    public final String id,mime,sha256;
    public final long bytes;
    public final int width,height;
    public Attachment(String id,String mime,long bytes,int width,int height,String hash){
        if(!validId(id)||!Arrays.asList("image/jpeg","image/png","image/webp").contains(mime)
            ||bytes<1||bytes>MAX_BYTES||width<1||height<1||width>40000||height>40000||(long)width*height>100000000
            ||!hash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("图片格式、大小、尺寸或标识无效");
        this.id=id;this.mime=mime;this.bytes=bytes;this.width=width;this.height=height;sha256=hash;
    }
    public static boolean validId(String id){return id!=null&&id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");}
    public String marker(){return "![图片](attachment://"+id+")";}
    public Attachment withId(String value){return new Attachment(value,mime,bytes,width,height,sha256);}
    public JSONObject toJson()throws JSONException{return new JSONObject().put("id",id).put("mime",mime).put("bytes",bytes).put("width",width).put("height",height).put("sha256",sha256);}
    public static Attachment fromJson(JSONObject j)throws JSONException{return new Attachment(j.getString("id"),j.getString("mime"),j.getLong("bytes"),j.getInt("width"),j.getInt("height"),j.getString("sha256"));}
    public boolean same(Attachment other){return other!=null&&mime.equals(other.mime)&&bytes==other.bytes&&width==other.width&&height==other.height&&sha256.equals(other.sha256);}
}

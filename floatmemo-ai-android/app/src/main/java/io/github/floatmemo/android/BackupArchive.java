package io.github.floatmemo.android;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Streaming media archive; validation finishes in a disposable directory before install. */
public final class BackupArchive {
    public static final long MAX_ARCHIVE=500L*1024*1024,MAX_EXPANDED=1024L*1024*1024;
    public static final int MAX_JSON=20*1024*1024;
    public static final class Prepared implements AutoCloseable {
        public final File directory,assets;public final JSONObject root;public final boolean complete;
        Prepared(File dir,JSONObject root,boolean complete){directory=dir;assets=new File(dir,"assets");this.root=root;this.complete=complete;}
        @Override public void close(){erase(directory);}
    }
    public static long copy(InputStream in,OutputStream out,long limit)throws IOException{
        byte[] buffer=new byte[32768];long total=0;int read;while((read=in.read(buffer))!=-1){total+=read;if(total>limit)throw new IOException("文件超过允许大小");out.write(buffer,0,read);}return total;
    }
    public static String sha256(File file)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static byte[] jsonBytes(JSONObject value)throws Exception{byte[] bytes=value.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_JSON)throw new IOException("笔记文字及历史超过20MiB备份上限，请分批整理后再导出");return bytes;}
    private static JSONObject readJson(File file)throws Exception{if(file.length()>MAX_JSON)throw new IOException("备份JSON超过20MiB");String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(file.toPath()))).toString();if(text.startsWith("\uFEFF"))text=text.substring(1);return new JSONObject(text);}
    private static List<JSONObject> rawNotes(JSONObject root)throws JSONException{
        List<JSONObject> notes=new ArrayList<>();for(String key:new String[]{"notes","history"}){JSONArray a=root.optJSONArray(key);if(a!=null)for(int i=0;i<a.length();i++)notes.add(a.getJSONObject(i));}
        JSONArray edits=root.optJSONArray("edits");if(edits!=null)for(int i=0;i<edits.length();i++)notes.add(edits.getJSONObject(i).getJSONObject("before"));return notes;
    }
    public static Map<String,Attachment> assets(JSONObject root)throws Exception{
        Map<String,Attachment> all=new LinkedHashMap<>();for(JSONObject raw:rawNotes(root)){
            Note n=Note.fromJson(raw);NoteBodyParser.validate(n);
            for(Attachment a:NoteBodyParser.referenced(n)){Attachment old=all.put(a.id,a);if(old!=null&&!old.same(a))throw new IOException("同一图片标识包含不同原图");}
        }return all;
    }
    public static JSONObject remap(JSONObject original,Map<String,String> map)throws Exception{
        JSONObject root=new JSONObject(original.toString());for(JSONObject raw:rawNotes(root)){
            String content=raw.optString("content","");for(Map.Entry<String,String> e:map.entrySet())if(!e.getKey().equals(e.getValue()))content=content.replace("attachment://"+e.getKey()+")","attachment://"+e.getValue()+")");raw.put("content",content);
            JSONArray a=raw.optJSONArray("attachments");if(a!=null)for(int i=0;i<a.length();i++){JSONObject metadata=a.getJSONObject(i);String id=metadata.getString("id");if(map.containsKey(id))metadata.put("id",map.get(id));}
        }return root;
    }
    /** Deterministic collision IDs make repeated imports idempotent, including missing-file repair. */
    public static Map<String,String> destinationIds(Map<String,Attachment> incoming,Map<String,Attachment> existing,File files)throws Exception{
        Map<String,String> remap=new HashMap<>();Map<String,String> assigned=new HashMap<>();Map<String,Attachment> occupied=new HashMap<>(existing);
        for(Attachment a:incoming.values()){
            String target=a.id;int attempt=0;
            while((assigned.containsKey(target)&&!assigned.get(target).equals(a.id))||(occupied.containsKey(target)&&!occupied.get(target).same(a))||(new File(files,target).exists()&&!sha256(new File(files,target)).equals(a.sha256)))
                target=UUID.nameUUIDFromBytes((a.id+":"+a.sha256+":"+(attempt++)).getBytes(StandardCharsets.UTF_8)).toString();
            remap.put(a.id,target);assigned.put(target,a.id);occupied.put(target,a.withId(target));
        }return remap;
    }
    public static void write(File target,JSONObject snapshot,File assetDirectory)throws Exception{
        JSONObject root=new JSONObject(snapshot.toString()).put("schemaVersion",3).put("includesImageFiles",true);
        Map<String,Attachment> assets=assets(root);byte[] data=jsonBytes(root);long total=data.length;JSONArray manifestAssets=new JSONArray();
        for(Attachment a:assets.values()){
            File file=new File(assetDirectory,a.id);if(!file.isFile()||file.length()!=a.bytes||!sha256(file).equals(a.sha256))throw new IOException("图片副本缺失或损坏，请先恢复完整备份："+a.id);
            total+=a.bytes;if(total>MAX_EXPANDED)throw new IOException("完整备份展开后最多1GiB");manifestAssets.put(a.toJson().put("path","assets/"+a.id));
        }
        File dataFile=File.createTempFile("notes-snapshot-",".json",target.getParentFile());
        try{
            try(FileOutputStream out=new FileOutputStream(dataFile)){out.write(data);out.getFD().sync();}
            JSONObject manifest=new JSONObject().put("format","floatmemo-ai-backup").put("version",1).put("data",new JSONObject().put("path","notes.json").put("bytes",data.length).put("sha256",sha256(dataFile))).put("assets",manifestAssets);
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)))){
                zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(jsonBytes(manifest));zip.closeEntry();zip.putNextEntry(new ZipEntry("notes.json"));zip.write(data);zip.closeEntry();
                for(Attachment a:assets.values()){zip.putNextEntry(new ZipEntry("assets/"+a.id));try(InputStream in=new FileInputStream(new File(assetDirectory,a.id))){if(copy(in,zip,a.bytes)!=a.bytes)throw new IOException("备份时图片发生变化");}zip.closeEntry();}
            }if(target.length()>MAX_ARCHIVE)throw new IOException("完整ZIP备份最大500MiB");
        }catch(Exception e){target.delete();throw e;}finally{dataFile.delete();}
    }
    public static Prepared read(File input,File temporaryParent)throws Exception{
        if(input.length()>MAX_ARCHIVE)throw new IOException("备份文件最大500MiB");
        File dir=Files.createTempDirectory(temporaryParent.toPath(),"memo-restore-").toFile();File assetDir=new File(dir,"assets");if(!assetDir.mkdirs()){erase(dir);throw new IOException("无法创建恢复目录");}
        try{
            boolean zip;try(InputStream in=new FileInputStream(input)){zip=in.read()=='P'&&in.read()=='K';}
            if(!zip){JSONObject root=readJson(input);checkRoot(root);return new Prepared(dir,root,false);}
            Set<String> seen=new HashSet<>();long total=0;Map<String,ZipEntry> central=new HashMap<>();
            // Require an intact central directory as well as per-entry CRCs.
            try(ZipFile archive=new ZipFile(input)){Enumeration<? extends ZipEntry> rows=archive.entries();long declared=0;while(rows.hasMoreElements()){ZipEntry e=rows.nextElement();if(central.size()>=50000||central.put(e.getName(),e)!=null||e.getSize()<0)throw new IOException("ZIP目录损坏或条目重复");declared+=e.getSize();if(declared>MAX_EXPANDED)throw new IOException("备份展开后超过1GiB");}}
            try(ZipInputStream in=new ZipInputStream(new BufferedInputStream(new FileInputStream(input)))){
                ZipEntry entry;while((entry=in.getNextEntry())!=null){String name=entry.getName();if(seen.size()>=50000||!seen.add(name)||entry.isDirectory()||!(name.equals("manifest.json")||name.equals("notes.json")||name.matches("assets/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")))throw new IOException("备份包含重复、越界或未知文件路径");
                    File target=new File(dir,name);long limit=name.startsWith("assets/")?Attachment.MAX_BYTES:MAX_JSON;
                    long bytes;try(FileOutputStream out=new FileOutputStream(target)){bytes=copy(in,out,Math.min(limit,MAX_EXPANDED-total));total+=bytes;out.getFD().sync();}in.closeEntry();
                    ZipEntry declared=central.get(name);if(declared==null||declared.getSize()!=bytes||declared.getCrc()!=entry.getCrc())throw new IOException("ZIP目录与数据不一致");
                }
            }
            if(!central.keySet().equals(seen))throw new IOException("ZIP目录与文件不一致");
            if(!seen.contains("manifest.json")||!seen.contains("notes.json"))throw new IOException("ZIP缺少清单或笔记数据");
            JSONObject manifest=readJson(new File(dir,"manifest.json")),root=readJson(new File(dir,"notes.json"));
            if(!"floatmemo-ai-backup".equals(manifest.optString("format"))||manifest.optInt("version")!=1)throw new IOException("不支持的ZIP备份格式");checkRoot(root);
            JSONObject data=manifest.getJSONObject("data");File notesFile=new File(dir,"notes.json");if(!data.getString("path").equals("notes.json")||data.getLong("bytes")!=notesFile.length()||!data.getString("sha256").equals(sha256(notesFile)))throw new IOException("笔记数据校验失败");
            Map<String,Attachment> references=assets(root);JSONArray list=manifest.getJSONArray("assets");Set<String> verified=new HashSet<>();
            for(int i=0;i<list.length();i++){JSONObject row=list.getJSONObject(i);Attachment a=Attachment.fromJson(row);String path="assets/"+a.id;File f=new File(dir,path);
                if(!row.getString("path").equals(path)||!verified.add(a.id)||!a.same(references.get(a.id))||!f.isFile()||f.length()!=a.bytes||!sha256(f).equals(a.sha256))throw new IOException("备份图片校验失败");}
            if(verified.size()!=references.size()||seen.size()!=verified.size()+2)throw new IOException("备份图片与清单不完整或不一致");return new Prepared(dir,root,true);
        }catch(Exception e){erase(dir);throw e;}
    }
    public static void checkRoot(JSONObject root)throws Exception{int version=root.optInt("schemaVersion",1);if(version<1||version>3)throw new IOException("不支持的备份版本");JSONArray a=root.getJSONArray("notes");if(a.length()>10000)throw new IOException("单次最多导入10000条笔记");}
    public static void erase(File f){if(f==null||!f.exists())return;File[] children=f.listFiles();if(children!=null)for(File c:children)erase(c);f.delete();}
}

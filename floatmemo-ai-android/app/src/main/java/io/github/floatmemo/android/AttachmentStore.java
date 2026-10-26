package io.github.floatmemo.android;

import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Immutable, private copies of selected images. Never stores source URIs. */
public final class AttachmentStore {
    public final File directory;
    private final Context context;
    public AttachmentStore(Context c){context=c.getApplicationContext();directory=new File(context.getFilesDir(),"attachments");if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("无法创建图片目录");}
    public File file(String id){if(!Attachment.validId(id))throw new IllegalArgumentException("无效图片标识");return new File(directory,id);}
    public Attachment copy(Uri uri)throws Exception{
        String id=UUID.randomUUID().toString();File temp=new File(directory,id+".part"),target=file(id);
        try{
            try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("无法读取所选图片");BackupArchive.copy(in,out,Attachment.MAX_BYTES);out.getFD().sync();}
            Attachment metadata=inspect(temp,id);
            if(!temp.renameTo(target))throw new IOException("无法保存图片副本");return metadata;
        }finally{temp.delete();}
    }
    public static Attachment inspect(File f,String id)throws Exception{
        if(f.length()<1||f.length()>Attachment.MAX_BYTES)throw new IOException("单张图片最多20MiB");
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),o);
        rejectAnimation(f,o.outMimeType);
        try{Attachment a=new Attachment(id,o.outMimeType,f.length(),o.outWidth,o.outHeight,BackupArchive.sha256(f));Bitmap probe=sample(f,64);probe.recycle();return a;}
        catch(OutOfMemoryError e){throw new IOException("图片解码内存不足，请缩小尺寸后重试",e);}
        catch(IllegalArgumentException e){throw new IOException("仅支持JPEG、PNG、静态WebP；图片尺寸最多40000边长及1亿像素",e);}
    }
    static void rejectAnimation(File f,String mime)throws Exception{
        try(RandomAccessFile r=new RandomAccessFile(f,"r")){
            if("image/png".equals(mime)){
                r.seek(8);while(r.getFilePointer()+12<=r.length()){long size=((long)r.readInt())&0xffffffffL;byte[] type=new byte[4];r.readFully(type);String chunk=new String(type,java.nio.charset.StandardCharsets.US_ASCII);
                    if(chunk.equals("acTL"))throw new IOException("暂不支持动画PNG");if(size>r.length()-r.getFilePointer()-4)throw new IOException("PNG数据损坏");r.seek(r.getFilePointer()+size+4);if(chunk.equals("IEND"))break;}
            }else if("image/webp".equals(mime)){
                r.seek(12);while(r.getFilePointer()+8<=r.length()){byte[] type=new byte[4];r.readFully(type);long size=Integer.toUnsignedLong(Integer.reverseBytes(r.readInt()));String chunk=new String(type,java.nio.charset.StandardCharsets.US_ASCII);
                    if(chunk.equals("ANIM")||chunk.equals("ANMF"))throw new IOException("暂不支持动画WebP");long start=r.getFilePointer();if(size>r.length()-start)throw new IOException("WebP数据损坏");if(chunk.equals("VP8X")&&size>0&&(r.readUnsignedByte()&2)!=0)throw new IOException("暂不支持动画WebP");r.seek(start+size+(size&1));}
            }
        }
    }
    public static Bitmap sample(File file,int maxEdge)throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),o);
        if(o.outWidth<1||o.outHeight<1)throw new IOException("图片无法解码");int sample=1;
        while(Math.max(o.outWidth,o.outHeight)/sample>maxEdge)sample*=2;
        o.inJustDecodeBounds=false;o.inSampleSize=sample;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap b=BitmapFactory.decodeFile(file.getAbsolutePath(),o);if(b==null)throw new IOException("图片数据损坏");
        if("image/jpeg".equals(o.outMimeType))try{int orientation=new ExifInterface(file.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();
            switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;default:break;}
            if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}
        }catch(IOException ignored){}return b;
    }
    public void validateStaged(BackupArchive.Prepared prepared)throws Exception{for(Attachment a:BackupArchive.assets(prepared.root).values()){Attachment actual=inspect(new File(prepared.assets,a.id),a.id);if(!actual.same(a))throw new IOException("备份图片格式或尺寸与清单不一致");}}
    /** Stage files first; a journal survives process death between file and DB commits. */
    public AssetTransaction install(BackupArchive.Prepared prepared,Map<String,Attachment> existing)throws Exception{
        Map<String,Attachment> incoming=BackupArchive.assets(prepared.root);Map<String,String> remap=BackupArchive.destinationIds(incoming,existing,directory);
        JSONObject transformed=BackupArchive.remap(prepared.root,remap);List<String> created=new ArrayList<>();
        for(Attachment a:incoming.values()){String target=remap.get(a.id);if(!file(target).exists())created.add(target);}
        AssetTransaction tx=new AssetTransaction(context.getFilesDir(),UUID.randomUUID().toString(),created);tx.write();
        try{for(Attachment a:incoming.values()){String target=remap.get(a.id);File destination=file(target);if(destination.exists())continue;
                File temp=new File(directory,target+".part");try(InputStream in=new FileInputStream(new File(prepared.assets,a.id));FileOutputStream out=new FileOutputStream(temp)){BackupArchive.copy(in,out,Attachment.MAX_BYTES);out.getFD().sync();}
                if(!temp.renameTo(destination))throw new IOException("恢复图片失败");}
            tx.root=transformed;return tx;
        }catch(Exception e){tx.rollback(directory,existing.keySet());throw e;}
    }
    public static final class AssetTransaction {
        public final String id;public final List<String> created;public JSONObject root;private final File journal;
        AssetTransaction(File root,String id,List<String> created){this.id=id;this.created=created;journal=new File(root,"attachment-import-journal.json");}
        void write()throws Exception{JSONObject j=new JSONObject().put("id",id).put("created",new JSONArray(created));File temp=new File(journal.getParentFile(),journal.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}if(!temp.renameTo(journal))throw new IOException("无法保存图片恢复记录");}
        public void finish()throws IOException{if(journal.exists()&&!journal.delete())throw new IOException("恢复已完成，但清理恢复记录失败；下次打开将重试");}
        public void rollback(File directory,Set<String> retained)throws IOException{for(String id:created)if(!retained.contains(id)){if(!Attachment.validId(id))throw new IOException("无效图片恢复标识");File f=new File(directory,id);if(f.exists()&&!f.delete())throw new IOException("恢复清理失败，请保留数据并重启应用");}finish();}
    }
    public void pruneOldUnreferenced(Set<String> retained){long before=System.currentTimeMillis()-24*60*60*1000L;File[] all=directory.listFiles();if(all!=null)for(File f:all)if(f.isFile()&&f.lastModified()<before&&!retained.contains(f.getName())&&(Attachment.validId(f.getName())||f.getName().endsWith(".part")))f.delete();}
    public AssetTransaction unfinished()throws Exception{File journal=new File(context.getFilesDir(),"attachment-import-journal.json");if(!journal.exists())return null;JSONObject j=new JSONObject(new String(Files.readAllBytes(journal.toPath()),java.nio.charset.StandardCharsets.UTF_8));List<String> ids=new ArrayList<>();JSONArray a=j.getJSONArray("created");for(int i=0;i<a.length();i++){String id=a.getString(i);if(!Attachment.validId(id))throw new IOException("恢复记录损坏");ids.add(id);}return new AssetTransaction(context.getFilesDir(),j.getString("id"),ids);}
}

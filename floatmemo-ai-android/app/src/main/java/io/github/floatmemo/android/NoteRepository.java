package io.github.floatmemo.android;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Local source of truth. Mutations are serialized; AI results use revision guards. */
@android.annotation.SuppressLint("StaticFieldLeak") // Only the process application context is retained.
public final class NoteRepository {
    public interface Listener { void onNotesChanged(); }
    public interface Mutation { void apply(Note note); }
    private static NoteRepository instance;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners=new HashSet<>();
    private final LinkedHashMap<String,Note> notes=new LinkedHashMap<>();
    private final LinkedHashSet<String> folders=new LinkedHashSet<>();
    private final SQLiteDatabase db;
    private long corpusVersion=1;
    private final Map<String,String[]> searchStamps=new HashMap<>();
    public static final class Corpus {public final long version;public final List<Note> notes;Corpus(long v,List<Note> n){version=v;notes=n;}}
    public synchronized long corpusVersion(){return corpusVersion;}
    public synchronized Corpus corpus(){return new Corpus(corpusVersion,all());}
    private void trackSearch(Note n){String[] stamp={n.title,n.content,n.folder,n.tags};String[] before=searchStamps.put(n.id,stamp);if(before==null||!java.util.Arrays.equals(before,stamp))corpusVersion++;}
    public String error="";
    public static synchronized NoteRepository get(Context c){
        if(instance==null)instance=new NoteRepository(c.getApplicationContext());return instance;
    }
    private NoteRepository(Context c){
        context=c;db=c.openOrCreateDatabase("floatmemo-ai.db",0,null);
        db.execSQL("CREATE TABLE IF NOT EXISTS notes (id TEXT PRIMARY KEY,data TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS history (id TEXT,revision INTEGER,data TEXT,PRIMARY KEY(id,revision))");
        db.execSQL("CREATE TABLE IF NOT EXISTS edits (id INTEGER PRIMARY KEY AUTOINCREMENT,noteId TEXT,beforeData TEXT,created INTEGER)");
        try{
            try(Cursor x=db.rawQuery("SELECT data FROM notes",null)){while(x.moveToNext()){Note n=Note.fromJson(new JSONObject(x.getString(0)));AiDocument.migrateFingerprint(n);notes.put(n.id,n);trackSearch(n);}}
            String fs=meta("folders");if(!fs.isEmpty()){JSONArray a=new JSONArray(fs);for(int i=0;i<a.length();i++)folders.add(a.getString(i));}
            if(meta("migration").isEmpty())migrate();
            for(Note n:notes.values())if(n.aiStatus.equals("整理中")){n.aiStatus="等待整理";persist(n);}
                    AttachmentStore storage=new AttachmentStore(context);AttachmentStore.AssetTransaction tx=storage.unfinished();if(tx!=null){if(meta("assetTxn:"+tx.id).equals("committed"))tx.finish();else tx.rollback(storage.directory,BackupArchive.assets(new JSONObject(exportJson())).keySet());}
            storage.pruneOldUnreferenced(BackupArchive.assets(new JSONObject(exportJson())).keySet());
        }catch(Exception e){error="读取、迁移或附件恢复未完成。旧文件与数据库均保留，请先导出检查。";}
    }
    private String meta(String k){try(Cursor c=db.rawQuery("SELECT value FROM meta WHERE key=?",new String[]{k})){return c.moveToFirst()?c.getString(0):"";}}
    private void meta(String k,String v){ContentValues cv=new ContentValues();cv.put("key",k);cv.put("value",v);if(db.insertWithOnConflict("meta",null,cv,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IllegalStateException("备份或设置元数据保存失败");}
    private void migrate()throws Exception{
        File source=new File(context.getFilesDir(),"notes.json");if(!source.exists())source=new File(context.getFilesDir(),"notes.json.bak");
        if(source.exists()){
            File backup=new File(context.getFilesDir(),"notes-before-ai-backup.json");if(!backup.exists())Files.copy(source.toPath(),backup.toPath());
            JSONObject root=new JSONObject(new String(Files.readAllBytes(source.toPath()),StandardCharsets.UTF_8));
            JSONArray a=root.getJSONArray("notes"),f=root.optJSONArray("folders");
            db.beginTransaction();try{
                if(f!=null)for(int i=0;i<f.length();i++)folders.add(f.getString(i));
                for(int i=0;i<a.length();i++){Note n=Note.fromJson(a.getJSONObject(i));n.folderLocked=!n.folder.isEmpty();if(!notes.containsKey(n.id)){notes.put(n.id,n);persist(n);}}
                meta("folders",new JSONArray(folders).toString());meta("migration","done");db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }else meta("migration","done");
    }
    private void persist(Note n)throws Exception{ContentValues cv=new ContentValues();cv.put("id",n.id);cv.put("data",n.toJson().toString());if(db.insertWithOnConflict("notes",null,cv,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new Exception("写入失败");if(db.getVersion()<2)db.setVersion(2);}
    private void snapshot(Note n)throws Exception{ContentValues cv=new ContentValues();cv.put("id",n.id);cv.put("revision",n.revision);cv.put("data",n.toJson().toString());db.insertWithOnConflict("history",null,cv,SQLiteDatabase.CONFLICT_IGNORE);}
    private void changed(){main.post(()->{for(Listener l:new ArrayList<>(listeners))l.onNotesChanged();});}
    public void observe(Listener l){listeners.add(l);}public void removeObserver(Listener l){listeners.remove(l);}
    public synchronized List<Note> all(){List<Note> list=new ArrayList<>();for(Note n:notes.values())list.add(n.copy());list.sort(Comparator.comparingLong((Note n)->n.updatedAt).reversed());return list;}
    public synchronized List<String> folders(){return new ArrayList<>(folders);}
    public synchronized Note find(String id){Note n=notes.get(id);return n==null?null:n.copy();}
    public synchronized Note create(){Note n=new Note();notes.put(n.id,n);store(n);return n.copy();}
    private void store(Note n){trackSearch(n);try{persist(n);error="";}catch(Exception e){error="保存失败，请保留当前文字并导出备份。";}changed();}
    public synchronized void update(String id,Mutation fn){
        Note n=notes.get(id);if(n==null)return;Note before=n.copy();fn.apply(n);
        n.attachments=NoteBodyParser.referenced(n);
        boolean raw=!before.content.equals(n.content)||!before.title.equals(n.title);
        boolean aiRaw=!NoteBodyParser.fingerprint(before).equals(NoteBodyParser.fingerprint(n));
        if(raw){
            try{if(!before.attachments.isEmpty()||AiDocument.current(before))snapshot(before);}catch(Exception e){error="版本备份失败";}
            n.revision++;
            if(aiRaw){n.aiGeneration++;n.reviewed=false;n.attempts=0;n.aiError="";
                n.aiStatus=NoteBodyParser.text(n.content).trim().isEmpty()?"未整理":n.aiLocked?"原文已更新":AiConfig.get(context).enabled()?"等待整理":n.aiJson.isEmpty()?"未整理":"原文已更新";
            }else if(AiDocument.current(n)){n.aiRevision=n.revision;}
            else if(!n.aiStatus.equals("等待整理")&&!n.aiStatus.equals("整理中")&&!n.aiJson.isEmpty()&&!AiDocument.validQuotes(n.aiJson,n.content)){n.aiStatus="原文引用位置已改变";}
        }
        if(!before.folder.equals(n.folder)||!before.tags.equals(n.tags)){try{journal(before);}catch(Exception e){error="纠正历史保存失败";}}
        if(!before.folder.equals(n.folder))n.folderLocked=true;
        if(!before.tags.equals(n.tags))n.tagsLocked=true;
        n.updatedAt=System.currentTimeMillis();store(n);
        if(aiRaw&&!NoteBodyParser.text(n.content).trim().isEmpty()&&AiConfig.get(context).enabled())AiJobService.schedule(context);
    }
    public synchronized void delete(String id){db.beginTransaction();try{db.delete("notes","id=?",new String[]{id});db.delete("history","id=?",new String[]{id});db.delete("edits","noteId=?",new String[]{id});db.setTransactionSuccessful();}finally{db.endTransaction();}notes.remove(id);searchStamps.remove(id);corpusVersion++;changed();}
    public synchronized boolean addFolder(String value){String f=value.trim();if(f.isEmpty()||!folders.add(f))return false;meta("folders",new JSONArray(folders).toString());changed();return true;}
    public synchronized void deleteFolder(String f){folders.remove(f);for(Note n:notes.values())if(n.folder.equals(f)){n.folder="";n.folderLocked=true;store(n);}meta("folders",new JSONArray(folders).toString());changed();}
    public synchronized void setAllOpen(boolean open){for(Note n:notes.values()){n.open=open;store(n);}}
    public synchronized void flush(){for(Note n:notes.values())try{persist(n);}catch(Exception e){error="保存失败，请导出备份";}}
    public synchronized void queue(String id,boolean unlock){Note n=notes.get(id);if(n==null||NoteBodyParser.text(n.content).trim().isEmpty())return;
        if(unlock){try{journal(n);}catch(Exception e){error="纠正历史保存失败";changed();return;}n.aiLocked=false;}if(n.aiLocked)return;n.aiGeneration++;n.aiStatus="等待整理";n.aiError="";n.attempts=0;store(n);AiJobService.schedule(context);}
    public synchronized Note claim(){for(Note n:notes.values())if(n.aiStatus.equals("等待整理")&&!n.aiLocked&&!NoteBodyParser.text(n.content).trim().isEmpty()&&System.currentTimeMillis()-n.updatedAt>=5000){
        n.aiStatus="整理中";try{snapshot(n);}catch(Exception e){n.aiStatus="整理失败";n.aiError="保存来源版本失败";store(n);return null;}store(n);return n.copy();}return null;}
    public synchronized boolean hasPending(){for(Note n:notes.values())if(n.aiStatus.equals("等待整理")&&!n.aiLocked&&!NoteBodyParser.text(n.content).trim().isEmpty())return true;return false;}
    private boolean accepts(Note n,Note requested){return ResultGate.accept(n,requested);}
    public synchronized void release(Note requested,String message,boolean retry){Note n=notes.get(requested.id);if(!accepts(n,requested))return;n.attempts++;n.aiStatus=retry&&n.attempts<3?"等待整理":"整理失败";n.aiError=message;store(n);}
    public synchronized boolean applyAi(Note requested,String json){Note n=notes.get(requested.id);if(!accepts(n,requested)||!AiDocument.validQuotes(json,n.content))return false;
        n.aiJson=json;n.aiRevision=n.revision;n.aiSourceHash=NoteBodyParser.fingerprint(n);n.aiStatus="待确认";n.aiError="";n.reviewed=false;
        try{JSONObject j=new JSONObject(json);n.topicSuggestion=j.optString("folder","");
            if(!n.folderLocked&&folders.contains(n.topicSuggestion))n.folder=n.topicSuggestion;
            if(!n.tagsLocked){JSONArray a=j.optJSONArray("tags");List<String> tags=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++)tags.add(a.getString(i));n.tags=String.join("、",tags);}
        }catch(Exception ignored){return false;}store(n);return true;}
    private void journal(Note n)throws Exception{ContentValues v=new ContentValues();v.put("noteId",n.id);v.put("beforeData",n.toJson().toString());v.put("created",System.currentTimeMillis());db.insertOrThrow("edits",null,v);}
    public synchronized void editAi(String id,long expected,String json)throws Exception{Note n=notes.get(id);if(n==null||n.revision!=expected||!AiDocument.current(n))throw new Exception("原文已改变，请刷新后重新整理");journal(n);n.aiJson=json;n.aiGeneration++;n.aiLocked=true;n.reviewed=true;n.aiStatus="已人工修正";store(n);}
    public synchronized boolean undoEdit(String id){Note n=notes.get(id);if(n==null)return false;try(Cursor c=db.rawQuery("SELECT id,beforeData FROM edits WHERE noteId=? ORDER BY id DESC LIMIT 1",new String[]{id})){if(!c.moveToFirst())return false;Note old=Note.fromJson(new JSONObject(c.getString(1)));n.folder=old.folder;n.tags=old.tags;n.folderLocked=old.folderLocked;n.tagsLocked=old.tagsLocked;n.aiJson=old.aiJson;n.aiLocked=old.aiLocked;n.reviewed=old.reviewed;n.aiStatus=n.revision==old.aiRevision?old.aiStatus:"原文已更新";n.aiRevision=old.aiRevision;n.aiSourceHash=old.aiSourceHash;n.aiGeneration++;if(AiDocument.current(n))n.aiRevision=n.revision;db.delete("edits","id=?",new String[]{c.getString(0)});store(n);return true;}catch(Exception e){return false;}}
    public synchronized String exportJson()throws Exception{
        JSONObject r=new JSONObject().put("schemaVersion",3).put("includesImageFiles",false);JSONArray ns=new JSONArray();for(Note n:notes.values())ns.put(n.toJson());
        r.put("notes",ns).put("folders",new JSONArray(folders));
        JSONArray hs=new JSONArray(),es=new JSONArray();try(Cursor c=db.rawQuery("SELECT data FROM history",null)){while(c.moveToNext())hs.put(new JSONObject(c.getString(0)));}
        try(Cursor c=db.rawQuery("SELECT noteId,beforeData,created FROM edits",null)){while(c.moveToNext())es.put(new JSONObject().put("noteId",c.getString(0)).put("before",new JSONObject(c.getString(1))).put("created",c.getLong(2)));}
        return r.put("history",hs).put("edits",es).toString(2);
    }
    public synchronized int importJson(String text)throws Exception{return importJsonInternal(text,"");}
    public synchronized void insertAttachments(String id,long expected,int position,List<Attachment> assets)throws Exception{
        Note before=notes.get(id);if(before==null||before.revision!=expected)throw new Exception("笔记已改变，请重新选择插入位置");
        Note n=before.copy();int at=NoteBodyParser.floorBoundary(n.content,Math.max(0,Math.min(position,n.content.length())));StringBuilder markers=new StringBuilder();for(Attachment a:assets)markers.append(a.marker());
        n.content=n.content.substring(0,at)+markers+n.content.substring(at);n.attachments.addAll(assets);NoteBodyParser.validate(n);n.revision++;n.updatedAt=System.currentTimeMillis();
        if(AiDocument.current(n))n.aiRevision=n.revision;else if(!n.aiStatus.equals("等待整理")&&!n.aiStatus.equals("整理中")&&!n.aiJson.isEmpty()&&!AiDocument.validQuotes(n.aiJson,n.content))n.aiStatus="原文引用位置已改变";
        db.beginTransaction();try{snapshot(before);persist(n);db.setTransactionSuccessful();}finally{db.endTransaction();}notes.put(id,n);trackSearch(n);changed();
    }
    public synchronized int importBackup(BackupArchive.Prepared prepared)throws Exception{
        if(!prepared.complete)return importJson(prepared.root.toString());
        AttachmentStore storage=new AttachmentStore(context);AttachmentStore.AssetTransaction pending=storage.unfinished();Map<String,Attachment> existing=BackupArchive.assets(new JSONObject(exportJson()));
        if(pending!=null){if(meta("assetTxn:"+pending.id).equals("committed"))pending.finish();else pending.rollback(storage.directory,existing.keySet());}
        storage.validateStaged(prepared);AttachmentStore.AssetTransaction tx=storage.install(prepared,existing);boolean committed=false;
        try{int count=importJsonInternal(tx.root.toString(),tx.id);committed=true;tx.finish();return count;}
        finally{if(!committed)tx.rollback(storage.directory,existing.keySet());}
    }
    public void exportArchive(File destination)throws Exception{JSONObject snapshot; synchronized(this){snapshot=new JSONObject(exportJson());}BackupArchive.write(destination,snapshot,new AttachmentStore(context).directory);}
    private int importJsonInternal(String text,String transactionId)throws Exception{
        JSONObject checked=new JSONObject(text);BackupArchive.checkRoot(checked);
        Map<String,Attachment> needed=BackupArchive.assets(checked);AttachmentStore storage=new AttachmentStore(context);
        for(Attachment a:needed.values()){File f=storage.file(a.id);if(!f.isFile()||f.length()!=a.bytes||!BackupArchive.sha256(f).equals(a.sha256))throw new Exception("缺少原图片。JSON仅含文字和附件信息，请导入完整ZIP备份");}

        String fingerprint=java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        JSONArray previous=new JSONArray(meta("import:"+fingerprint).isEmpty()?"[]":meta("import:"+fingerprint));
        Map<String,JSONObject> receipts=new HashMap<>();for(int i=0;i<previous.length();i++){JSONObject e=previous.getJSONObject(i);receipts.put(e.getString("original"),e);}
        JSONArray newReceipt=new JSONArray();Set<String> seenIds=new HashSet<>();
        JSONObject r=new JSONObject(text);int version=r.optInt("schemaVersion",1);if(version<1||version>3)throw new Exception("不支持的备份版本");
        JSONArray ns=r.getJSONArray("notes"),fs=r.optJSONArray("folders");if(ns.length()>10000)throw new Exception("单次最多导入10000条");
        List<Note> incoming=new ArrayList<>();Map<String,String> remap=new HashMap<>();
        for(int i=0;i<ns.length();i++){
            JSONObject raw=ns.getJSONObject(i);if(!raw.has("content")||!raw.has("id"))throw new Exception("备份缺少笔记内容或标识");
            Note n=Note.fromJson(raw);String original=n.id;if(!seenIds.add(original))throw new Exception("备份包含重复标识，请检查文件");
            if(version==1)n.folderLocked=!n.folder.isEmpty();AiDocument.migrateFingerprint(n);
            JSONObject receipt=receipts.get(original);if(receipt!=null){Note imported=notes.get(receipt.getString("target"));
                if(imported!=null&&ImportPolicy.signature(imported).equals(receipt.getString("signature"))){remap.put(original,imported.id);newReceipt.put(receipt);continue;}}
            Note old=notes.get(n.id);if(old!=null){if(ImportPolicy.signature(old).equals(ImportPolicy.signature(n))){newReceipt.put(new JSONObject().put("original",original).put("target",old.id).put("signature",ImportPolicy.signature(old)));continue;}n.id=UUID.randomUUID().toString();remap.put(original,n.id);}
            if(n.aiStatus.equals("整理中")||n.aiStatus.equals("等待整理"))n.aiStatus="未整理";n.open=false;incoming.add(n);
            newReceipt.put(new JSONObject().put("original",original).put("target",n.id).put("signature",ImportPolicy.signature(n)));
        }
        LinkedHashSet<String> oldFolders=new LinkedHashSet<>(folders);
        db.beginTransaction();try{
            for(Note n:incoming){if(!n.dismissedRelations.isEmpty()){List<String> links=new ArrayList<>();for(String old:n.dismissedRelations.split(","))links.add(remap.getOrDefault(old,old));n.dismissedRelations=String.join(",",links);}persist(n);for(int ri=0;ri<newReceipt.length();ri++){JSONObject re=newReceipt.getJSONObject(ri);if(re.getString("target").equals(n.id))re.put("signature",ImportPolicy.signature(n));}}
            if(fs!=null)for(int i=0;i<fs.length();i++)folders.add(fs.getString(i));
            JSONArray hs=r.optJSONArray("history");if(hs!=null)for(int i=0;i<hs.length();i++){Note n=Note.fromJson(hs.getJSONObject(i));n.id=remap.getOrDefault(n.id,n.id);snapshot(n);}
            // Restore correction history only for newly imported notes, so repeated imports do not duplicate edits.
            Set<String> importedIds=new HashSet<>();for(Note n:incoming)importedIds.add(n.id);
            JSONArray es=r.optJSONArray("edits");if(es!=null)for(int i=0;i<es.length();i++){JSONObject e=es.getJSONObject(i);String target=remap.getOrDefault(e.getString("noteId"),e.getString("noteId"));if(!importedIds.contains(target))continue;
                Note before=Note.fromJson(e.getJSONObject("before"));before.id=target;ContentValues v=new ContentValues();v.put("noteId",target);v.put("beforeData",before.toJson().toString());v.put("created",e.optLong("created",0));db.insertOrThrow("edits",null,v);}
            meta("folders",new JSONArray(folders).toString());meta("import:"+fingerprint,newReceipt.toString());if(!transactionId.isEmpty())meta("assetTxn:"+transactionId,"committed");db.setTransactionSuccessful();
        }catch(Exception e){folders.clear();folders.addAll(oldFolders);throw e;}finally{db.endTransaction();}
        for(Note n:incoming){AiDocument.migrateFingerprint(n);notes.put(n.id,n);trackSearch(n);}changed();return incoming.size();
    }
}

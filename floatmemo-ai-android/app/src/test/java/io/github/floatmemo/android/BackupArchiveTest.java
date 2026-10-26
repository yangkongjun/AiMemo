package io.github.floatmemo.android;

import org.junit.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class BackupArchiveTest {
    private File root,assets;private Attachment image;private byte[] bytes;
    @Before public void setup()throws Exception{root=Files.createTempDirectory("memo-backup-test-").toFile();assets=new File(root,"assets");assertTrue(assets.mkdir());bytes=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jXioAAAAASUVORK5CYII=");File f=new File(assets,"11111111-1111-4111-8111-111111111111");Files.write(f.toPath(),bytes);image=new Attachment(f.getName(),"image/png",bytes.length,1,1,BackupArchive.sha256(f));}
    @After public void teardown(){BackupArchive.erase(root);}
    private Note media(){Note n=new Note();n.content="前面\n"+image.marker()+"\n后面";n.attachments.add(image);return n;}
    private JSONObject document(Note n)throws Exception{return new JSONObject().put("schemaVersion",3).put("notes",new JSONArray().put(n.toJson())).put("history",new JSONArray()).put("edits",new JSONArray()).put("folders",new JSONArray().put("生活"));}
    private File archive(JSONObject data)throws Exception{File zip=new File(root,UUID.randomUUID()+".zip");BackupArchive.write(zip,data,assets);return zip;}
    @Test public void mediaAndNoteRestorePreservesExactBytesAndOriginalText()throws Exception{
        Note n=media();File zip=archive(document(n));try(BackupArchive.Prepared p=BackupArchive.read(zip,root)){assertTrue(p.complete);assertEquals(n.content,p.root.getJSONArray("notes").getJSONObject(0).getString("content"));assertArrayEquals(bytes,Files.readAllBytes(new File(p.assets,image.id).toPath()));assertEquals(1,BackupArchive.assets(p.root).size());}
    }
    @Test public void historyAndCorrectionsKeepImagesNoLongerInCurrentBody()throws Exception{
        Note n=new Note();n.content="现在只有文字";Note old=media();old.id=n.id;JSONObject r=document(n).put("history",new JSONArray().put(old.toJson())).put("edits",new JSONArray().put(new JSONObject().put("noteId",n.id).put("before",old.toJson()).put("created",123)));
        try(BackupArchive.Prepared p=BackupArchive.read(archive(r),root)){assertTrue(new File(p.assets,image.id).exists());assertEquals(old.content,p.root.getJSONArray("edits").getJSONObject(0).getJSONObject("before").getString("content"));}
    }
    @Test public void missingAssetPreventsSuccessfulExport()throws Exception{assertTrue(new File(assets,image.id).delete());try{archive(document(media()));fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("缺失"));}}
    @Test public void alteredImageIsRejectedEvenWithRecomputedZipCrc()throws Exception{
        File zip=archive(document(media()));File altered=rewrite(zip,"assets/"+image.id,"different".getBytes(StandardCharsets.UTF_8));reject(altered);
    }
    @Test public void alteredNoteJsonIsRejectedByManifest()throws Exception{File zip=archive(document(media()));reject(rewrite(zip,"notes.json",document(new Note()).toString().getBytes(StandardCharsets.UTF_8)));}
    @Test public void removedImageAndUnexpectedEntryAreRejected()throws Exception{File zip=archive(document(media()));reject(rewrite(zip,"assets/"+image.id,null));reject(rewrite(zip,"extra.txt",new byte[]{1}));}
    @Test public void pathTraversalCannotEscapeStagingDirectory()throws Exception{
        File bad=new File(root,"bad.zip");try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(bad))){z.putNextEntry(new ZipEntry("../escaped.txt"));z.write(new byte[]{1});z.closeEntry();}reject(bad);assertFalse(new File(root,"escaped.txt").exists());
    }
    @Test public void truncatedCentralDirectoryIsRejected()throws Exception{File zip=archive(document(media()));byte[] raw=Files.readAllBytes(zip.toPath());Files.write(zip.toPath(),Arrays.copyOf(raw,raw.length-20));reject(zip);}
    @Test public void oldJsonBackupsStillReadAsTextOnly()throws Exception{
        for(int version:new int[]{1,2}){File json=new File(root,"old"+version+".json");JSONObject r=document(new Note()).put("schemaVersion",version);Files.write(json.toPath(),r.toString().getBytes(StandardCharsets.UTF_8));try(BackupArchive.Prepared p=BackupArchive.read(json,root)){assertFalse(p.complete);assertEquals(0,BackupArchive.assets(p.root).size());}}
    }
    @Test public void collisionRemapUpdatesBodyMetadataHistoryAndEdits()throws Exception{
        Note n=media();JSONObject r=document(n).put("history",new JSONArray().put(n.toJson())).put("edits",new JSONArray().put(new JSONObject().put("noteId",n.id).put("before",n.toJson())));String other="22222222-2222-4222-8222-222222222222";Map<String,String> ids=Collections.singletonMap(image.id,other);JSONObject transformed=BackupArchive.remap(r,ids);
        assertTrue(transformed.getJSONArray("notes").getJSONObject(0).getString("content").contains(other));assertEquals(other,transformed.getJSONArray("history").getJSONObject(0).getJSONArray("attachments").getJSONObject(0).getString("id"));assertTrue(transformed.getJSONArray("edits").getJSONObject(0).getJSONObject("before").getString("content").contains(other));assertTrue(r.getJSONArray("notes").getJSONObject(0).getString("content").contains(image.id));
    }
    @Test public void collisionIdsStayStableAcrossRepeatedImportsAndMissingFileRepairs()throws Exception{
        Attachment conflicting=new Attachment(image.id,"image/png",10,1,1,"a".repeat(64));Map<String,Attachment> incoming=Collections.singletonMap(image.id,image),existing=new HashMap<>();existing.put(image.id,conflicting);
        // Metadata reserves an ID even when its original file is missing.
        File empty=new File(root,"empty");assertTrue(empty.mkdir());Map<String,String> first=BackupArchive.destinationIds(incoming,existing,empty);assertNotEquals(image.id,first.get(image.id));
        String mapped=first.get(image.id);existing.put(mapped,image.withId(mapped));Map<String,String> second=BackupArchive.destinationIds(incoming,existing,empty);assertEquals(first,second);Files.write(new File(empty,mapped).toPath(),bytes);assertEquals(first,BackupArchive.destinationIds(incoming,existing,empty));
    }
    @Test public void interruptedFileTransactionRemovesOnlyUnreferencedNewFiles()throws Exception{
        String a=image.id,b="22222222-2222-4222-8222-222222222222";Files.write(new File(assets,b).toPath(),bytes);AttachmentStore.AssetTransaction tx=new AttachmentStore.AssetTransaction(root,"test",Arrays.asList(a,b));tx.write();assertTrue(new File(root,"attachment-import-journal.json").exists());tx.rollback(assets,Collections.singleton(a));assertTrue(new File(assets,a).exists());assertFalse(new File(assets,b).exists());assertFalse(new File(root,"attachment-import-journal.json").exists());
    }
    @Test public void committedFileTransactionRetainsNewFiles()throws Exception{AttachmentStore.AssetTransaction tx=new AttachmentStore.AssetTransaction(root,"done",Collections.singletonList(image.id));tx.write();tx.finish();assertTrue(new File(assets,image.id).exists());}
    @Test public void copyLimitRejectsOversizeWithoutReadingWholeSource()throws Exception{try{BackupArchive.copy(new ByteArrayInputStream(new byte[101]),new ByteArrayOutputStream(),100);fail();}catch(IOException expected){}}
    private void reject(File zip)throws Exception{int before=countStages();try(BackupArchive.Prepared ignored=BackupArchive.read(zip,root)){fail("invalid backup accepted");}catch(IOException expected){}assertEquals(before,countStages());}
    private int countStages(){File[] dirs=root.listFiles(f->f.getName().startsWith("memo-restore-"));return dirs==null?0:dirs.length;}
    private File rewrite(File zip,String name,byte[] replacement)throws Exception{File out=new File(root,UUID.randomUUID()+".zip");boolean replaced=false;try(ZipInputStream in=new ZipInputStream(new FileInputStream(zip));ZipOutputStream z=new ZipOutputStream(new FileOutputStream(out))){ZipEntry e;while((e=in.getNextEntry())!=null){byte[] b=in.readAllBytes();if(e.getName().equals(name)){replaced=true;b=replacement;}if(b!=null){z.putNextEntry(new ZipEntry(e.getName()));z.write(b);z.closeEntry();}in.closeEntry();}if(!replaced&&replacement!=null){z.putNextEntry(new ZipEntry(name));z.write(replacement);z.closeEntry();}}return out;}
}

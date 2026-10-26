package io.github.floatmemo.android;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class AiDocumentTest {
    private final String source="我当时觉得聊天可能缺少新话题。后来试着分享散步经历。";
    private String response(String quote)throws Exception{return new JSONObject().put("title","聊天记录")
        .put("summary",new JSONObject().put("text","当时怀疑缺少新话题").put("quote",quote))
        .put("points",new JSONArray()).put("advice",new JSONArray()).put("examples",new JSONArray())
        .put("tags",new JSONArray().put("聊天").put("聊天")).put("questions",new JSONArray()).put("folder","交流").toString();}
    @Test public void preservesQuotesAndDeduplicatesTags()throws Exception{
        JSONObject j=AiDocument.parse(response("我当时觉得聊天可能缺少新话题。"),source);
        assertEquals(1,j.getJSONArray("tags").length());
        assertEquals("我当时觉得聊天可能缺少新话题。",j.getJSONArray("summaries").getJSONObject(0).getString("quote"));
        assertEquals(0,j.getJSONArray("advice").length());
    }
    @Test public void fabricatedSourceIsRejected()throws Exception{
        try{AiDocument.parse(response("我天生不善社交"),source);fail("fabricated source accepted");}catch(Exception expected){assertTrue(expected.getMessage().contains("依据"));}
    }
    @Test public void emptyQuoteIsRejected()throws Exception{
        try{AiDocument.parse(response(""),source);fail("empty quote accepted");}catch(Exception expected){assertTrue(expected.getMessage().contains("依据"));}
    }
    @Test public void invalidJsonDoesNotBecomeAResult()throws Exception{
        try{AiDocument.parse("not json",source);fail("invalid JSON accepted");}catch(JSONException expected){assertNotNull(expected.getMessage());}
    }
    @Test public void chunksPreserveEveryCharacterAndEmoji(){
        String original="甲😀乙\n".repeat(6200);List<String> chunks=AiDocument.chunks(original);
        assertEquals(original,String.join("",chunks));assertTrue(chunks.size()>1);
        for(String c:chunks){assertTrue(c.length()<=10000);assertFalse(Character.isHighSurrogate(c.charAt(c.length()-1)));assertFalse(Character.isLowSurrogate(c.charAt(0)));}
    }
    @Test public void combinesAllChunkSummaries()throws Exception{
        JSONObject a=AiDocument.parse(response("我当时觉得聊天可能缺少新话题。"),source);
        JSONObject b=AiDocument.parse(response("后来试着分享散步经历。"),source);
        JSONObject combined=AiDocument.combine(Arrays.asList(a,b));assertEquals(2,combined.getInt("parts"));
        assertEquals(2,combined.getJSONArray("summaries").length());assertEquals(1,combined.getJSONArray("tags").length());
    }
    @Test public void noteBackupRoundTripPreservesAiAndLocks()throws Exception{
        Note n=new Note();n.content=source;n.tags="聊天";n.aiJson=response("后来试着分享散步经历。");n.revision=7;n.aiRevision=6;n.aiLocked=true;n.folderLocked=true;n.dismissedRelations="another-note";
        Note restored=Note.fromJson(n.toJson());assertEquals(n.content,restored.content);assertEquals(n.aiJson,restored.aiJson);
        assertEquals(7,restored.revision);assertEquals(6,restored.aiRevision);assertTrue(restored.aiLocked);assertTrue(restored.folderLocked);assertEquals(n.dismissedRelations,restored.dismissedRelations);
    }
    @Test public void duplicateSignatureIgnoresWindowAndTransientJobState()throws Exception{
        Note n=new Note();n.content="原文";Note other=n.copy();other.open=true;other.x=999;other.aiStatus="整理中";
        assertEquals(ImportPolicy.signature(n),ImportPolicy.signature(other));
        other.content="不同原文";assertNotEquals(ImportPolicy.signature(n),ImportPolicy.signature(other));
    }
    @Test public void noteCopiesDoNotLoseRejectionAndManualLocks(){
        Note n=new Note();n.tagsLocked=true;n.aiLocked=true;n.dismissedRelations="one,two";Note copy=n.copy();
        assertTrue(copy.tagsLocked);assertTrue(copy.aiLocked);assertEquals("one,two",copy.dismissedRelations);
    }
    @Test public void oldBackupHasSafeDefaults()throws Exception{
        Note n=Note.fromJson(new JSONObject().put("id","old").put("content","原笔记").put("folder","旧分类"));
        assertEquals("原笔记",n.content);assertEquals("旧分类",n.folder);assertEquals(1,n.revision);assertEquals("",n.aiJson);assertFalse(n.aiLocked);
    }
}

package io.github.floatmemo.android;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class MediaAndSourceTest {
    private Attachment image(){return new Attachment("11111111-1111-4111-8111-111111111111","image/png",100,10,10,"a".repeat(64));}
    private String ai(String quote)throws Exception{return new JSONObject().put("summaries",new JSONArray().put(new JSONObject().put("text","保留当时的感受").put("quote",quote))).put("points",new JSONArray()).toString();}
    @Test public void insertingAndDeletingMarkerKeepsAiInputAndExactSurroundingText(){
        Note n=new Note();n.content="开头\n\n我当时有点困惑。尾声";String hash=NoteBodyParser.fingerprint(n);int at=4;String raw=n.content;
        n.content=raw.substring(0,at)+image().marker()+raw.substring(at);assertEquals(raw,NoteBodyParser.text(n.content));assertEquals(hash,NoteBodyParser.fingerprint(n));
        assertEquals(raw,NoteBodyParser.text(n.content));n.content=n.content.replace(image().marker(),"");assertEquals(raw,n.content);
    }
    @Test public void sourceOffsetsAfterImageReferToOriginalBody(){
        String raw="甲"+image().marker()+"我当时觉得可能没话题。";assertEquals(1+image().marker().length(),NoteBodyParser.locate(raw,"我当时觉得"));
        assertEquals(-1,NoteBodyParser.locate(raw,"甲我当时"));
    }
    @Test public void imageBinaryAndMarkersAreNotAiInput(){String raw=image().marker();assertEquals("",NoteBodyParser.text(raw));assertEquals("图片笔记",note(raw).displayTitle());}
    private Note note(String content){Note n=new Note();n.content=content;return n;}
    @Test public void imageEditDoesNotInvalidateSafeTextResult()throws Exception{
        Note n=note("甲。后来分享了经历。乙。");n.aiJson=ai("后来分享了经历。");n.aiRevision=n.revision;AiDocument.migrateFingerprint(n);assertTrue(AiDocument.current(n));
        Note requested=n.copy();n.content=image().marker()+n.content;n.revision++;assertTrue(AiDocument.current(n));assertTrue(ResultGate.accept(n,requested));
    }
    @Test public void insertionInsideAQuoteRejectsItsContiguity()throws Exception{
        Note n=note("后来分享经历");n.aiJson=ai("后来分享经历");n.aiRevision=n.revision;AiDocument.migrateFingerprint(n);
        n.content="后来"+image().marker()+"分享经历";assertFalse(AiDocument.current(n));assertFalse(AiDocument.validQuotes(n.aiJson,n.content));
    }
    @Test public void changedTextTitleLockGenerationAndDeletionRejectLateResult(){
        Note n=note("原始正文");Note requested=n.copy();n.content="新正文";assertFalse(ResultGate.accept(n,requested));
        n=requested.copy();n.title="新标题";assertFalse(ResultGate.accept(n,requested));n=requested.copy();n.aiLocked=true;assertFalse(ResultGate.accept(n,requested));
        n=requested.copy();n.aiGeneration++;assertFalse(ResultGate.accept(n,requested));assertFalse(ResultGate.accept(null,requested));
    }
    @Test public void staleLegacyResultNeverGetsFreshFingerprint()throws Exception{Note n=note("原文");n.aiJson=ai("原文");n.revision=3;n.aiRevision=2;AiDocument.migrateFingerprint(n);assertEquals("",n.aiSourceHash);assertFalse(AiDocument.current(n));}
    @Test public void legacyQuoteMustActuallyExistBeforeMigration()throws Exception{Note n=note("原文");n.aiJson=ai("不存在");n.aiRevision=n.revision;AiDocument.migrateFingerprint(n);assertEquals("",n.aiSourceHash);}
    @Test public void attachmentMetadataSurvivesCopyAndJsonWithoutMutableAliases()throws Exception{
        Note n=note(image().marker());n.attachments.add(image());n.aiSourceHash="hash";n.aiGeneration=9;Note copy=n.copy();copy.attachments.clear();assertEquals(1,n.attachments.size());
        Note restored=Note.fromJson(n.toJson());assertTrue(image().same(restored.attachments.get(0)));assertEquals("hash",restored.aiSourceHash);assertEquals(9,restored.aiGeneration);
    }
    @Test public void attachmentDataChangesImportIdentity()throws Exception{
        Note n=note(image().marker());n.attachments.add(image());Note other=n.copy();other.attachments.set(0,new Attachment(image().id,"image/png",100,10,10,"b".repeat(64)));assertNotEquals(ImportPolicy.signature(n),ImportPolicy.signature(other));
    }
    @Test public void twentyOneReferencesAndMissingMetadataFail(){
        Note n=note(image().marker().repeat(21));n.attachments.add(image());try{NoteBodyParser.validate(n);fail();}catch(IllegalArgumentException expected){}
        n=note(image().marker());try{NoteBodyParser.validate(n);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void invalidPathAndUnsupportedOrOversizedImagesAreRejected(){
        assertFalse(Attachment.validId("../../secret"));String id=image().id;
        for(int i=0;i<4;i++)try{new Attachment(id,i==0?"image/gif":"image/png",i==1?Attachment.MAX_BYTES+1:10,i==2?40001:10,i==3?100000:10,"a".repeat(64));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void unicodeBoundariesNeverSplitASurrogate(){String raw="甲😀乙";assertEquals(1,NoteBodyParser.floorBoundary(raw,2));assertEquals("甲",NoteBodyParser.bounded(raw,2));}
    @Test public void analysisSegmentsKeepImageBoundariesWithoutAddingImagePaths(){String raw="前面的想法"+image().marker()+"后面的建议";List<String> spans=NoteBodyParser.spans(raw,2,9);assertEquals(Arrays.asList("的想法","后面的建"),spans);assertEquals("的想法后面的建",String.join("",spans));}
    @Test public void diagnosticEchoOfAnalysisSegmentsIsHidden()throws Exception{String source="私人的笔记内容";String body=new JSONObject().put("originalNoteSegments",new JSONArray().put(source)).put("message","context limit exceeded").toString();String preview=DiagnosticRecord.responsePreview(body,"");assertFalse(preview.contains(source));assertTrue(preview.contains("context limit exceeded"));}
    @Test public void incompleteMarkerCannotLeakItsInternalUriToAi(){for(String value:new String[]{"![图片](attachment://"+image().id,"attachment:\\/\\/"+image().id})try{NoteBodyParser.ensureAiSafe(value);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("未发送"));}}
    @Test public void normalTextAfterRemovingCompleteMarkersPassesTransportGuard(){NoteBodyParser.ensureAiSafe(NoteBodyParser.text("这里是文字"+image().marker()+"和补充说明"));}
}

package io.github.floatmemo.android;

import org.junit.Test;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class ResponseReaderTest {
    private String frame(String text,String reasoning,String finish)throws Exception{
        JSONObject delta=new JSONObject();if(text!=null)delta.put("content",text);if(reasoning!=null)delta.put("reasoning_content",reasoning);
        return "data: "+new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("index",0).put("delta",delta).put("finish_reason",finish==null?JSONObject.NULL:finish))).toString()+"\n\n";
    }
    private String read(String body,String type,ResponseReader.Stats stats)throws Exception{return ResponseReader.read(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),type,stats);}
    private String done(){return "data: [DONE]\n\n";}
    @Test public void reasoningAndVisibleDeltasReconstructOneAnswerWithoutReasoningLeak()throws Exception{
        String wire=frame(null,"不应出现在保存结果里的推理",null)+frame("{\"points\":",null,null)+frame("[]}",null,null)+frame(null,null,"stop")+done();ResponseReader.Stats s=new ResponseReader.Stats();
        String body=read(wire,"text/event-stream",s);assertEquals("{\"points\":[]}",ApiResponse.content(200,body));assertFalse(body.contains("不应出现在"));assertTrue(s.reasoningChars>0);assertEquals("SSE",s.mode);assertTrue(s.doneMarker);assertEquals(5,s.events);
    }
    @Test public void jsonReturnedDespiteStreamRequestStillWorks()throws Exception{String original=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content","OK")).put("finish_reason","stop"))).toString();ResponseReader.Stats s=new ResponseReader.Stats();assertEquals("OK",ApiResponse.content(200,read(original,"application/json",s)));assertEquals("JSON",s.mode);}
    @Test public void mislabeledJsonUsesSameResponseNotAnotherRequest()throws Exception{ResponseReader.Stats s=new ResponseReader.Stats();assertEquals("{}",read("{}","text/event-stream",s));assertEquals("JSON",s.mode);}
    @Test public void detectsSseWithMissingContentType()throws Exception{ResponseReader.Stats s=new ResponseReader.Stats();assertEquals("OK",ApiResponse.content(200,read(frame("OK",null,"stop")+done(),null,s)));assertEquals("SSE",s.mode);}
    @Test public void bomCrLfAndHeartbeatAreAccepted()throws Exception{String wire="\uFEFF: keepalive\r\n\r\nevent: message\r\nid: ignored\r\n"+(frame("你好😀",null,"stop")+done()).replace("\n","\r\n");ResponseReader.Stats s=new ResponseReader.Stats();assertEquals("你好😀",ApiResponse.content(200,read(wire,"text/event-stream",s)));assertEquals(1,s.heartbeats);}
    @Test public void bareCrMultilineDataAndUnknownFieldsAreHandled()throws Exception{String wire="retry: 1000\runknown: ignored\rdata: {\"choices\":\rdata: [{\"delta\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\r\rdata: [DONE]\r\r";assertEquals("OK",ApiResponse.content(200,read(wire,"text/event-stream",new ResponseReader.Stats())));}
    @Test public void utf8AcrossIndividualNetworkBytesIsNotCorrupted()throws Exception{byte[] wire=(frame("中😀文",null,"stop")+done()).getBytes(StandardCharsets.UTF_8);InputStream tiny=new ByteArrayInputStream(wire){public synchronized int read(byte[] b,int off,int len){return super.read(b,off,Math.min(1,len));}};assertEquals("中😀文",ApiResponse.content(200,ResponseReader.read(tiny,"text/event-stream",new ResponseReader.Stats())));}
    @Test public void finalUsageOnlyFrameIsRetainedWhenActuallyProvided()throws Exception{String wire=frame("OK",null,"stop")+"data: {\"choices\":[],\"usage\":{\"prompt_tokens\":44,\"completion_tokens\":64,\"total_tokens\":108,\"private_text\":\"hidden\"}}\n\n"+done();ResponseReader.Stats s=new ResponseReader.Stats();JSONObject out=new JSONObject(read(wire,"text/event-stream",s));assertEquals(108,out.getJSONObject("usage").getInt("total_tokens"));assertFalse(out.toString().contains("hidden"));}
    @Test public void reasoningOnlyLengthIsValidForProbeButNeverForNotes()throws Exception{String body=read(frame(null,"推理",null)+frame(null,null,"length")+done(),"text/event-stream",new ResponseReader.Stats());assertTrue(ApiResponse.parse(200,body,true).truncated);try{ApiResponse.parse(200,body,false);fail();}catch(ApiResponse.Failure e){assertEquals("OUTPUT_TRUNCATED",e.kind);}}
    @Test public void lengthWithPartialVisibleJsonStillRejectsNote()throws Exception{String body=read(frame("{\"partial",null,"length")+done(),"text/event-stream",new ResponseReader.Stats());try{ApiResponse.content(200,body);fail();}catch(ApiResponse.Failure e){assertEquals("OUTPUT_TRUNCATED",e.kind);}}
    @Test public void eofBeforeFinishCannotPromotePartialContent()throws Exception{ResponseReader.Stats s=new ResponseReader.Stats();reject(frame("{\"apparently_valid\":true}",null,null),"STREAM_INCOMPLETE",s);assertTrue(s.visibleChars>0);assertFalse(s.doneMarker);}
    @Test public void doneWithoutFinishReasonIsRejected()throws Exception{reject(frame("OK",null,null)+done(),"STREAM_INCOMPLETE",new ResponseReader.Stats());}
    @Test public void cleanEofAfterDelimitedFinishIsComplete()throws Exception{assertEquals("OK",ApiResponse.content(200,read(frame("OK",null,"stop"),"text/event-stream",new ResponseReader.Stats())));}
    @Test public void nonDelimitedFinalEventIsNotDispatched()throws Exception{reject(frame("OK",null,"stop").trim(),"STREAM_INCOMPLETE",new ResponseReader.Stats());}
    @Test public void malformedEventAndServerErrorDoNotBecomeNotes()throws Exception{reject("data: {broken}\n\n","INVALID_STREAM",new ResponseReader.Stats());ResponseReader.Stats s=new ResponseReader.Stats();reject("data: {\"error\":{\"code\":\"context_length_exceeded\",\"message\":\"private\"}}\n\n","STREAM_SERVICE_ERROR",s);assertEquals("context_length_exceeded",s.serviceCode);}
    @Test public void nonTextContentAndNonNormalFinishAreRejected()throws Exception{reject("data: {\"choices\":[{\"delta\":{\"content\":[]},\"finish_reason\":\"stop\"}]}\n\n","INVALID_STREAM",new ResponseReader.Stats());reject(frame("OK",null,"content_filter"),"STREAM_FINISH_REASON",new ResponseReader.Stats());}
    @Test public void oversizedEventIsStoppedBeforeItCanGrowUnbounded()throws Exception{reject("data: "+"x".repeat(ResponseReader.MAX_EVENT+1)+"\n\n","STREAM_EVENT_TOO_LARGE",new ResponseReader.Stats());}
    @Test public void accumulatedVisibleContentHasIndependentSizeLimit()throws Exception{String chunk=frame("x".repeat(200000),null,null);reject(chunk.repeat(11),"RESPONSE_TOO_LARGE",new ResponseReader.Stats());}
    @Test public void socketResetAfterSomeDeltasStaysAFailureAndKeepsOnlyCounts()throws Exception{
        byte[] first=frame("不应入库的半段回答",null,null).getBytes(StandardCharsets.UTF_8);InputStream reset=new InputStream(){int at;public int read()throws IOException{if(at<first.length)return first[at++]&255;throw new SocketException("Connection reset");}public int read(byte[] b,int off,int len)throws IOException{if(at>=first.length)throw new SocketException("Connection reset");int n=Math.min(len,first.length-at);System.arraycopy(first,at,b,off,n);at+=n;return n;}};ResponseReader.Stats s=new ResponseReader.Stats();
        try{ResponseReader.read(reset,"text/event-stream",s);fail();}catch(SocketException expected){assertTrue(s.visibleChars>0);assertEquals("CONNECTION_RESET",NetworkTrace.signature(expected));}
        DiagnosticRecord record=new DiagnosticRecord("测试");s.record(record);assertFalse(record.value.toString().contains("不应入库"));
    }
    @Test public void messageSnapshotIsNotDuplicatedWhenDeltaIsAlsoPresent()throws Exception{String wire="data: {\"choices\":[{\"delta\":{\"content\":\"OK\"},\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\n\n"+done();assertEquals("OK",ApiResponse.content(200,read(wire,"text/event-stream",new ResponseReader.Stats())));}
    @Test public void completeMessageSnapshotsCanExtendWithoutRepeatingText()throws Exception{String wire="data: {\"choices\":[{\"message\":{\"content\":\"O\"},\"finish_reason\":null}]}\n\ndata: {\"choices\":[{\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}\n\n"+done();assertEquals("OK",ApiResponse.content(200,read(wire,"text/event-stream",new ResponseReader.Stats())));}
    @Test public void noOutputAndHeartbeatsAloneAreNotSuccessfulResults()throws Exception{reject(": heartbeat\n\ndata: \n\n","STREAM_INCOMPLETE",new ResponseReader.Stats());}
    @Test public void aLaterStopCannotEraseAnEarlierLengthTruncation()throws Exception{reject(frame("{}",null,"length")+frame(null,null,"stop")+done(),"INVALID_STREAM",new ResponseReader.Stats());}
    private void reject(String wire,String kind,ResponseReader.Stats stats)throws Exception{try{read(wire,"text/event-stream",stats);fail("partial/invalid stream accepted");}catch(ApiResponse.Failure e){assertEquals(kind,e.kind);}}
}

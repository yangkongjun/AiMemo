package io.github.floatmemo.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class ApiResponseTest {
    private void fails(int status,String body,String kind)throws Exception{
        try{ApiResponse.content(status,body);fail("Should reject "+kind);}catch(ApiResponse.Failure e){assertEquals(kind,e.kind);assertFalse(e.getMessage().isEmpty());}
    }
    @Test public void acceptsValidChatResponse()throws Exception{
        assertEquals("OK",ApiResponse.content(200,"{\"choices\":[{\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}"));
    }
    @Test public void errorObjectIsNotMistakenForMissingChoices()throws Exception{fails(200,"{\"error\":{\"message\":\"bad model\"}}","SERVICE_ERROR");}
    @Test public void missingChoicesHasUsefulFailureInsteadOfJsonException()throws Exception{fails(200,"{\"message\":\"welcome\"}","MISSING_CHOICES");}
    @Test public void htmlResponseIsClassified()throws Exception{fails(200,"<html>gateway</html>","NOT_JSON");}
    @Test public void nonObjectJsonIsRejected()throws Exception{fails(200,"[]","NOT_JSON");}
    @Test public void emptyChoicesIsExplicit()throws Exception{fails(200,"{\"choices\":[]}","EMPTY_CHOICES");}
    @Test public void invalidChoiceIsExplicit()throws Exception{fails(200,"{\"choices\":[42]}","INVALID_CHOICE");}
    @Test public void missingOrNonTextContentIsRejected()throws Exception{
        for(String value:new String[]{"null","[]","\"   \""})fails(200,"{\"choices\":[{\"message\":{\"content\":"+value+"}}]}","MISSING_CONTENT");
    }
    @Test public void truncatedOutputIsRejected()throws Exception{fails(200,"{\"choices\":[{\"finish_reason\":\"length\"}]}","OUTPUT_TRUNCATED");}
    @Test public void httpFailureIsPreservedEvenWhenBodyIsHtml()throws Exception{
        for(int status:new int[]{301,400,401,403,404,405,422,429,500,503})fails(status,"<html>error</html>","HTTP_"+status);
    }
    @Test public void reasoningOnlyProbeAt64TokensShowsConnectivityNotCompleteAnswer()throws Exception{
        String body="{\"model\":\"deepseek-ai/DeepSeek-V4.1-Flash\",\"choices\":[{\"delta\":{\"content\":\"\"},\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"\",\"reasoning_content\":\"reasoning only\"}}],\"usage\":{\"prompt_tokens\":44,\"completion_tokens\":64,\"total_tokens\":108}}";
        ApiResponse.Answer answer=ApiResponse.parse(200,body,true);
        assertTrue(answer.truncated);assertEquals("",answer.text);
        fails(200,body,"OUTPUT_TRUNCATED");
    }
    @Test public void probeNeverAcceptsWelcomeOrAuthErrorAsConnectivitySuccess()throws Exception{
        for(int status:new int[]{200,401})try{ApiResponse.parse(status,"{\"message\":\"Welcome to ModelScope\"}",true);fail();}
        catch(ApiResponse.Failure e){assertEquals(status==200?"MISSING_CHOICES":"HTTP_401",e.kind);}
    }
    @Test public void probeStillRejectsMalformedTruncatedEnvelope()throws Exception{
        for(String message:new String[]{"null","{}", "{\"content\":[]}"}){
            String body="{\"choices\":[{\"finish_reason\":\"length\",\"message\":"+message+"}]}";
            // An empty message object is valid evidence of a budget-limited probe;
            // missing message objects and non-text content are not compatible envelopes.
            if(message.equals("{}")){assertTrue(ApiResponse.parse(200,body,true).truncated);continue;}
            try{ApiResponse.parse(200,body,true);fail();}catch(ApiResponse.Failure e){assertEquals("MISSING_CONTENT",e.kind);}
        }
    }
    @Test public void normalProbeReturnsTextButTruncatedNotesRemainRejected()throws Exception{
        ApiResponse.Answer answer=ApiResponse.parse(200,"{\"choices\":[{\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}",true);
        assertFalse(answer.truncated);assertEquals("OK",answer.text);
        fails(200,"{\"choices\":[{\"message\":{\"content\":\"partial text\"},\"finish_reason\":\"length\"}]}","OUTPUT_TRUNCATED");
    }
}

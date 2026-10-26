package io.github.floatmemo.android;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class DiagnosticRecordTest {
    @Test public void masksActiveKeyInServiceError()throws Exception{
        String result=DiagnosticRecord.responsePreview("{\"error\":{\"code\":\"auth\",\"message\":\"invalid secret-12345\"}}","secret-12345");
        assertFalse(result.contains("secret-12345"));assertTrue(result.contains("auth"));assertTrue(result.contains("invalid"));
    }
    @Test public void masksNestedCredentialsAndEchoedMessages()throws Exception{
        String body="{\"error\":{\"api_key\":\"OTHERKEY\",\"Access-Token\":\"OTHERTOKEN\"},\"messages\":[{\"content\":\"私人笔记\"}],\"choices\":[{\"message\":{\"content\":\"私人模型回答\"}}]}";
        String result=DiagnosticRecord.responsePreview(body,"actual-key");
        for(String secret:new String[]{"OTHERKEY","OTHERTOKEN","私人笔记","私人模型回答"})assertFalse(result.contains(secret));
    }
    @Test public void masksRawBearerAndQueryCredentials(){
        String text="<html>Authorization: Bearer opaque-token-123 /?api_key=other-private-key</html>";
        String result=DiagnosticRecord.responsePreview(text,"");assertFalse(result.contains("opaque-token-123"));assertFalse(result.contains("other-private-key"));
    }
    @Test public void masksExactRequestEchoIncludingEscapedNewlines()throws Exception{
        String request="personal line one\npersonal line two";
        String body=new JSONObject().put("error",new JSONObject().put("message",request)).toString();
        String result=DiagnosticRecord.responsePreview(body,"",request);assertFalse(result.contains("personal line"));
    }
    @Test public void successfulModelBodyAndSourcesAreNeverInPreview(){
        String result=DiagnosticRecord.responsePreview("{\"summary\":\"私人总结\",\"quote\":\"私人原文\",\"points\":[\"私人要点\"]}","");
        assertFalse(result.contains("私人"));
    }
    @Test public void previewIsBoundedAndDoesNotCutEmoji(){
        String text=String.join("",java.util.Collections.nCopies(5000,"字"));
        assertTrue(DiagnosticRecord.responsePreview(text,"").length()<4100);
        assertEquals("a…[已截断]",DiagnosticRecord.bounded("a😀b",2));
    }
    @Test public void nestedResponseIsBounded()throws Exception{
        JSONObject body=new JSONObject().put("message","hidden-too-deep");
        for(int i=0;i<15;i++)body=new JSONObject().put("nested",body);
        assertFalse(DiagnosticRecord.responsePreview(body.toString(),"").contains("hidden-too-deep"));
    }
    @Test public void reportTracksStageAndDuration()throws Exception{
        DiagnosticRecord r=new DiagnosticRecord("连接测试");r.stage("接口返回格式");r.put("HTTP状态",200);r.end(false,"MISSING_CHOICES","缺少回答");
        assertEquals("接口返回格式",r.value.getString("阶段"));assertEquals("MISSING_CHOICES",r.value.getString("错误类型"));
        assertEquals(200,r.value.getInt("HTTP状态"));assertTrue(r.value.getLong("耗时毫秒")>=0);
    }
}

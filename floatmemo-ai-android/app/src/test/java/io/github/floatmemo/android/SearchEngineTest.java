package io.github.floatmemo.android;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SearchEngineTest {
    @Test public void tagsAreSearchableWithoutChangingTheOriginal(){Note n=new Note();n.content="今天与同学散步的经历";n.tags="找话题、聊得开心";
        List<SearchEngine.Hit> r=SearchEngine.local(Arrays.asList(n),"聊得开心");assertEquals(1,r.size());assertEquals("今天与同学散步的经历",n.content);}
    @Test public void unrelatedExactQueryProducesNoFakeMatch(){Note n=new Note();n.content="买菜清单";assertTrue(SearchEngine.local(Arrays.asList(n),"密码协议").isEmpty());}
    @Test public void relatedNeverReturnsTheSource(){Note n=new Note();n.content="聊天话题";assertTrue(SearchEngine.related(n,Arrays.asList(n)).isEmpty());}
    @Test public void candidatesAreBounded(){List<Note> ns=new ArrayList<>();for(int i=0;i<100;i++){Note n=new Note();n.content="聊天"+i;ns.add(n);}assertEquals(30,SearchEngine.candidates(ns,"聊天").size());}
    @Test public void staleOrLockedAiMustNeverOverwrite(){assertTrue(ResultGate.accept(5,5,false));assertFalse(ResultGate.accept(6,5,false));assertFalse(ResultGate.accept(5,5,true));}
    @Test public void explicitHttpsEndpointRequired()throws Exception{assertEquals("https://example.com/v1/chat/completions",AiConfig.validatedEndpoint("https://example.com/v1/chat/completions"));
        for(String value:new String[]{"http://example.com/v1/chat/completions","https://user:secret@example.com/v1/chat/completions","https://example.com/v1/chat/completions?key=secret","https://example.com/v1#fragment"}){try{AiConfig.validatedEndpoint(value);fail(value);}catch(Exception expected){assertNotNull(expected.getMessage());}}}
    @Test public void modelscopeEndpointIsUsedExactlyAsEntered()throws Exception{
        for(String input:new String[]{"https://api-inference.modelscope.cn","https://api-inference.modelscope.cn/","https://api-inference.modelscope.cn/v1","https://api-inference.modelscope.cn/v1/","https://api-inference.modelscope.cn/v1/chat/completions","  https://api-inference.modelscope.cn/v1/chat/completions/  "})
            assertEquals(input,input.trim(),AiConfig.validatedEndpoint(input));
    }
    @Test public void customPathAndEscapesArePreservedWithoutAddingPaths()throws Exception{
        assertEquals("https://example.com/gateway/v1/",AiConfig.validatedEndpoint("https://example.com/gateway/v1/"));
        assertEquals("https://example.com/api/v3",AiConfig.validatedEndpoint("https://example.com/api/v3"));
        assertEquals("https://example.com:8443/custom%2Fendpoint/",AiConfig.validatedEndpoint("https://example.com:8443/custom%2Fendpoint/"));
    }
    @Test public void normalizedEndpointsCanBeRevalidatedWithoutDuplicatingPath()throws Exception{
        String result=AiConfig.validatedEndpoint("https://api-inference.modelscope.cn");
        assertEquals(result,AiConfig.validatedEndpoint(result));
    }
}

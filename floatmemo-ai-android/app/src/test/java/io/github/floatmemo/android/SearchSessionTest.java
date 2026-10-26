package io.github.floatmemo.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class SearchSessionTest {
    @Test public void repeatedClickAndRecreatedViewAdmitOneRequest(){SearchSession<String> s=new SearchSession<>(20,300000,1000);assertTrue(s.acquire("same","first",0).fresh);assertFalse(s.acquire("same","rotation",10).fresh);assertEquals("first",s.find("same",20));assertTrue(s.busy());}
    @Test public void changingQueryDoesNotStartASecondRequestInParallel(){SearchSession<String> s=new SearchSession<>(20,300000,1000);s.acquire("old","request",0);try{s.acquire("new","other",1);fail();}catch(IllegalStateException expected){}assertEquals("request",s.find("old",2));}
    @Test public void completedResultReusesCacheUntilFiveMinutes(){SearchSession<String> s=new SearchSession<>(20,300000,1000);s.acquire("key","pending",0);s.complete("key","found",true,10,10);assertFalse(s.busy());assertFalse(s.acquire("key","extra",299999).fresh);assertTrue(s.acquire("key","new",300010).fresh);}
    @Test public void failuresCanBeRetriedByAnExplicitClick(){SearchSession<String> s=new SearchSession<>(20,300000,1000);s.acquire("key","request",0);s.complete("key","failed",false,10,1);assertEquals("failed",s.find("key",2));assertTrue(s.acquire("key","manual retry",3).fresh);}
    @Test public void queryScopeMetadataVersionSettingsAndBudgetKeysAreSeparate(){SearchSession<String> s=new SearchSession<>(20,300000,1000);s.acquire("q:scope:v1:epoch1:budget1","initial",0);s.complete("q:scope:v1:epoch1:budget1","result",true,10,1);for(String key:new String[]{"other:scope:v1:epoch1:budget1","q:other:v1:epoch1:budget1","q:scope:v2:epoch1:budget1","q:scope:v1:epoch2:budget1","q:scope:v1:epoch1:budget2"}){assertNull(s.find(key,2));}}
    @Test public void twentyEntryAndMemoryCapsEvictLeastRecentlyUsed(){SearchSession<String> s=new SearchSession<>(20,300000,1000);for(int i=0;i<21;i++){s.acquire("k"+i,"r"+i,i*2);s.complete("k"+i,"r"+i,true,1,i*2+1);}assertNull(s.find("k0",50));assertEquals("r20",s.find("k20",50));SearchSession<String> small=new SearchSession<>(20,300000,10);small.acquire("a","A",0);small.complete("a","A",true,8,1);small.acquire("b","B",2);small.complete("b","B",true,8,3);assertNull(small.find("a",4));}
}

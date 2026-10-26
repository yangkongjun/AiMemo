package io.github.floatmemo.android;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class SearchPlannerTest {
    private Note note(String content){Note n=new Note();n.content=content;return n;}
    private SearchPlanner.Plan plan(List<Note> all,String q,RequestOptions options)throws Exception{return new SearchPlanner.Index(all,7).plan(q,"all",options);}
    @Test public void hitAt2200IsActuallySentAndMapped()throws Exception{
        Note n=note("甲".repeat(2200)+"聊的开心：分享自己的小经历。"+"乙".repeat(700));SearchPlanner.Plan p=plan(Arrays.asList(n),"聊的开心",RequestOptions.defaults());
        assertTrue(p.payload().toString().contains("聊的开心"));SearchPlanner.Window w=p.entries.get(0).windows.get(0);assertTrue(w.start>1400);assertEquals(w.text,n.content.substring(w.start,w.end));
        List<SearchEngine.Hit> found=p.results(new JSONObject().put("matches",new JSONArray().put(new JSONObject().put("id",n.id).put("quote","聊的开心").put("reason","包含当时想找的线索"))).toString());assertEquals(2200,found.get(0).sourceStart);
    }
    @Test public void defaultAndSmallBudgetsHoldFor10_100_1000Notes()throws Exception{
        for(int count:new int[]{10,100,1000}){List<Note> all=new ArrayList<>();for(int i=0;i<count;i++)all.add(note("开头"+"甲".repeat(4800)+"聊的开心"+"乙".repeat(500)));
            for(RequestOptions o:new RequestOptions[]{RequestOptions.defaults(),new RequestOptions(8192,4096,120,30,2000)}){
                SearchPlanner.Plan p=plan(all,"聊的开心",o);assertEquals(Math.min(count,o.candidates),p.entries.size());assertTrue(p.bodyChars<=o.searchChars);
                for(SearchPlanner.Entry e:p.entries){int size=0;assertTrue(e.windows.size()<=3);for(SearchPlanner.Window w:e.windows){size+=w.text.length();assertTrue(w.text.contains("聊的开心"));}assertTrue(size<=1200);}
            }
        }
    }
    @Test public void topicScopeIsAppliedBeforeSelectingCandidates()throws Exception{
        Note a=note("聊天经历");a.folder="工作";Note b=note("聊天经历");b.folder="生活";
        SearchPlanner.Plan p=new SearchPlanner.Index(Arrays.asList(a,b),1).plan("聊天","folder:生活",RequestOptions.defaults());assertEquals(1,p.entries.size());assertEquals(b.id,p.entries.get(0).note.id);
    }
    @Test public void metadataOnlyHitUsesAFallbackAndBoundedMetadata()throws Exception{
        Note n=note("甲".repeat(3000));n.title="聊天"+"长".repeat(2000);n.tags="标签".repeat(2000);SearchPlanner.Plan p=plan(Arrays.asList(n),"聊天",RequestOptions.defaults());assertTrue(p.entries.get(0).fallback);
        JSONObject row=p.payload().getJSONArray("notes").getJSONObject(0);assertEquals(120,row.getString("title").length());assertEquals(240,row.getString("tags").length());
    }
    @Test public void snippetsNeverExposeMarkersOrCrossImages()throws Exception{
        Note n=note("聊天左侧"+"![图片](attachment://11111111-1111-4111-8111-111111111111)"+"聊天右侧");SearchPlanner.Plan p=plan(Arrays.asList(n),"聊天",RequestOptions.defaults());
        for(SearchPlanner.Window w:p.entries.get(0).windows)assertFalse(w.text.contains("attachment://"));
        reject(p,n.id,"聊天左侧聊天右侧");
    }
    @Test public void independentWindowsCannotBeJoinedIntoAFakeQuote()throws Exception{
        Note n=note("聊天甲"+"x".repeat(1800)+"聊天乙"+"y".repeat(1800)+"聊天丙");SearchPlanner.Plan p=plan(Arrays.asList(n),"聊天",RequestOptions.defaults());
        assertTrue(p.entries.get(0).windows.size()>1);List<SearchPlanner.Window> w=p.entries.get(0).windows;reject(p,n.id,w.get(0).text+w.get(1).text);
    }
    private void reject(SearchPlanner.Plan p,String id,String quote)throws Exception{try{p.results(new JSONObject().put("matches",new JSONArray().put(new JSONObject().put("id",id).put("quote",quote).put("reason","相关"))).toString());fail("invalid quote accepted");}catch(Exception expected){assertNotNull(expected.getMessage());}}
    @Test public void escapedControlsAlsoRespectSerializedInputCap()throws Exception{
        Note n=note("\u0001".repeat(25000)+"聊天");RequestOptions o=new RequestOptions(8192,4096,120,5,2000);SearchPlanner.Plan p=plan(Arrays.asList(n),"聊天",o);assertTrue(p.payload().toString().length()<=o.searchChars+o.candidates*1000+4096);assertTrue(p.bodyChars<=o.searchChars);
    }
    @Test public void emptyImageOnlyLibraryUsesNoCandidates()throws Exception{assertEquals(0,plan(Arrays.asList(note("![图片](attachment://11111111-1111-4111-8111-111111111111)")),"图片",RequestOptions.defaults()).entries.size());}
    @Test public void repeatedQueryDoesNotMutateIndexOrSource()throws Exception{Note n=note("甲".repeat(5000)+"聊天");String body=n.content;SearchPlanner.Index index=new SearchPlanner.Index(Arrays.asList(n),99);String a=index.plan("聊天","all",RequestOptions.defaults()).payload().toString();String b=index.plan("聊天","all",RequestOptions.defaults()).payload().toString();assertEquals(a,b);assertEquals(body,n.content);}
    @Test public void caseInsensitiveOffsetsUseOriginalUtf16Indices()throws Exception{Note n=note("İ甲😀ABC故事");SearchPlanner.Plan p=plan(Arrays.asList(n),"abc",RequestOptions.defaults());SearchPlanner.Window w=p.entries.get(0).windows.get(0);assertTrue(w.text.contains("ABC"));assertEquals(w.text,n.content.substring(w.start,w.end));}
    @Test public void overlappingWindowsAreMerged()throws Exception{Note n=note("开头聊天"+"甲".repeat(50)+"聊天结束");SearchPlanner.Plan p=plan(Arrays.asList(n),"聊天",RequestOptions.defaults());assertEquals(1,p.entries.get(0).windows.size());}
    @Test public void fullRememberedPhraseWinsOverAnEarlierPartialHitUnderTightBudget()throws Exception{
        List<Note> all=new ArrayList<>();for(int i=0;i<30;i++)all.add(note("聊的另一些事"+"甲".repeat(5000)+"聊的开心"+"乙".repeat(1000)));
        SearchPlanner.Plan p=plan(all,"聊的开心",new RequestOptions(8192,4096,120,30,2000));for(SearchPlanner.Entry e:p.entries)assertTrue(e.windows.get(0).text.contains("聊的开心"));
    }
    @Test public void longQueriesAreRejectedBeforeAnyPlanOrRequest()throws Exception{try{plan(Arrays.asList(note("原文")),"甲".repeat(257),RequestOptions.defaults());fail();}catch(IllegalArgumentException expected){}}
}

import io.github.floatmemo.android.*;
import org.json.*;
import java.util.*;
/** Synthetic host benchmark; no network, personal notes or phone performance claims. */
public final class SearchBenchmark {
    public static void main(String[] args)throws Exception {
        JSONArray results=new JSONArray();
        for(int count:new int[]{10,100,1000}){
            List<Note> notes=new ArrayList<>();
            for(int i=0;i<count;i++){Note n=new Note();n.title="合成笔记"+i;n.folder="主题"+(i%5);n.tags="交流、回顾";n.content="日常随记".repeat(550)+"聊的开心：分享自己的小经历。"+"收尾".repeat(400);notes.add(n);}
            long before=System.nanoTime();SearchPlanner.Index index=new SearchPlanner.Index(notes,1);long indexed=System.nanoTime();
            SearchPlanner.Plan p=index.plan("聊的开心","all",RequestOptions.defaults());long finished=System.nanoTime();
            if(p.entries.size()>10||p.bodyChars>12000)throw new AssertionError("budget exceeded");
            results.put(new JSONObject().put("notes",count).put("charsPerNote",notes.get(0).content.length()).put("indexBuildMs",(indexed-before)/1000000.0).put("firstPlanMs",(finished-indexed)/1000000.0).put("candidates",p.entries.size()).put("bodyChars",p.bodyChars).put("serializedUserPayloadChars",p.payload().toString().length()).put("actualHttpCalls",0));
        }
        System.out.println(new JSONObject().put("environment","Linux x86_64, OpenJDK17 host; synthetic data only").put("phonePerformanceVerified",false).put("results",results).toString(2));
    }
}

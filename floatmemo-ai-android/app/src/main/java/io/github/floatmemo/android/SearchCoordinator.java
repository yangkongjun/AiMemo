package io.github.floatmemo.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;

/** Process-wide single-flight search, including Activity recreation. Never retries HTTP. */
public final class SearchCoordinator {
    public interface Listener {void searchChanged();}
    public static final class State {
        public final String key,query,scope;public final long version,epoch;
        public volatile SearchPlanner.Plan plan;public volatile List<SearchEngine.Hit> hits;public volatile String error="";
        public volatile boolean done;public long finishedAt;
        State(String key,String q,String scope,long version,long epoch){this.key=key;query=q;this.scope=scope;this.version=version;this.epoch=epoch;}
    }
    private static final SearchCoordinator INSTANCE=new SearchCoordinator();
    public static SearchCoordinator get(){return INSTANCE;}
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners=new HashSet<>();
    private final SearchSession<State> session=new SearchSession<>(20,300000,8*1024*1024);
    private SearchPlanner.Index index;
    private SearchCoordinator(){}
    public synchronized void observe(Listener l){listeners.add(l);}
    public synchronized void removeObserver(Listener l){listeners.remove(l);}
    public synchronized boolean busy(){return session.busy();}
    private void changed(){main.post(()->{List<Listener> copy;synchronized(this){copy=new ArrayList<>(listeners);}for(Listener l:copy)l.searchChanged();});}
    public synchronized State find(String key){return session.find(key,android.os.SystemClock.elapsedRealtime());}
    public synchronized String start(Context context,String query,String scope)throws Exception{
        Context c=context.getApplicationContext();AiConfig.Snapshot config=AiConfig.get(c).snapshot();RequestOptions options=config.options;long epoch=config.epoch;
        if(!config.enabled)throw new Exception("请先启用AI");String q=SearchPlanner.normalize(query);if(q.isEmpty()||q.length()>256)throw new Exception("搜索线索请填写1至256字符");
        NoteRepository repo=NoteRepository.get(c);long version=repo.corpusVersion();String key=NoteBodyParser.hash(q+"\n"+scope+"\n"+version+":"+epoch+":"+options.cacheKey());
        State state=new State(key,q,scope,version,epoch);SearchSession.Ticket<State> ticket=session.acquire(key,state,android.os.SystemClock.elapsedRealtime());if(!ticket.fresh){changed();return key;}changed();
        worker.execute(()->{
            try{
                if(index==null||index.version!=version){NoteRepository.Corpus snapshot=repo.corpus();if(snapshot.version!=version)throw new Exception("笔记已改变，请重新点击查找");index=new SearchPlanner.Index(snapshot.notes,version);}
                state.plan=index.plan(q,scope,options);changed();
                if(repo.corpusVersion()!=version||AiConfig.get(c).epoch()!=epoch||!AiConfig.get(c).enabled())throw new Exception("笔记或AI设置已改变，请重新查找");
                List<SearchEngine.Hit> result=state.plan.entries.isEmpty()?Collections.emptyList():AiClient.search(c,state.plan,epoch,options);
                if(repo.corpusVersion()!=version||AiConfig.get(c).epoch()!=epoch||!AiConfig.get(c).enabled())throw new Exception("笔记或AI设置已改变，本次结果未展示");
                state.hits=Collections.unmodifiableList(new ArrayList<>(result));
            }catch(Exception e){state.error=e.getMessage()==null?"搜索未完成，请重试":e.getMessage();}
            finally{synchronized(this){state.done=true;state.finishedAt=android.os.SystemClock.elapsedRealtime();long bytes=0;if(state.plan!=null)for(SearchPlanner.Entry e:state.plan.entries){bytes+=2L*e.note.content.length();for(SearchPlanner.Window w:e.windows)bytes+=2L*w.text.length();}session.complete(key,state,state.error.isEmpty(),bytes, state.finishedAt);}changed();}
        });return key;
    }
}

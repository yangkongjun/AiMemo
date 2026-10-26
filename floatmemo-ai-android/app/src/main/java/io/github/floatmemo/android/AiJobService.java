package io.github.floatmemo.android;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AiJobService extends JobService {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private static volatile boolean active;
    private AtomicBoolean stopped;private volatile Note current;private NoteRepository repo;
    public static void schedule(Context c){
        if(!AiConfig.get(c).enabled()||active)return;JobScheduler scheduler=(JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE);
        if(scheduler.getPendingJob(1042)!=null)return;
        JobInfo job=new JobInfo.Builder(1042,new ComponentName(c,AiJobService.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(5500).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build();scheduler.schedule(job);
    }
    @Override public boolean onStartJob(JobParameters params){
        active=true;repo=NoteRepository.get(this);stopped=new AtomicBoolean(false);AtomicBoolean cancelled=stopped;
        executor.execute(()->{int count=0;try{
            while(!cancelled.get()&&AiConfig.get(this).enabled()&&count++<3){Note n=repo.claim();current=n;if(n==null)break;
                try{long epoch=AiConfig.get(this).epoch();String result=AiClient.analyze(this,n,epoch).toString();
                    if(!cancelled.get()&&AiConfig.get(this).enabled()&&AiConfig.get(this).epoch()==epoch)repo.applyAi(n.id,n.revision,result);
                    else repo.release(n.id,n.revision,"整理已中断，可重试",false);
                }catch(Exception e){String msg=e.getMessage()==null?"整理失败，请重试":e.getMessage();boolean retry=msg.contains("超时")||msg.contains("无法连接")||msg.contains("限流");repo.release(n.id,n.revision,msg,retry);if(retry)break;}
                finally{current=null;}
            }
        }finally{active=false;if(!cancelled.get())jobFinished(params,AiConfig.get(this).enabled()&&repo.hasPending());}});return true;
    }
    @Override public boolean onStopJob(JobParameters params){active=false;if(stopped!=null)stopped.set(true);Note n=current;if(n!=null&&repo!=null)repo.release(n.id,n.revision,"系统中断，稍后重试",true);return AiConfig.get(this).enabled();}
    @Override public void onDestroy(){executor.shutdown();super.onDestroy();}
}

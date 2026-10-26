package io.github.floatmemo.android;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Owns only windows. Closing/hiding a window never deletes its note. */
public final class OverlayService extends Service implements NoteRepository.Listener {
    public static final String SHOW="io.github.floatmemo.SHOW", SHOW_ALL="io.github.floatmemo.SHOW_ALL";
    public static final String HIDE_ALL="io.github.floatmemo.HIDE_ALL", TOGGLE="io.github.floatmemo.TOGGLE", STOP="io.github.floatmemo.STOP";
    public static boolean running;
    private static final String CHANNEL="floating_notes";
    private final Map<String,FloatingNoteWindow> windows=new HashMap<>();
    private NoteRepository repo;
    private boolean hidden, started, destroying;

    @Override public void onCreate(){
        super.onCreate();repo=NoteRepository.get(this);repo.observe(this);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"悬浮便签",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("控制正在显示的悬浮便签");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?STOP:intent.getAction();
        if(STOP.equals(action)){stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)){
            Toast.makeText(this,"悬浮权限已关闭，请在应用中重新开启",Toast.LENGTH_LONG).show();
            stopSelf();return START_NOT_STICKY;
        }
        if(!started){
            try{
                if(Build.VERSION.SDK_INT>=34)startForeground(100,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                else startForeground(100,notification());
                started=true;running=true;
            }catch(RuntimeException e){
                android.util.Log.e("FloatMemo","Foreground service could not start",e);
                Toast.makeText(this,"无法启动常驻服务，请从应用内重试",Toast.LENGTH_LONG).show();
                stopSelf();return START_NOT_STICKY;
            }
        }
        if(SHOW_ALL.equals(action)){hidden=false;repo.setAllOpen(true);}
        else if(SHOW.equals(action)){
            hidden=false;String id=intent.getStringExtra("noteId");
            repo.update(id,n->n.open=true);
        }else if(HIDE_ALL.equals(action))hidden=true;
        else if(TOGGLE.equals(action))hidden=!hidden;
        reconcile();
        return START_NOT_STICKY;
    }
    @Override public void onNotesChanged(){if(started&&!destroying)reconcile();}
    private void reconcile(){
        if(!Settings.canDrawOverlays(this)){stopSelf();return;}
        Set<String> active=new HashSet<>();
        for(Note n:repo.all())if(n.open)active.add(n.id);
        for(String id:new ArrayList<>(windows.keySet())){
            if(hidden||!active.contains(id)){windows.remove(id).close();}
        }
        if(!hidden){
            for(Note n:repo.all())if(n.open){
                FloatingNoteWindow window=windows.get(n.id);
                if(window==null){
                    try{window=new FloatingNoteWindow(this,repo,n);windows.put(n.id,window);}
                    catch(RuntimeException e){
                        android.util.Log.e("FloatMemo","Cannot display overlay",e);
                        Toast.makeText(this,"悬浮窗显示失败，请检查权限",Toast.LENGTH_LONG).show();
                        stopSelf();return;
                    }
                }else window.refresh(n);
            }
        }
        if(active.isEmpty()){stopSelf();return;}
        getSystemService(NotificationManager.class).notify(100,notification());
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle=PendingIntent.getService(this,2,new Intent(this,OverlayService.class).setAction(TOGGLE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,3,new Intent(this,OverlayService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        int count=0;for(Note n:repo.all())if(n.open)count++;
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_note)
            .setContentTitle(hidden?"便签已隐藏":"FloatMemo · "+count+" 张悬浮便签")
            .setContentText("点击管理便签；可随时隐藏或停止")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(null,hidden?"显示便签":"隐藏便签",toggle).build())
            .addAction(new Notification.Action.Builder(null,"停止悬浮",stop).build()).build();
    }
    @Override public void onConfigurationChanged(Configuration config){
        super.onConfigurationChanged(config);
        for(FloatingNoteWindow window:new ArrayList<>(windows.values()))window.fitScreen();
    }
    @Override public void onDestroy(){
        destroying=true;repo.removeObserver(this);
        for(FloatingNoteWindow window:windows.values())window.close();windows.clear();
        repo.flush();running=false;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}

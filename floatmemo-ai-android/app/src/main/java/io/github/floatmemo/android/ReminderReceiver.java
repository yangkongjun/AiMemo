package io.github.floatmemo.android;
import android.app.*;
import android.content.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class ReminderReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){PendingResult pending=goAsync();Context c=context.getApplicationContext();new Thread(()->{try{handle(c,intent);}finally{pending.finish();}},"note-reminder").start();}
 private void handle(Context c,Intent intent){
  NoteRepository repo=NoteRepository.get(c);
  if(!"REMIND".equals(intent.getAction())&&!"COMPLETE".equals(intent.getAction())){ReminderScheduler.reschedule(c);return;}
  String id=intent.getStringExtra("noteId");long generation=intent.getLongExtra("generation",-1);
  synchronized(repo){Note n=repo.find(id);if(n==null||n.reminderGeneration!=generation)return;
   if("COMPLETE".equals(intent.getAction())){repo.editMetadata(id,x->{x.task=true;x.completedAt=System.currentTimeMillis();x.open=false;});return;}
   if(!NoteTasks.mayDeliver(n,generation,System.currentTimeMillis())){if(NoteTasks.reminderEligible(n))ReminderScheduler.schedule(c,n);return;}
   NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
   manager.createNotificationChannel(new NotificationChannel(ReminderScheduler.CHANNEL,"便签截止提醒",NotificationManager.IMPORTANCE_HIGH));
   NotificationChannel channel=manager.getNotificationChannel(ReminderScheduler.CHANNEL);if(!manager.areNotificationsEnabled()||channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return;
   PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,NoteEditorActivity.class).setData(android.net.Uri.parse("floatmemo://open/"+n.id)).putExtra("noteId",n.id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   PendingIntent done=PendingIntent.getBroadcast(c,0,new Intent(c,ReminderReceiver.class).setAction("COMPLETE").setData(android.net.Uri.parse("floatmemo://complete/"+n.id)).putExtra("noteId",n.id).putExtra("generation",generation),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   String due=new SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA).format(new Date(n.dueAt));
   Notification notification=new Notification.Builder(c,ReminderScheduler.CHANNEL).setSmallIcon(R.drawable.ic_note).setContentTitle(n.displayTitle()).setContentText("截止 "+due+"，这项任务完成了吗？").setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(new Notification.Action.Builder(null,"标记完成",done).build()).build();
   try{manager.notify(n.id,2001,notification);repo.editMetadata(id,x->x.reminderFiredAt=System.currentTimeMillis());}catch(SecurityException ignored){}
  }
 }
}

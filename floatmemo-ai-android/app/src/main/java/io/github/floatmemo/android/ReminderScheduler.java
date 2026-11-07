package io.github.floatmemo.android;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;

public final class ReminderScheduler {
 public static final String CHANNEL="note_deadlines";
 private ReminderScheduler(){}
 public static boolean exact(Context c){return Build.VERSION.SDK_INT<31||((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();}
 private static PendingIntent alarm(Context c,String id,long generation){return PendingIntent.getBroadcast(c,0,new Intent(c,ReminderReceiver.class).setAction("REMIND").setData(Uri.parse("floatmemo://reminder/"+id)).putExtra("noteId",id).putExtra("generation",generation),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 public static void cancel(Context c,String id){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(alarm(c,id,0));((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id,2001);}
 public static void schedule(Context c,Note n){
  cancel(c,n.id);long at=NoteTasks.nextAlarm(n,System.currentTimeMillis());if(at==0)return;AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);PendingIntent pending=alarm(c,n.id,n.reminderGeneration);
  try{if(exact(c))manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending);else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending);}
  catch(SecurityException e){try{manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending);}catch(RuntimeException ignored){}}
 }
 public static void reschedule(Context c){for(Note n:NoteRepository.get(c).all())if(NoteTasks.reminderEligible(n))schedule(c,n);}
 public static String status(Context c,Note n){
  if(n.reminderAt==0)return "提醒未开启";if(NoteTasks.done(n))return "已完成，提醒已取消";
  NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);NotificationChannel channel=m.getNotificationChannel(CHANNEL);
  if(!m.areNotificationsEnabled()||channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return "通知权限或提醒通知已关闭";
  if(n.reminderFiredAt>0)return "已发送提醒";if(n.dueAt<=System.currentTimeMillis())return "已过截止时间";
  return exact(c)?"精确提醒已安排":"近似提醒，可能延迟；可授予闹钟和提醒权限";
 }
}

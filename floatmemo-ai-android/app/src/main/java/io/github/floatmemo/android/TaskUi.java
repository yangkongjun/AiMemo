package io.github.floatmemo.android;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class TaskUi {
 private TaskUi(){}
 public static int color(Note n){return NoteTasks.done(n)?0xFFE4E6E4:Ui.COLORS[n.color];}
 public static String summary(Context c,Note n){String s=NoteTasks.label(n,System.currentTimeMillis());if(n.dueAt>0)s+=" · 截止 "+new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(n.dueAt));if(n.reminderAt>0&&!NoteTasks.done(n))s+="\n"+ReminderScheduler.status(c,n);return s;}
 public static void button(Activity a,LinearLayout root,String id){Note n=NoteRepository.get(a).find(id);if(n==null)return;Button button=Ui.button(a,summary(a,n)+" · 任务/提醒",false,v->show(a,id));button.setTextColor(NoteTasks.overdue(n,System.currentTimeMillis())?0xFFAF3030:Ui.GREEN);root.addView(button);Ui.space(root,6);}
 public static void show(Activity a,String id){NoteRepository repo=NoteRepository.get(a);Note n=repo.find(id);if(n==null)return;
  String[] labels={n.task?"改为普通便签":"设为待办",NoteTasks.done(n)?"恢复为待办":"标记整张便签完成","设置截止时间与提醒","清除截止时间和提醒","检查提醒权限"};
  new AlertDialog.Builder(a).setTitle(summary(a,n)).setItems(labels,(d,w)->{
   if(w==0)repo.editMetadata(id,x->{x.task=!n.task;if(!x.task){x.completedAt=0;x.dueAt=0;x.reminderAt=0;}});
   else if(w==1)repo.editMetadata(id,x->{x.task=true;x.completedAt=NoteTasks.done(n)?0:System.currentTimeMillis();x.open=false;});
   else if(w==2)deadline(a,id,n);
   else if(w==3)repo.editMetadata(id,x->{x.dueAt=0;x.reminderAt=0;});
   else permissions(a);
   if(!repo.error.isEmpty())Toast.makeText(a,repo.error,Toast.LENGTH_LONG).show();
  }).setNegativeButton("关闭",null).show();
 }
 private static void deadline(Activity a,String id,Note n){Calendar date=Calendar.getInstance();date.setTimeInMillis(n.dueAt>System.currentTimeMillis()?n.dueAt:System.currentTimeMillis()+7200000);
  new DatePickerDialog(a,(view,year,month,day)->{date.set(year,month,day);new TimePickerDialog(a,(v,h,m)->{date.set(Calendar.HOUR_OF_DAY,h);date.set(Calendar.MINUTE,m);date.set(Calendar.SECOND,0);date.set(Calendar.MILLISECOND,0);long due=date.getTimeInMillis();if(due<=System.currentTimeMillis()){Toast.makeText(a,"请选择未来的截止时间",Toast.LENGTH_LONG).show();return;}
   String[] options={"不提醒","提前15分钟","提前30分钟","提前1小时","提前2小时"};int[] minutes={-1,15,30,60,120};final int[] chosen={3};
   new AlertDialog.Builder(a).setTitle("提醒时间（默认提前1小时）").setSingleChoiceItems(options,3,(d,w)->chosen[0]=w).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{
    int lead=minutes[chosen[0]];long when=lead<0?0:due-lead*60000L;
    if(when>0&&when<=System.currentTimeMillis())new AlertDialog.Builder(a).setTitle("已进入提前提醒时间").setMessage("选择立即提醒一次，或只保存截止时间。")
     .setNegativeButton("只保存截止",(x,y)->save(a,id,due,0)).setPositiveButton("立即提醒",(x,y)->save(a,id,due,System.currentTimeMillis()+5000)).show();
    else save(a,id,due,when);
   }).show();
  },date.get(Calendar.HOUR_OF_DAY),date.get(Calendar.MINUTE),true).show();},date.get(Calendar.YEAR),date.get(Calendar.MONTH),date.get(Calendar.DAY_OF_MONTH)).show();
 }
 private static void save(Activity a,String id,long due,long when){NoteRepository repo=NoteRepository.get(a);if(repo.editMetadata(id,n->{n.task=true;n.completedAt=0;n.dueAt=due;n.reminderAt=when;})){Toast.makeText(a,"已保存截止时间"+(when>0?"和提醒":""),Toast.LENGTH_LONG).show();if(when>0)permissions(a);}else Toast.makeText(a,repo.error,Toast.LENGTH_LONG).show();}
 public static void permissions(Activity a){
  if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},93);return;}
  NotificationManager notifications=(NotificationManager)a.getSystemService(Context.NOTIFICATION_SERVICE);NotificationChannel channel=notifications.getNotificationChannel(ReminderScheduler.CHANNEL);
  if(!notifications.areNotificationsEnabled()||channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_NONE){new AlertDialog.Builder(a).setTitle("提醒通知已关闭").setMessage("请在系统通知设置中允许便签截止提醒。")
   .setNegativeButton("稍后",null).setPositiveButton("去设置",(d,w)->{try{Intent settings=new Intent(channel==null?Settings.ACTION_APP_NOTIFICATION_SETTINGS:Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName());if(channel!=null)settings.putExtra(Settings.EXTRA_CHANNEL_ID,ReminderScheduler.CHANNEL);a.startActivity(settings);}catch(Exception e){Toast.makeText(a,"请到系统设置开启提醒通知",Toast.LENGTH_LONG).show();}}).show();return;}
  if(!ReminderScheduler.exact(a))new AlertDialog.Builder(a).setTitle("允许精确提醒？").setMessage("当前只能使用可能延迟的近似提醒。可在系统中允许“闹钟和提醒”；返回后会重新安排有效提醒。")
   .setNegativeButton("使用近似提醒",null).setPositiveButton("去设置",(d,w)->{try{a.startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+a.getPackageName())));}catch(RuntimeException e){Toast.makeText(a,"请在系统设置检查闹钟和提醒权限",Toast.LENGTH_LONG).show();}}).show();
  else if(!((NotificationManager)a.getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled())new AlertDialog.Builder(a).setTitle("通知已关闭").setMessage("请允许通知，以便收到任务提醒。").setPositiveButton("通知设置",(d,w)->a.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.getPackageName()))).setNegativeButton("取消",null).show();
 }
}

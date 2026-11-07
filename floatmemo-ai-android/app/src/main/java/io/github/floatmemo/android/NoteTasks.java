package io.github.floatmemo.android;
import java.util.*;
public final class NoteTasks {
 private NoteTasks(){}
 public static boolean active(Note n){return n!=null&&n.deletedAt==0;}
 public static boolean done(Note n){return n.task&&n.completedAt>0;}
 public static boolean canFloat(Note n){return active(n)&&!done(n);}
 public static boolean overdue(Note n,long now){return active(n)&&n.task&&!done(n)&&n.dueAt>0&&n.dueAt<now;}
 public static boolean matches(Note n,int f){return active(n)&&(f==0||f==1&&n.task&&!done(n)||f==2&&done(n)||f==3&&!n.task);}
 public static boolean reminderEligible(Note n){return active(n)&&n.task&&!done(n)&&n.reminderAt>0&&n.reminderFiredAt==0;}
 public static long nextAlarm(Note n,long now){return !reminderEligible(n)||n.dueAt<=now?0:Math.max(n.reminderAt,now+3000);}
 public static boolean mayDeliver(Note n,long generation,long now){return reminderEligible(n)&&n.reminderGeneration==generation&&now>=n.reminderAt;}
 public static String label(Note n,long now){return n.deletedAt>0?"回收站":done(n)?"✓ 已完成":overdue(n,now)?"逾期未完成":n.task?"待办":"普通便签";}
 public static Comparator<Note> order(){return Comparator.comparingInt((Note n)->done(n)?1:0).thenComparing(Comparator.comparingLong((Note n)->n.updatedAt).reversed());}
 public static void resetImportedReminder(Note n){n.reminderGeneration++;n.reminderAt=0;n.reminderFiredAt=0;}
}

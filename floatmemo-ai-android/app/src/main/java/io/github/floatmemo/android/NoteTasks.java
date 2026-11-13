package io.github.floatmemo.android;
import java.util.*;
public final class NoteTasks {
 private NoteTasks(){}
 public static boolean active(Note n){return n!=null&&n.deletedAt==0;}
 public static boolean done(Note n){return n.task&&n.completedAt>0;}
 public static boolean canFloat(Note n){return active(n)&&!done(n);}
 public static boolean overdue(Note n,long now){return active(n)&&n.task&&!done(n)&&n.dueAt>0&&n.dueAt<=now;}
 public static boolean matches(Note n,int f){return active(n)&&(f==0||f==1&&n.task&&!done(n)||f==2&&done(n)||f==3&&!n.task);}
    public enum ReminderMode {
        OFF, NOTIFICATION, ALARM;

        static ReminderMode read(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                return OFF;
            }
        }
    }

    public enum ReminderStage {
        BEFORE, OVERDUE;

        ReminderMode mode(Note note) {
            if (this == OVERDUE) return note.overdueReminder;
            if (note.reminderAt == 0) return ReminderMode.OFF;
            return note.reminderUsesAlarm ? ReminderMode.ALARM : ReminderMode.NOTIFICATION;
        }

        long firedAt(Note note) {
            return this == BEFORE ? note.reminderFiredAt : note.overdueFiredAt;
        }

        void markFired(Note note, long now) {
            if (this == BEFORE) note.reminderFiredAt = now;
            else note.overdueFiredAt = now;
        }
    }

    public static boolean reminderEligible(Note note, ReminderStage stage) {
        return active(note) && note.task && !done(note) && note.dueAt > 0
            && stage.mode(note) != ReminderMode.OFF && stage.firedAt(note) == 0;
    }

    public static long nextAlarm(Note note, ReminderStage stage, long now) {
        if (!reminderEligible(note, stage)) return 0;
        if (stage == ReminderStage.OVERDUE) return Math.max(note.dueAt, now);
        if (now >= note.dueAt || note.reminderAt >= note.dueAt) return 0;
        return Math.min(note.dueAt - 1, Math.max(note.reminderAt, now + 3000));
    }

    public static boolean mayDeliver(Note note, ReminderStage stage, long generation, long now) {
        if (!reminderEligible(note, stage) || note.reminderGeneration != generation) return false;
        if (stage == ReminderStage.OVERDUE) return now >= note.dueAt;
        return now >= note.reminderAt && now < note.dueAt;
    }

    static void normalizeReminders(Note note) {
        if (!note.task) {
            note.completedAt = 0;
            note.dueAt = 0;
        }
        if (note.dueAt == 0) clearReminders(note);
        if (note.reminderAt == 0) note.reminderUsesAlarm = false;
    }

    static boolean updateReminderGeneration(Note before, Note after) {
        normalizeReminders(after);
        boolean newDeadline = before.dueAt != after.dueAt;
        boolean newLeadTime = before.reminderAt != after.reminderAt;
        boolean changed = newDeadline || newLeadTime || before.task != after.task
            || before.completedAt != after.completedAt || before.reminderUsesAlarm != after.reminderUsesAlarm
            || before.overdueReminder != after.overdueReminder;
        if (changed) after.reminderGeneration = before.reminderGeneration + 1;
        if (newDeadline || newLeadTime) after.reminderFiredAt = 0;
        if (newDeadline) after.overdueFiredAt = 0;
        return changed;
    }

    private static void clearReminders(Note note) {
        note.reminderAt = 0;
        note.reminderUsesAlarm = false;
        note.reminderFiredAt = 0;
        note.overdueReminder = ReminderMode.OFF;
        note.overdueFiredAt = 0;
    }
 public static String label(Note n,long now){return n.deletedAt>0?"回收站":done(n)?"✓ 已完成":overdue(n,now)?"逾期未完成":n.task?"待办":"普通便签";}
 public static Comparator<Note> order(){return Comparator.comparingInt((Note n)->done(n)?1:0).thenComparing(Comparator.comparingLong((Note n)->n.updatedAt).reversed());}
    public static void resetImportedReminder(Note note) {
        note.reminderGeneration++;
        clearReminders(note);
    }
}

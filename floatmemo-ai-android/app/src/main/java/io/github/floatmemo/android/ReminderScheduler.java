package io.github.floatmemo.android;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import static io.github.floatmemo.android.NoteTasks.ReminderMode;
import static io.github.floatmemo.android.NoteTasks.ReminderStage;

public final class ReminderScheduler {
    static final String BEFORE_CHANNEL = "note_deadlines";
    static final String OVERDUE_CHANNEL = "note_overdue";
    static final String ALARM_CHANNEL = "note_alarm";
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private ReminderScheduler() {}

    public static boolean exact(Context context) {
        return Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    private static Intent alarmIntent(Context context, String id, ReminderStage stage) {
        String action = stage == ReminderStage.BEFORE ? "REMIND" : "REMIND_OVERDUE";
        return new Intent(context, ReminderReceiver.class).setAction(action)
            .setData(Uri.parse("floatmemo://reminder/" + Uri.encode(id))).putExtra("noteId", id);
    }

    static PendingIntent openNote(Context context, String id) {
        Intent intent = new Intent(context, NoteEditorActivity.class)
            .setData(Uri.parse("floatmemo://open/" + Uri.encode(id))).putExtra("noteId", id);
        return PendingIntent.getActivity(context, 0, intent, FLAGS);
    }

    static int notificationId(ReminderStage stage) {
        return stage == ReminderStage.BEFORE ? 2001 : 2002;
    }

    private static void cancelAlarm(Context context, String id, ReminderStage stage) {
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, alarmIntent(context, id, stage),
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            context.getSystemService(AlarmManager.class).cancel(pending);
            pending.cancel();
        }
    }

    public static void cancel(Context context, String id) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        for (ReminderStage stage : ReminderStage.values()) {
            cancelAlarm(context, id, stage);
            notifications.cancel(id, notificationId(stage));
        }
    }

    // A configuration change invalidates old notification actions as well as scheduled alarms.
    public static void schedule(Context context, Note note) {
        cancel(context, note.id);
        for (ReminderStage stage : ReminderStage.values()) scheduleStage(context, note, stage);
    }

    static void scheduleStage(Context context, Note note, ReminderStage stage) {
        long at = NoteTasks.nextAlarm(note, stage, System.currentTimeMillis());
        if (at == 0) {
            cancelAlarm(context, note.id, stage);
            return;
        }
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (!notifications.areNotificationsEnabled()) return;

        AlarmManager manager = context.getSystemService(AlarmManager.class);
        Intent intent = alarmIntent(context, note.id, stage).putExtra("generation", note.reminderGeneration);
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent, FLAGS);
        try {
            if (exact(context)) {
                if (stage.mode(note) == ReminderMode.ALARM) {
                    manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, openNote(context, note.id)), pending);
                } else {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
                }
                return;
            }
        } catch (SecurityException e) {
            // Permission can change between the check and scheduling.
        } catch (RuntimeException e) {
            Log.w("FloatMemo", "Exact reminder could not be scheduled", e);
        }
        try {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
        } catch (RuntimeException e) {
            Log.w("FloatMemo", "Reminder could not be scheduled", e);
        }
    }

    public static void reschedule(Context context) {
        NoteRepository repository = NoteRepository.get(context);
        synchronized (repository) {
            for (Note note : repository.all()) {
                for (ReminderStage stage : ReminderStage.values()) {
                    if (NoteTasks.reminderEligible(note, stage)) scheduleStage(context, note, stage);
                }
            }
        }
    }

    static String channelId(ReminderStage stage, ReminderMode mode) {
        if (mode == ReminderMode.ALARM) return ALARM_CHANNEL;
        return stage == ReminderStage.BEFORE ? BEFORE_CHANNEL : OVERDUE_CHANNEL;
    }

    static void ensureChannels(NotificationManager manager) {
        manager.createNotificationChannel(new NotificationChannel(BEFORE_CHANNEL, "便签截止提醒",
            NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(OVERDUE_CHANNEL, "便签逾期提醒",
            NotificationManager.IMPORTANCE_HIGH));
        NotificationChannel alarm = new NotificationChannel(ALARM_CHANNEL, "便签闹钟响铃",
            NotificationManager.IMPORTANCE_HIGH);
        alarm.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        manager.createNotificationChannel(alarm);
    }

    static String status(Context context, Note note, ReminderStage stage) {
        ReminderMode mode = stage.mode(note);
        if (mode == ReminderMode.OFF) return "关闭";
        String sound = mode == ReminderMode.ALARM ? "闹钟铃声" : "普通通知";
        if (stage.firedAt(note) > 0) return sound + " · 已发送";
        if (stage == ReminderStage.BEFORE && System.currentTimeMillis() >= note.dueAt) return "已过提前提醒时段";
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(channelId(stage, mode));
        if (!manager.areNotificationsEnabled() || (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE)) {
            return sound + " · 通知已关闭";
        }
        return exact(context) ? sound : sound + " · 可能延迟";
    }
}

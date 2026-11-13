package io.github.floatmemo.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static io.github.floatmemo.android.NoteTasks.ReminderMode;
import static io.github.floatmemo.android.NoteTasks.ReminderStage;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                handle(app, intent);
            } finally {
                pending.finish();
            }
        }, "note-reminder").start();
    }

    private void handle(Context context, Intent intent) {
        String action = intent.getAction();
        boolean complete = "COMPLETE".equals(action);
        if (!complete && !"REMIND".equals(action) && !"REMIND_OVERDUE".equals(action)) {
            ReminderScheduler.reschedule(context);
            return;
        }
        ReminderStage stage = "REMIND_OVERDUE".equals(action) ? ReminderStage.OVERDUE : ReminderStage.BEFORE;
        String id = intent.getStringExtra("noteId");
        long generation = intent.getLongExtra("generation", -1);
        NoteRepository repository = NoteRepository.get(context);
        synchronized (repository) {
            Note note = repository.find(id);
            if (note == null || note.reminderGeneration != generation) return;
            if (complete) {
                repository.editMetadata(id, n -> {
                    n.completedAt = System.currentTimeMillis();
                    n.open = false;
                });
                return;
            }
            long now = System.currentTimeMillis();
            if (!NoteTasks.mayDeliver(note, stage, generation, now)) {
                ReminderScheduler.scheduleStage(context, note, stage);
                return;
            }
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            ReminderScheduler.ensureChannels(manager);
            ReminderMode mode = stage.mode(note);
            String channelId = ReminderScheduler.channelId(stage, mode);
            NotificationChannel channel = manager.getNotificationChannel(channelId);
            if (!manager.areNotificationsEnabled() || channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return;

            Intent finish = new Intent(context, ReminderReceiver.class).setAction("COMPLETE")
                .setData(Uri.parse("floatmemo://complete/" + Uri.encode(id) + "/" + generation))
                .putExtra("noteId", id).putExtra("generation", generation);
            PendingIntent done = PendingIntent.getBroadcast(context, 0, finish,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String due = new SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(new Date(note.dueAt));
            boolean overdue = stage == ReminderStage.OVERDUE;
            Notification notification = new Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_note)
                .setContentTitle((overdue ? "逾期未完成 · " : "截止前提醒 · ") + note.displayTitle())
                .setContentText((overdue ? "已过截止时间 " : "截止时间 ") + due)
                .setCategory(mode == ReminderMode.ALARM ? Notification.CATEGORY_ALARM : Notification.CATEGORY_REMINDER)
                .setContentIntent(ReminderScheduler.openNote(context, id))
                .setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(null, "标记完成", done).build())
                .build();
            if (!repository.editMetadata(id, n -> stage.markFired(n, now))) return;
            try {
                if (overdue) manager.cancel(id, ReminderScheduler.notificationId(ReminderStage.BEFORE));
                manager.notify(id, ReminderScheduler.notificationId(stage), notification);
            } catch (SecurityException e) {
                repository.editMetadata(id, n -> stage.markFired(n, 0));
            }
        }
    }
}

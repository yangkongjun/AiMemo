package io.github.floatmemo.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static io.github.floatmemo.android.NoteTasks.ReminderMode;
import static io.github.floatmemo.android.NoteTasks.ReminderStage;

public final class TaskUi {
    private static final int[] LEAD_MINUTES = {-1, 15, 30, 60, 120};
    private static final String[] LEAD_LABELS = {"关闭", "提前15分钟", "提前30分钟", "提前1小时", "提前2小时"};
    private static final String[] SOUND_LABELS = {"普通通知", "闹钟铃声"};

    private TaskUi() {}

    public static int color(Note note) {
        return NoteTasks.done(note) ? 0xFFE4E6E4 : Ui.COLORS[note.color];
    }

    public static String summary(Activity activity, Note note) {
        String status = NoteTasks.label(note, System.currentTimeMillis());
        if (note.dueAt == 0) return status;
        status += " · 截止 " + formatDate(note.dueAt, "MM-dd HH:mm");
        if (!NoteTasks.done(note)) {
            if (note.reminderAt > 0) status += "\n提前提醒 · " + ReminderScheduler.status(activity, note, ReminderStage.BEFORE);
            if (note.overdueReminder != ReminderMode.OFF) {
                status += "\n逾期提醒 · " + ReminderScheduler.status(activity, note, ReminderStage.OVERDUE);
            }
        }
        return status;
    }

    public static void button(Activity activity, LinearLayout root, String id) {
        Note note = NoteRepository.get(activity).find(id);
        if (note == null) return;
        Button button = Ui.button(activity, summary(activity, note) + " · 任务/提醒", false, v -> show(activity, id));
        button.setTextColor(NoteTasks.overdue(note, System.currentTimeMillis()) ? 0xFFAF3030 : Ui.GREEN);
        root.addView(button);
        Ui.space(root, 6);
    }

    public static void show(Activity activity, String id) {
        NoteRepository repository = NoteRepository.get(activity);
        Note note = repository.find(id);
        if (note == null) return;
        String[] labels = {
            note.task ? "改为普通便签" : "设为待办",
            NoteTasks.done(note) ? "恢复为待办" : "标记整张便签完成",
            "设置截止时间与提醒", "清除截止时间和提醒", "检查提醒权限"
        };
        new AlertDialog.Builder(activity).setTitle(summary(activity, note)).setItems(labels, (dialog, which) -> {
            switch (which) {
                case 0 -> repository.editMetadata(id, n -> n.task = !note.task);
                case 1 -> repository.editMetadata(id, n -> {
                    n.task = true;
                    n.completedAt = NoteTasks.done(note) ? 0 : System.currentTimeMillis();
                    n.open = false;
                });
                case 2 -> deadline(activity, note);
                case 3 -> repository.editMetadata(id, n -> n.dueAt = 0);
                default -> permissions(activity);
            }
            if (!repository.error.isEmpty()) toast(activity, repository.error);
        }).setNegativeButton("关闭", null).show();
    }

    private static void deadline(Activity activity, Note note) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(note.dueAt > 0 ? note.dueAt : System.currentTimeMillis() + 7200000);
        if (note.dueAt == 0) {
            date.set(Calendar.SECOND, 0);
            date.set(Calendar.MILLISECOND, 0);
        }
        LinearLayout form = Ui.column(activity);
        int padding = Ui.dp(activity, 20);
        form.setPadding(padding, padding, padding, padding);
        Button dateButton = Ui.button(activity, formatDate(date.getTimeInMillis(), "yyyy-MM-dd HH:mm"), false,
            v -> pickDeadline(activity, date, (Button) v));
        form.addView(Ui.text(activity, "截止时间", 15, Ui.INK));
        form.addView(dateButton);

        int leadSelection = 0;
        if (note.dueAt == 0) leadSelection = 3;
        else if (note.reminderAt > 0) {
            leadSelection = LEAD_MINUTES.length;
            for (int i = 1; i < LEAD_MINUTES.length; i++) {
                if (note.dueAt - note.reminderAt == LEAD_MINUTES[i] * 60000L) leadSelection = i;
            }
        }
        String[] leadOptions = leadSelection == LEAD_MINUTES.length
            ? new String[]{"关闭", "提前15分钟", "提前30分钟", "提前1小时", "提前2小时", "沿用原提醒时间"}
            : LEAD_LABELS;
        Spinner lead = addChoice(activity, form, "提前提醒时间", leadOptions, leadSelection);
        Spinner beforeSound = addChoice(activity, form, "提前提醒方式", SOUND_LABELS, note.reminderUsesAlarm ? 1 : 0);
        Spinner overdue = addChoice(activity, form, "到期未完成时提醒一次",
            new String[]{"关闭", "普通通知", "闹钟铃声"},
            note.dueAt == 0 ? ReminderMode.NOTIFICATION.ordinal() : note.overdueReminder.ordinal());
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("截止时间与提醒").setView(scroll)
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long due = date.getTimeInMillis();
            long now = System.currentTimeMillis();
            if (due != note.dueAt && due <= now) {
                toast(activity, "请选择未来的截止时间");
                return;
            }
            int choice = lead.getSelectedItemPosition();
            long beforeAt = choice == 0 ? 0 : choice == LEAD_MINUTES.length
                ? due - (note.dueAt - note.reminderAt) : due - LEAD_MINUTES[choice] * 60000L;
            boolean useAlarm = beforeSound.getSelectedItemPosition() == 1;
            ReminderMode overdueMode = ReminderMode.values()[overdue.getSelectedItemPosition()];
            if (beforeAt > 0 && beforeAt <= now && beforeAt != note.reminderAt && due > now) {
                long configuredBefore = beforeAt;
                new AlertDialog.Builder(activity).setTitle("已进入提前提醒时段")
                    .setMessage("可以现在提醒一次，或关闭本次提前提醒。")
                    .setNegativeButton("关闭提前提醒", (skip, w) -> {
                        if (save(activity, note.id, due, 0, false, overdueMode)) dialog.dismiss();
                    })
                    .setPositiveButton("立即提醒", (send, w) -> {
                        if (save(activity, note.id, due, configuredBefore, useAlarm, overdueMode)) dialog.dismiss();
                    }).show();
            } else if (save(activity, note.id, due, beforeAt, useAlarm, overdueMode)) {
                dialog.dismiss();
            }
        }));
        dialog.show();
    }

    private static Spinner addChoice(Activity activity, LinearLayout form, String label, String[] options, int selected) {
        Ui.space(form, 12);
        form.addView(Ui.text(activity, label, 15, Ui.INK));
        Spinner spinner = new Spinner(activity, Spinner.MODE_DROPDOWN);
        spinner.setContentDescription(label);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        spinner.setMinimumHeight(Ui.dp(activity, 48));
        form.addView(spinner);
        return spinner;
    }

    private static void pickDeadline(Activity activity, Calendar date, Button button) {
        new DatePickerDialog(activity, (picker, year, month, day) ->
            new TimePickerDialog(activity, (time, hour, minute) -> {
                date.set(year, month, day, hour, minute, 0);
                date.set(Calendar.MILLISECOND, 0);
                button.setText(formatDate(date.getTimeInMillis(), "yyyy-MM-dd HH:mm"));
            }, date.get(Calendar.HOUR_OF_DAY), date.get(Calendar.MINUTE), true).show(),
            date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static String formatDate(long millis, String pattern) {
        return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(millis));
    }

    private static boolean save(Activity activity, String id, long due, long beforeAt, boolean alarm, ReminderMode overdue) {
        NoteRepository repository = NoteRepository.get(activity);
        if (!repository.editMetadata(id, note -> {
            note.task = true;
            note.dueAt = due;
            note.reminderAt = beforeAt;
            note.reminderUsesAlarm = alarm;
            note.overdueReminder = overdue;
        })) {
            toast(activity, repository.error.isEmpty() ? "便签已不可用" : repository.error);
            return false;
        }
        toast(activity, "已保存截止时间与提醒");
        Note saved = repository.find(id);
        if (saved != null && (NoteTasks.reminderEligible(saved, ReminderStage.BEFORE)
            || NoteTasks.reminderEligible(saved, ReminderStage.OVERDUE))) {
            permissions(activity);
        }
        return true;
    }

    public static void permissions(Activity activity) {
        NotificationManager notifications = activity.getSystemService(NotificationManager.class);
        ReminderScheduler.ensureChannels(notifications);
        if (!notifications.areNotificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 93);
            } else {
                showNotificationSettings(activity, null);
            }
            return;
        }
        if (!ReminderScheduler.exact(activity)) {
            new AlertDialog.Builder(activity).setTitle("允许准时提醒？")
                .setMessage("开启系统的“闹钟和提醒”权限后，FloatMemo 可以按设定时间提醒。未开启时，提醒可能延迟。")
                .setNegativeButton("稍后", null).setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + activity.getPackageName()));
                    openSettings(activity, intent);
                }).show();
            return;
        }
        for (String id : new String[]{ReminderScheduler.BEFORE_CHANNEL, ReminderScheduler.OVERDUE_CHANNEL, ReminderScheduler.ALARM_CHANNEL}) {
            NotificationChannel channel = notifications.getNotificationChannel(id);
            if (channel.getImportance() == NotificationManager.IMPORTANCE_NONE) {
                showNotificationSettings(activity, channel);
                return;
            }
        }
    }

    private static void showNotificationSettings(Activity activity, NotificationChannel channel) {
        Intent intent = new Intent(channel == null ? Settings.ACTION_APP_NOTIFICATION_SETTINGS
            : Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
        if (channel != null) intent.putExtra(Settings.EXTRA_CHANNEL_ID, channel.getId());
        String name = channel == null ? "FloatMemo 通知" : channel.getName().toString();
        new AlertDialog.Builder(activity).setTitle(name + "已关闭").setNegativeButton("稍后", null)
            .setPositiveButton("去设置", (dialog, which) -> openSettings(activity, intent)).show();
    }

    private static void openSettings(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
        } catch (RuntimeException e) {
            toast(activity, "请在系统设置里检查 FloatMemo 的通知和闹钟权限");
        }
    }

    private static void toast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}

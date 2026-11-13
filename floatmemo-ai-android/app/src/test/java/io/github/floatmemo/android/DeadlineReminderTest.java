package io.github.floatmemo.android;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import static io.github.floatmemo.android.NoteTasks.ReminderStage.BEFORE;
import static io.github.floatmemo.android.NoteTasks.ReminderStage.OVERDUE;
import static io.github.floatmemo.android.NoteTasks.ReminderMode.*;

public class DeadlineReminderTest {
    private Note task() {
        Note note = new Note();
        note.task = true;
        note.dueAt = 10000;
        note.reminderAt = 4000;
        note.overdueReminder = ALARM;
        return note;
    }

    @Test public void stagesFireOnceAndUseDifferentModes() {
        Note note = task();
        assertEquals(NOTIFICATION, BEFORE.mode(note));
        assertEquals(ALARM, OVERDUE.mode(note));
        assertTrue(NoteTasks.mayDeliver(note, BEFORE, 0, 4000));
        assertFalse(NoteTasks.mayDeliver(note, OVERDUE, 0, 9999));
        BEFORE.markFired(note, 4000);
        assertFalse(NoteTasks.mayDeliver(note, BEFORE, 0, 4001));
        assertTrue(NoteTasks.mayDeliver(note, OVERDUE, 0, 10000));
        OVERDUE.markFired(note, 10000);
        assertFalse(NoteTasks.mayDeliver(note, OVERDUE, 0, 11000));
    }

    @Test public void lateDeliveryUsesOnlyTheOverdueStage() {
        Note note = task();
        assertFalse(NoteTasks.mayDeliver(note, BEFORE, 0, 10000));
        assertEquals(0, NoteTasks.nextAlarm(note, BEFORE, 12000));
        assertEquals(12000, NoteTasks.nextAlarm(note, OVERDUE, 12000));
        assertTrue(NoteTasks.mayDeliver(note, OVERDUE, 0, 12000));
    }

    @Test public void completingOrDeletingTaskSuppressesBothStages() {
        Note note = task();
        note.completedAt = 5000;
        assertFalse(NoteTasks.mayDeliver(note, OVERDUE, 0, 10000));
        assertEquals(0, NoteTasks.nextAlarm(note, BEFORE, 5000));
        note.completedAt = 0;
        note.deletedAt = 5100;
        assertEquals(0, NoteTasks.nextAlarm(note, OVERDUE, 11000));
    }

    @Test public void soundChangesRejectOldAlarmsWithoutRepeatingSentStages() {
        Note original = task();
        OVERDUE.markFired(original, 10000);
        Note changed = original.copy();
        changed.overdueReminder = NOTIFICATION;
        assertTrue(NoteTasks.updateReminderGeneration(original, changed));
        assertEquals(1, changed.reminderGeneration);
        assertFalse(NoteTasks.mayDeliver(changed, BEFORE, 0, 5000));
        assertFalse(NoteTasks.mayDeliver(changed, OVERDUE, 1, 11000));
        Note rescheduled = changed.copy();
        rescheduled.dueAt = 30000;
        assertTrue(NoteTasks.updateReminderGeneration(changed, rescheduled));
        assertEquals(30000, NoteTasks.nextAlarm(rescheduled, OVERDUE, 12000));
    }

    @Test public void storedModesRoundTripAndImportDisarmsBothStages() throws Exception {
        Note note = task();
        note.reminderUsesAlarm = true;
        OVERDUE.markFired(note, 10000);
        Note restored = Note.fromJson(note.toJson());
        assertEquals(ALARM, BEFORE.mode(restored));
        assertEquals(ALARM, OVERDUE.mode(restored));
        assertEquals(10000, OVERDUE.firedAt(restored));
        NoteTasks.resetImportedReminder(restored);
        assertEquals(10000, restored.dueAt);
        assertEquals(OFF, BEFORE.mode(restored));
        assertEquals(OFF, OVERDUE.mode(restored));
        assertEquals(0, OVERDUE.firedAt(restored));
    }

    @Test public void legacyDeadlineKeepsItsOrdinaryReminderWithoutSchedulingPastAlerts() throws Exception {
        Note old = Note.fromJson(new JSONObject().put("task", true).put("dueAt", 10000)
            .put("reminderAt", 4000));
        assertEquals(NOTIFICATION, BEFORE.mode(old));
        assertEquals(OFF, OVERDUE.mode(old));
        assertEquals(0, NoteTasks.nextAlarm(old, OVERDUE, 12000));
    }
}

package io.github.floatmemo.android;
import org.json.JSONObject;
/** Window state and transient task status do not make a duplicate into new content. */
public final class ImportPolicy {
    private ImportPolicy(){}
    public static String signature(Note n)throws Exception{
        org.json.JSONArray assets=new org.json.JSONArray();java.util.List<Attachment> sorted=NoteBodyParser.referenced(n);sorted.sort(java.util.Comparator.comparing(a->a.id));for(Attachment a:sorted)assets.put(a.toJson());
        JSONObject presentation=n.toJson();
        JSONObject signature = new JSONObject().put("task",n.task).put("completedAt",n.completedAt).put("dueAt",n.dueAt).put("reminderAt",n.reminderAt).put("deletedAt",n.deletedAt).put("formats",presentation.getJSONArray("formats")).put("checks",presentation.getJSONArray("checks")).put("attachments",assets).put("title",n.title).put("content",n.content).put("folder",n.folder).put("tags",n.tags)
            .put("revision",n.revision).put("aiRevision",n.aiRevision).put("ai",n.aiJson).put("folderLocked",n.folderLocked)
            .put("tagsLocked",n.tagsLocked).put("aiLocked",n.aiLocked).put("dismissedRelations",n.dismissedRelations);
        if (n.reminderUsesAlarm) signature.put("reminderUsesAlarm", true);
        if (n.overdueReminder != NoteTasks.ReminderMode.OFF) signature.put("overdueReminder", n.overdueReminder.name());
        return signature.toString();
    }
}

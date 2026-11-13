package io.github.floatmemo.android;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** Plain note data. All window coordinates and dimensions are density independent. */
public final class Note {
    public String id = UUID.randomUUID().toString();
    public String title = "";
    public String content = "";
    public String folder = "";
    public int color = 0;
    public float opacity = .96f;
    public int fontSize = 16;
    public int x = 20, y = 100, width = 292, height = 300;
    public boolean open = false, compact = false;
    public boolean task=false;
    public long completedAt=0,dueAt=0,reminderAt=0,reminderGeneration=0,reminderFiredAt=0,deletedAt=0;
    public boolean reminderUsesAlarm;
    public NoteTasks.ReminderMode overdueReminder = NoteTasks.ReminderMode.OFF;
    public long overdueFiredAt;
    public java.util.List<TextFormat.Range> formats=new java.util.ArrayList<>();
    public java.util.Map<Integer,Boolean> checks=new java.util.LinkedHashMap<>();
    public long updatedAt = System.currentTimeMillis();
    public long createdAt = updatedAt, revision = 1, aiRevision = 0;
    public String aiJson = "", aiStatus = "未整理", aiError = "", topicSuggestion = "";
    public String tags = "", dismissedRelations = "";
    public boolean folderLocked = false, tagsLocked = false, aiLocked = false, reviewed = false;
    public int attempts = 0;
    public String aiSourceHash="";
    public long aiGeneration=0;
    public java.util.List<Attachment> attachments=new java.util.ArrayList<>();

    public String displayTitle() {
        String value = title.trim();
        if (value.isEmpty()) value = NoteBodyParser.text(content).trim().split("\n", 2)[0];
        if (value.isEmpty()) return NoteBodyParser.ids(content).isEmpty()?"新便签":"图片笔记";
        int count = value.codePointCount(0, value.length());
        return count > 28 ? value.substring(0, value.offsetByCodePoints(0, 28)) + "…" : value;
    }

    public Note copy() {
        Note n = new Note();
        n.id = id; n.title = title; n.content = content; n.folder = folder;
        n.color = color; n.opacity = opacity; n.fontSize = fontSize;
        n.x = x; n.y = y; n.width = width; n.height = height;
        n.open = open; n.compact = compact; n.updatedAt = updatedAt;
        n.createdAt=createdAt; n.revision=revision; n.aiRevision=aiRevision; n.aiJson=aiJson;
        n.aiStatus=aiStatus; n.aiError=aiError; n.topicSuggestion=topicSuggestion; n.tags=tags;
        n.folderLocked=folderLocked; n.tagsLocked=tagsLocked; n.aiLocked=aiLocked;
        n.reviewed=reviewed; n.attempts=attempts;n.dismissedRelations=dismissedRelations;
        n.aiSourceHash=aiSourceHash;n.aiGeneration=aiGeneration;n.attachments=new java.util.ArrayList<>(attachments);
        n.task=task;n.completedAt=completedAt;n.dueAt=dueAt;n.reminderAt=reminderAt;n.reminderGeneration=reminderGeneration;n.reminderFiredAt=reminderFiredAt;n.deletedAt=deletedAt;n.formats=new java.util.ArrayList<>(formats);n.checks=new java.util.LinkedHashMap<>(checks);
        n.reminderUsesAlarm = reminderUsesAlarm;
        n.overdueReminder = overdueReminder;
        n.overdueFiredAt = overdueFiredAt;
        return n;
    }

    public JSONObject toJson() throws JSONException {
        org.json.JSONArray assets=new org.json.JSONArray();for(Attachment a:attachments)assets.put(a.toJson());
        org.json.JSONArray checked=new org.json.JSONArray();for(java.util.Map.Entry<Integer,Boolean> e:checks.entrySet())checked.put(new JSONObject().put("start",e.getKey()).put("checked",e.getValue()));
        return new JSONObject().put("task",task).put("completedAt",completedAt).put("dueAt",dueAt).put("reminderAt",reminderAt).put("reminderGeneration",reminderGeneration).put("reminderFiredAt",reminderFiredAt)
            .put("reminderUsesAlarm", reminderUsesAlarm).put("overdueReminder", overdueReminder.name())
            .put("overdueFiredAt", overdueFiredAt).put("deletedAt",deletedAt).put("formats",TextFormat.json(formats)).put("checks",checked).put("attachments",assets).put("aiSourceHash",aiSourceHash).put("aiGeneration",aiGeneration).put("id", id).put("title", title).put("content", content)
            .put("folder", folder).put("color", color).put("opacity", opacity)
            .put("fontSize", fontSize).put("x", x).put("y", y).put("width", width)
            .put("height", height).put("open", open).put("compact", compact)
            .put("updatedAt", updatedAt).put("createdAt",createdAt).put("revision",revision)
            .put("aiRevision",aiRevision).put("aiJson",aiJson).put("aiStatus",aiStatus)
            .put("aiError",aiError).put("topicSuggestion",topicSuggestion).put("tags",tags)
            .put("folderLocked",folderLocked).put("tagsLocked",tagsLocked).put("aiLocked",aiLocked)
            .put("reviewed",reviewed).put("attempts",attempts).put("dismissedRelations",dismissedRelations);
    }

    public static Note fromJson(JSONObject j) {
        Note n = new Note();
        n.id = j.optString("id", n.id);
        n.title = j.optString("title", ""); n.content = j.optString("content", "");
        n.folder = j.optString("folder", "");
        n.color = Math.max(0, Math.min(7, j.optInt("color", 0)));
        double opacity = j.optDouble("opacity", .96);
        n.opacity = Double.isFinite(opacity) ? (float) Math.max(.35, Math.min(1, opacity)) : .96f;
        n.fontSize = Math.max(12, Math.min(26, j.optInt("fontSize", 16)));
        n.x = j.optInt("x", 20); n.y = j.optInt("y", 100);
        n.width = Math.max(220, j.optInt("width", 292));
        n.height = Math.max(180, j.optInt("height", 300));
        n.open = j.optBoolean("open", false); n.compact = j.optBoolean("compact", false);
        n.updatedAt = j.optLong("updatedAt", System.currentTimeMillis());
        n.createdAt=j.optLong("createdAt",n.updatedAt);n.revision=Math.max(1,j.optLong("revision",1));
        n.aiRevision=j.optLong("aiRevision",0);n.aiJson=j.optString("aiJson","");
        n.aiStatus=j.optString("aiStatus","未整理");n.aiError=j.optString("aiError","");
        n.topicSuggestion=j.optString("topicSuggestion","");n.tags=j.optString("tags","");
        n.folderLocked=j.optBoolean("folderLocked",false);n.tagsLocked=j.optBoolean("tagsLocked",false);
        n.aiLocked=j.optBoolean("aiLocked",false);n.reviewed=j.optBoolean("reviewed",false);
        n.attempts=j.optInt("attempts",0);n.dismissedRelations=j.optString("dismissedRelations","");
        n.aiSourceHash=j.optString("aiSourceHash","");n.aiGeneration=Math.max(0,j.optLong("aiGeneration",0));
        org.json.JSONArray assets=j.optJSONArray("attachments");if(assets!=null)for(int i=0;i<assets.length();i++)try{n.attachments.add(Attachment.fromJson(assets.getJSONObject(i)));}catch(Exception e){throw new IllegalArgumentException("笔记附件信息损坏",e);}
        n.task=j.optBoolean("task",false);n.completedAt=Math.max(0,j.optLong("completedAt",0));n.dueAt=Math.max(0,j.optLong("dueAt",0));n.reminderAt=Math.max(0,j.optLong("reminderAt",0));n.reminderGeneration=Math.max(0,j.optLong("reminderGeneration",0));n.reminderFiredAt=Math.max(0,j.optLong("reminderFiredAt",0));n.deletedAt=Math.max(0,j.optLong("deletedAt",0));
        try{n.formats=TextFormat.read(j.optJSONArray("formats"));org.json.JSONArray a=j.optJSONArray("checks");if(a!=null){if(a.length()>TextFormat.MAX_RANGES)throw new IllegalArgumentException("清单状态过多");for(int i=0;i<a.length();i++){JSONObject c=a.getJSONObject(i);n.checks.put(c.getInt("start"),c.getBoolean("checked"));}}TextFormat.validate(n);}catch(JSONException e){throw new IllegalArgumentException("文字格式或清单数据损坏",e);}
        n.reminderUsesAlarm = j.optBoolean("reminderUsesAlarm", false);
        n.overdueReminder = NoteTasks.ReminderMode.read(j.optString("overdueReminder", ""));
        n.overdueFiredAt = Math.max(0, j.optLong("overdueFiredAt", 0));
        NoteTasks.normalizeReminders(n);
        if (!NoteTasks.canFloat(n)) n.open = false;
        return n;
    }
}

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
    public long updatedAt = System.currentTimeMillis();
    public long createdAt = updatedAt, revision = 1, aiRevision = 0;
    public String aiJson = "", aiStatus = "未整理", aiError = "", topicSuggestion = "";
    public String tags = "", dismissedRelations = "";
    public boolean folderLocked = false, tagsLocked = false, aiLocked = false, reviewed = false;
    public int attempts = 0;

    public String displayTitle() {
        String value = title.trim();
        if (value.isEmpty()) value = content.trim().split("\n", 2)[0];
        if (value.isEmpty()) return "新便签";
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
        return n;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("title", title).put("content", content)
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
        return n;
    }
}

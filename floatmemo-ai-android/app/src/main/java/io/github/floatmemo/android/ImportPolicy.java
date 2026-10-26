package io.github.floatmemo.android;
import org.json.JSONObject;
/** Window state and transient task status do not make a duplicate into new content. */
public final class ImportPolicy {
    private ImportPolicy(){}
    public static String signature(Note n)throws Exception{
        return new JSONObject().put("title",n.title).put("content",n.content).put("folder",n.folder).put("tags",n.tags)
            .put("revision",n.revision).put("aiRevision",n.aiRevision).put("ai",n.aiJson).put("folderLocked",n.folderLocked)
            .put("tagsLocked",n.tagsLocked).put("aiLocked",n.aiLocked).put("dismissedRelations",n.dismissedRelations).toString();
    }
}

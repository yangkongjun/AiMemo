package io.github.floatmemo.android;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class SelectionFormatTest {
    @Test public void boldToggleRecognizesMixedSelectionAndPreservesOutsideText() {
        List<TextFormat.Range> ranges = List.of(new TextFormat.Range(0, 4, "bold", 1));
        assertFalse(TextFormat.allBold(ranges, 2, 6));
        ranges = TextFormat.set(ranges, 2, 6, "bold", 1);
        assertTrue(TextFormat.allBold(ranges, 2, 6));
        ranges = TextFormat.set(ranges, 2, 6, "bold", 0);
        assertTrue(TextFormat.allBold(ranges, 0, 2));
        assertFalse(TextFormat.allBold(ranges, 2, 6));
    }

    @Test public void steppingMixedSizesChangesEachRunByOneAndLeavesOtherStylesIntact() {
        Note note = new Note();
        note.content = "abcdefgh";
        note.fontSize = 16;
        note.formats = TextFormat.set(note.formats, 1, 3, "size", 1.25);
        note.formats = TextFormat.set(note.formats, 5, 7, "sizeSp", 24);
        note.formats = TextFormat.set(note.formats, 0, 8, "color", 0xFF245EB0L);

        note.formats = TextFormat.stepSize(note, 2, 6, 1);
        assertRange(note, 1, 2, "size", 1.25);
        assertRange(note, 2, 3, "sizeSp", 21);
        assertRange(note, 3, 5, "sizeSp", 17);
        assertRange(note, 5, 6, "sizeSp", 25);
        assertRange(note, 6, 7, "sizeSp", 24);
        assertRange(note, 0, 8, "color", 0xFF245EB0L);
    }

    @Test public void presetReplacesOldRelativeSizeAndClearingRestoresBaseSize() {
        Note note = new Note();
        note.content = "abcd";
        note.formats = TextFormat.set(note.formats, 0, 4, "size", 1.5);
        note.formats = TextFormat.set(note.formats, 1, 3, "sizeSp", 16);
        assertRange(note, 0, 1, "size", 1.5);
        assertRange(note, 1, 3, "sizeSp", 16);
        assertRange(note, 3, 4, "size", 1.5);
        note.formats = TextFormat.set(note.formats, 1, 3, "size", 0);
        assertEquals(2, note.formats.size());
    }

    @Test public void colorAndPointSizeRoundTripWithoutChangingRawText() throws Exception {
        Note note = new Note();
        note.content = "😀文字";
        note.formats = TextFormat.set(note.formats, 2, 4, "color", 0xFFAF3030L);
        note.formats = TextFormat.set(note.formats, 2, 4, "sizeSp", 20);
        Note restored = Note.fromJson(note.toJson());
        assertEquals(note.content, restored.content);
        assertRange(restored, 2, 4, "color", 0xFFAF3030L);
        assertRange(restored, 2, 4, "sizeSp", 20);
    }

    private static void assertRange(Note note, int start, int end, String kind, double value) {
        assertTrue(note.formats.stream().anyMatch(r -> r.start == start && r.end == end
            && r.kind.equals(kind) && r.value == value));
    }
}

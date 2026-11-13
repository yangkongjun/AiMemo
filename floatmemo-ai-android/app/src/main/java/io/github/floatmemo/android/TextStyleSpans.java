package io.github.floatmemo.android;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.content.Context;

public final class TextStyleSpans {
    private interface LocalStyle {}
    private static final class Bold extends StyleSpan implements LocalStyle {
        Bold() { super(Typeface.BOLD); }
    }
    private static final class Size extends RelativeSizeSpan implements LocalStyle {
        Size(float factor) { super(factor); }
    }
    private static final class Color extends ForegroundColorSpan implements LocalStyle {
        Color(int color) { super(color); }
    }
    private static final class PointSize extends MetricAffectingSpan implements LocalStyle {
        private final float pixels;

        PointSize(Context context, int sp) {
            pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
        }

        @Override public void updateDrawState(TextPaint paint) { paint.setTextSize(pixels); }
        @Override public void updateMeasureState(TextPaint paint) { paint.setTextSize(pixels); }
    }
    private static final class Strike extends StrikethroughSpan implements LocalStyle {}

    private TextStyleSpans() {}

    private static Object span(Context context, TextFormat.Range range) {
        return switch (range.kind) {
            case "bold" -> new Bold();
            case "size" -> new Size((float) range.value);
            case "sizeSp" -> new PointSize(context, (int) range.value);
            case "color" -> new Color((int) (long) range.value);
            default -> throw new IllegalArgumentException("未知文字格式");
        };
    }

    public static void apply(FormatEditText editor, Note note) {
        Editable text = editor.getText();
        if (!text.toString().equals(note.content)) return;
        for (LocalStyle span : text.getSpans(0, text.length(), LocalStyle.class)) text.removeSpan(span);
        for (TextFormat.Range range : note.formats) {
            text.setSpan(span(editor.getContext(), range), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for (TextFormat.Item item : TextFormat.items(note.content)) {
            if (TextFormat.checked(note, item) && item.textStart < item.end) {
                text.setSpan(new Strike(), item.textStart, item.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    public static void append(Context context, SpannableStringBuilder out, String value, int rawOffset, Note note) {
        int start = out.length();
        out.append(value);
        if (note == null) return;
        for (TextFormat.Range range : note.formats) {
            int left = Math.max(rawOffset, range.start), right = Math.min(rawOffset + value.length(), range.end);
            if (left < right) {
                out.setSpan(span(context, range), start + left - rawOffset, start + right - rawOffset,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
}

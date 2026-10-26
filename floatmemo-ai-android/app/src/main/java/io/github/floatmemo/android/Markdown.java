package io.github.floatmemo.android;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small offline renderer: headings, lists, quotes, fenced code, bold and inline code. */
public final class Markdown {
    private static final Pattern INLINE = Pattern.compile("(\\*\\*(.+?)\\*\\*|`([^`]+)`)");
    public static CharSequence render(String source) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        boolean code = false;
        for (String line : source.split("\n", -1)) {
            if (line.trim().startsWith("```")) { code = !code; continue; }
            int start = out.length();
            if (code) {
                out.append(line).append('\n');
                span(out,new TypefaceSpan("monospace"),start,out.length());
                span(out,new BackgroundColorSpan(0x18333333),start,out.length());
            } else {
                int heading = 0;
                while (heading < line.length() && heading < 6 && line.charAt(heading) == '#') heading++;
                boolean isHeading = heading > 0 && line.length() > heading && line.charAt(heading) == ' ';
                String text = isHeading ? line.substring(heading+1) : line;
                if (text.startsWith("- ") || text.startsWith("* ")) text = "• " + text.substring(2);
                if (text.startsWith("> ")) text = "│ " + text.substring(2);
                appendInline(out,text); out.append('\n');
                if (isHeading) {
                    span(out,new StyleSpan(Typeface.BOLD),start,out.length());
                    span(out,new RelativeSizeSpan(heading == 1 ? 1.6f : heading == 2 ? 1.35f : 1.15f),start,out.length());
                }
                if (line.startsWith("> ")) span(out,new ForegroundColorSpan(Ui.MUTED),start,out.length());
            }
        }
        return out;
    }
    private static void appendInline(SpannableStringBuilder out, String line) {
        Matcher m = INLINE.matcher(line); int cursor = 0;
        while (m.find()) {
            out.append(line,cursor,m.start()); int start = out.length();
            boolean bold = m.group(2) != null;
            out.append(bold ? m.group(2) : m.group(3));
            span(out,bold ? new StyleSpan(Typeface.BOLD) : new TypefaceSpan("monospace"),start,out.length());
            cursor = m.end();
        }
        out.append(line,cursor,line.length());
    }
    private static void span(SpannableStringBuilder text, Object span, int start, int end) {
        if (end > start) text.setSpan(span,start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}

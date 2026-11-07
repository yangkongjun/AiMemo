package io.github.floatmemo.android;
import android.graphics.Typeface;
import android.text.*;
import android.text.style.*;
public final class TextStyleSpans {
 private static final class Bold extends StyleSpan {Bold(){super(Typeface.BOLD);}}
 private static final class Size extends RelativeSizeSpan {Size(float v){super(v);}}
 private static final class Strike extends StrikethroughSpan {}
 private TextStyleSpans(){}
 public static void apply(Editable text,Note n){if(!text.toString().equals(n.content))return;for(Bold s:text.getSpans(0,text.length(),Bold.class))text.removeSpan(s);for(Size s:text.getSpans(0,text.length(),Size.class))text.removeSpan(s);for(Strike s:text.getSpans(0,text.length(),Strike.class))text.removeSpan(s);
  for(TextFormat.Range r:n.formats)text.setSpan(r.kind.equals("bold")?new Bold():new Size((float)r.value),r.start,r.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  for(TextFormat.Item i:TextFormat.items(n.content))if(TextFormat.checked(n,i)&&i.textStart<i.end)text.setSpan(new Strike(),i.textStart,i.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
 }
 public static void append(SpannableStringBuilder out,String value,int rawOffset,Note n){int start=out.length();out.append(value);if(n==null)return;for(TextFormat.Range r:n.formats){int a=Math.max(rawOffset,r.start),b=Math.min(rawOffset+value.length(),r.end);if(a<b)out.setSpan(r.kind.equals("bold")?new StyleSpan(Typeface.BOLD):new RelativeSizeSpan((float)r.value),start+a-rawOffset,start+b-rawOffset,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}}
}

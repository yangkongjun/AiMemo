package io.github.floatmemo.android;
import android.graphics.Typeface;
import android.text.*;
import android.text.style.*;
import java.util.regex.*;
/** Source-aware, offline renderer retaining the previous Markdown subset. */
public final class Markdown {
 private static final Pattern INLINE=Pattern.compile("(\\*\\*(.+?)\\*\\*|`([^`]+)`)");
 public static CharSequence render(String source){return render(source,null,0);}
 public static CharSequence render(String source,Note note,int base){SpannableStringBuilder out=new SpannableStringBuilder();boolean code=false;int raw=base;
  for(String line:source.split("\n",-1)){int lineRaw=raw;raw+=line.length()+1;if(line.trim().startsWith("```")){code=!code;continue;}int start=out.length();
   if(code){TextStyleSpans.append(out,line,lineRaw,note);out.append('\n');span(out,new TypefaceSpan("monospace"),start,out.length());span(out,new BackgroundColorSpan(0x18333333),start,out.length());}
   else{int h=0;while(h<line.length()&&h<6&&line.charAt(h)=='#')h++;boolean heading=h>0&&line.length()>h&&line.charAt(h)==' ';int skip=heading?h+1:0;String text=line.substring(skip);
    if(text.startsWith("- ")||text.startsWith("* "))text="• "+text.substring(2);if(text.startsWith("> "))text="│ "+text.substring(2);
    inline(out,text,note,lineRaw+skip);out.append('\n');if(heading){span(out,new StyleSpan(Typeface.BOLD),start,out.length());span(out,new RelativeSizeSpan(h==1?1.6f:h==2?1.35f:1.15f),start,out.length());}if(line.startsWith("> "))span(out,new ForegroundColorSpan(Ui.MUTED),start,out.length());
   }
  }return out;
 }
 private static void inline(SpannableStringBuilder out,String text,Note n,int raw){Matcher m=INLINE.matcher(text);int cursor=0;while(m.find()){TextStyleSpans.append(out,text.substring(cursor,m.start()),raw+cursor,n);int start=out.length();boolean bold=m.group(2)!=null;int a=bold?m.start(2):m.start(3),b=bold?m.end(2):m.end(3);TextStyleSpans.append(out,text.substring(a,b),raw+a,n);span(out,bold?new StyleSpan(Typeface.BOLD):new TypefaceSpan("monospace"),start,out.length());cursor=m.end();}TextStyleSpans.append(out,text.substring(cursor),raw+cursor,n);}
 private static void span(SpannableStringBuilder out,Object span,int a,int b){if(a<b)out.setSpan(span,a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
}

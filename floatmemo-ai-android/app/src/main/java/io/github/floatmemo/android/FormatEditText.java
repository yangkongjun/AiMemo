package io.github.floatmemo.android;
import android.content.Context;
import android.widget.EditText;
public final class FormatEditText extends EditText {
 public Runnable selectionChanged;
 public FormatEditText(Context c){super(c);setTextColor(Ui.INK);setHintTextColor(Ui.MUTED);setTextSize(16);setPadding(Ui.dp(c,14),Ui.dp(c,10),Ui.dp(c,14),Ui.dp(c,10));setBackground(Ui.round(c,0xFFFFFFFF,12));}
 @Override protected void onFocusChanged(boolean focused,int direction,android.graphics.Rect previous){
  super.onFocusChanged(focused,direction,previous);if(selectionChanged!=null)selectionChanged.run();
 }
 @Override protected void onSelectionChanged(int start,int end){super.onSelectionChanged(start,end);if(selectionChanged!=null)selectionChanged.run();}
}

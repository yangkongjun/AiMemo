package io.github.floatmemo.android;
import android.content.Context;
import android.view.*;
import android.widget.*;
public final class FormatToolbar extends HorizontalScrollView {
 private final FormatEditText edit;private final NoteRepository repo;private final String id;private int a,b;private boolean touching;
 public FormatToolbar(Context c,FormatEditText e,NoteRepository r,String noteId){super(c);edit=e;repo=r;id=noteId;setHorizontalScrollBarEnabled(true);setFillViewport(false);LinearLayout row=Ui.row(c);addView(row);setBackground(Ui.round(c,0xFFE7EDE6,12));
  add(row,"加粗","bold",1);add(row,"取消粗体","bold",0);add(row,"普通字号","size",0);add(row,"大号","size",1.25);add(row,"特大","size",1.5);add(row,"清除格式","all",0);
  edit.selectionChanged=this::refresh;setVisibility(GONE);setContentDescription("选中文字后左右滑动选择格式");
 }
 private void add(LinearLayout row,String label,String kind,double value){Button button=Ui.button(getContext(),label,false,v->apply(kind,value));button.setFocusable(false);button.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN){capture();touching=true;}else if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){touching=false;refresh();}return false;});row.addView(button,new LinearLayout.LayoutParams(-2,Ui.dp(getContext(),44)));}
 private boolean capture(){int x=edit.getSelectionStart(),y=edit.getSelectionEnd();if(x>=0&&y>=0&&x!=y){a=Math.min(x,y);b=Math.max(x,y);return true;}return false;}
 public void refresh(){if(touching)return;setVisibility(capture()?VISIBLE:GONE);}
 private void apply(String kind,double value){try{Note n=repo.find(id);if(n==null||a>=b||b>n.content.length()||!n.content.equals(edit.getText().toString()))throw new IllegalArgumentException("文字已改变，请重新选择");final int start=a,end=b;
   boolean saved=repo.editMetadata(id,x->{if(kind.equals("all")){x.formats=TextFormat.set(x.formats,start,end,"bold",0);x.formats=TextFormat.set(x.formats,start,end,"size",0);}else x.formats=TextFormat.set(x.formats,start,end,kind,value);});
   if(!saved)throw new IllegalArgumentException(repo.error);Note current=repo.find(id);if(current!=null)TextStyleSpans.apply(edit.getText(),current);edit.setSelection(start,end);
  }catch(Exception e){Toast.makeText(getContext(),e.getMessage(),Toast.LENGTH_LONG).show();}finally{touching=false;refresh();}}
}

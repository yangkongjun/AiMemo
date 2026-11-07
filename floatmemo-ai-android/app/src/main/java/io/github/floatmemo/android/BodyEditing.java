package io.github.floatmemo.android;
import android.text.*;
import java.util.function.BooleanSupplier;
public final class BodyEditing {
 private BodyEditing(){}
 public static void attach(FormatEditText body,NoteRepository repo,String id,BooleanSupplier binding,BooleanSupplier editable){body.addTextChangedListener(new TextWatcher(){int newline=-1;boolean continuing;
  public void beforeTextChanged(CharSequence s,int start,int count,int after){}
  public void onTextChanged(CharSequence s,int start,int before,int count){newline=!binding.getAsBoolean()&&!continuing&&before==0&&count==1&&s.charAt(start)=='\n'?start:-1;}
  public void afterTextChanged(Editable e){if(binding.getAsBoolean()||!editable.getAsBoolean())return;if(newline>=0){ListEditing.Edit next=ListEditing.newline(e.toString(),newline);newline=-1;if(next!=null){continuing=true;e.replace(next.start,next.end,next.text);body.setSelection(Math.min(e.length(),next.start+next.text.length()));continuing=false;return;}}
   repo.update(id,n->n.content=e.toString());Note n=repo.find(id);if(n!=null)TextStyleSpans.apply(e,n);
  }
 });}
 public static void insertList(FormatEditText body,String prefix){int x=body.getSelectionStart(),y=body.getSelectionEnd();int a=Math.max(0,Math.min(x,y)),b=Math.max(a,Math.max(x,y));String raw=body.getText().toString();int start=raw.lastIndexOf('\n',a-1)+1;String selected=raw.substring(start,b);String result=ListEditing.prefixLines(selected,prefix);body.getText().replace(start,b,result);body.setSelection(Math.min(body.length(),start+result.length()));body.requestFocus();}
}

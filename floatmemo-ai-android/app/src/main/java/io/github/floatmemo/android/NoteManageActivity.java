package io.github.floatmemo.android;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Batch operations retain stable IDs and apply only to the currently visible result scope. */
public final class NoteManageActivity extends BaseActivity implements NoteRepository.Listener {
 private NoteRepository repo;private final List<Note> rows=new ArrayList<>();private final Set<String> selected=new LinkedHashSet<>();private boolean trash;private int filter;private String kind,value,query;private ArrayList<String> candidateIds;private Cards adapter;private TextView count;private Button action,restore;
 @Override public void onCreate(Bundle state){super.onCreate(state);repo=NoteRepository.get(this);trash=getIntent().getBooleanExtra("trash",false);kind=getIntent().getStringExtra("kind");value=getIntent().getStringExtra("value");query=getIntent().getStringExtra("query");candidateIds=getIntent().getStringArrayListExtra("candidateIds");filter=getIntent().getIntExtra("taskFilter",0);
  if(state!=null){filter=state.getInt("filter",filter);ArrayList<String> ids=state.getStringArrayList("selected");if(ids!=null)selected.addAll(ids);}else {String id=getIntent().getStringExtra("selected");if(id!=null)selected.add(id);}
  LinearLayout root=Ui.column(this);Ui.insetActivity(this,root,16);root.addView(Ui.button(this,"‹ 返回",false,v->finish()));Ui.space(root,14);root.addView(Ui.bold(this,trash?"回收站":"批量管理",25,Ui.INK));
  count=Ui.text(this,"",13,Ui.MUTED);root.addView(count);root.addView(Ui.text(this,trash?"恢复会保留原文、图片、样式与任务状态。":"点击卡片勾选；全选只作用于当前列表。",13,Ui.MUTED));
  if(!trash){Spinner spinner=new Spinner(this);spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"全部状态","待办","已完成","普通便签"}));spinner.setSelection(filter);spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(filter!=position){filter=position;selected.clear();refresh();}}public void onNothingSelected(AdapterView<?> p){}});root.addView(spinner);}
  LinearLayout tools=Ui.row(this);tools.addView(Ui.button(this,"全选当前列表",false,v->{for(Note n:rows)selected.add(n.id);adapter.notifyDataSetChanged();labels();}));tools.addView(Ui.button(this,"取消选择",false,v->{selected.clear();adapter.notifyDataSetChanged();labels();}));HorizontalScrollView toolScroll=new HorizontalScrollView(this);toolScroll.addView(tools);root.addView(toolScroll);
  ListView list=new ListView(this);list.setDividerHeight(Ui.dp(this,8));list.setDivider(new android.graphics.drawable.ColorDrawable(Ui.BG));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));adapter=new Cards();list.setAdapter(adapter);list.setOnItemClickListener((p,v,position,id)->{String key=rows.get(position).id;if(!selected.remove(key))selected.add(key);adapter.notifyDataSetChanged();labels();});
  LinearLayout footer=Ui.row(this);if(trash){restore=Ui.button(this,"恢复",false,v->perform(true));footer.addView(restore,new LinearLayout.LayoutParams(0,-2,1));}
  action=Ui.button(this,trash?"永久删除":"移入回收站",true,v->perform(false));footer.addView(action,new LinearLayout.LayoutParams(0,-2,1));root.addView(footer);
 }
 @Override protected void onStart(){super.onStart();repo.observe(this);refresh();}
 @Override protected void onStop(){repo.removeObserver(this);super.onStop();}
 @Override public void onNotesChanged(){refresh();}
 @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putStringArrayList("selected",new ArrayList<>(selected));out.putInt("filter",filter);}
 private boolean matches(Note n){if(trash)return true;if(!NoteTasks.matches(n,filter))return false;if(candidateIds!=null&&!candidateIds.contains(n.id))return false;if("folder".equals(kind)&&!Objects.equals(value,n.folder))return false;if("tag".equals(kind)){boolean found=false;for(String t:n.tags.split("、"))if(t.trim().equals(value))found=true;if(!found)return false;}return true;}
 private void refresh(){if(adapter==null)return;List<Note> source=trash?repo.trash():repo.all();if(!trash&&query!=null&&!query.trim().isEmpty()){List<Note> result=new ArrayList<>();for(SearchEngine.Hit hit:SearchEngine.local(source,query))result.add(hit.note);source=result;}
  rows.clear();Set<String> visible=new HashSet<>();for(Note n:source)if(matches(n)){rows.add(n);visible.add(n.id);}selected.retainAll(visible);adapter.notifyDataSetChanged();labels();}
 private void labels(){count.setText(rows.size()+" 条 · 已选 "+selected.size()+" 条");action.setText((trash?"永久删除":"移入回收站")+"（"+selected.size()+"）");action.setEnabled(!selected.isEmpty());if(restore!=null){restore.setText("恢复（"+selected.size()+"）");restore.setEnabled(!selected.isEmpty());}}
 private void perform(boolean recover){final Set<String> ids=new LinkedHashSet<>(selected);if(ids.isEmpty())return;
  String title=recover?"恢复所选便签？":trash?"永久删除所选便签？":"移入回收站？";String message=ids.size()+" 条便签。"+(recover?"原状态保留；截止已过不补发，截止前未发送的提醒会重新安排。":trash?"便签及其整理历史将无法在应用中恢复；已另存的备份不受影响。":"可以在首页“更多 → 回收站”恢复。");
  new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消",null).setPositiveButton(recover?"恢复":trash?"永久删除":"移入回收站",(d,w)->{Set<String> visible=new HashSet<>();for(Note n:rows)visible.add(n.id);ids.retainAll(visible);boolean ok=trash&&!recover?repo.purgeMany(ids):repo.trashMany(ids,recover);if(ok){selected.clear();refresh();Toast.makeText(this,"操作完成",Toast.LENGTH_SHORT).show();}else Toast.makeText(this,repo.error,Toast.LENGTH_LONG).show();}).show();
 }
 private final class Cards extends BaseAdapter {
  public int getCount(){return rows.size();}public Note getItem(int p){return rows.get(p);}public long getItemId(int p){return p;}
  public View getView(int p,View recycled,ViewGroup parent){CheckedTextView view=recycled instanceof CheckedTextView?(CheckedTextView)recycled:new CheckedTextView(NoteManageActivity.this);Note n=getItem(p);view.setCheckMarkDrawable(android.R.drawable.checkbox_off_background);view.setChecked(selected.contains(n.id));view.setCheckMarkDrawable(selected.contains(n.id)?android.R.drawable.checkbox_on_background:android.R.drawable.checkbox_off_background);view.setTextSize(15);view.setTextColor(NoteTasks.done(n)?Ui.MUTED:Ui.INK);view.setMaxLines(6);view.setEllipsize(TextUtils.TruncateAt.END);int pad=Ui.dp(NoteManageActivity.this,14);view.setPadding(pad,pad,pad,pad);view.setBackground(Ui.round(NoteManageActivity.this,selected.contains(n.id)?0xFFDDEBDD:0xFFFFFFFF,12));view.setText(n.displayTitle()+"\n"+NoteBodyParser.bounded(NoteBodyParser.text(n.content).replaceAll("\\s+"," "),100)+"\n"+TaskUi.summary(NoteManageActivity.this,n));return view;}
 }
}

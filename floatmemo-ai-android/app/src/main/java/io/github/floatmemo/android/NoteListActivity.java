package io.github.floatmemo.android;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Live, recycled note cards for one tag or folder. Does not request AI or modify notes. */
public final class NoteListActivity extends BaseActivity implements NoteRepository.Listener {
    private NoteRepository repo;
    private String kind,value,restoreId="";
    private int restoreTop;
    private ListView list;
    private TextView count;
    private final List<Note> notes=new ArrayList<>();
    private Cards adapter;
    private final SimpleDateFormat date=new SimpleDateFormat("yyyy.MM.dd HH:mm",Locale.CHINA);

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        repo=NoteRepository.get(this);
        kind=getIntent().getStringExtra("kind");value=getIntent().getStringExtra("value");
        if(value==null||!("tag".equals(kind)||"folder".equals(kind))){finish();return;}
        if(state!=null){restoreId=state.getString("noteAnchor","");restoreTop=state.getInt("noteTop",0);}

        LinearLayout root=Ui.column(this);Ui.insetActivity(this,root,16);
        LinearLayout bar=Ui.row(this);
        bar.addView(Ui.button(this,"‹ 主题与标签",false,v->finish()));
        View gap=new View(this);bar.addView(gap,new LinearLayout.LayoutParams(0,1,1));
        count=Ui.text(this,"",13,Ui.MUTED);bar.addView(count);root.addView(bar);
        Ui.space(root,22);
        root.addView(Ui.text(this,"tag".equals(kind)?"标签下的笔记":"主题下的笔记",12,Ui.MUTED));Ui.space(root,6);
        TextView heading=Ui.bold(this,("tag".equals(kind)?"# ":"")+(value.isEmpty()?"未分类":value),26,Ui.INK);
        heading.setMaxLines(3);heading.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(heading);Ui.space(root,8);
        root.addView(Ui.text(this,"按最近编辑排序 · 点开查看笔记",13,Ui.MUTED));Ui.space(root,18);

        FrameLayout body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        list=new ListView(this);list.setId(android.R.id.list);list.setSaveEnabled(false);list.setDivider(null);list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);list.setCacheColorHint(Color.TRANSPARENT);
        list.setClipToPadding(false);list.setPadding(0,0,0,Ui.dp(this,4));
        body.addView(list,new FrameLayout.LayoutParams(-1,-1));
        TextView empty=Ui.text(this,"这里暂时没有笔记。\n笔记的标签或主题变更后，列表会自动更新。",15,Ui.MUTED);
        empty.setGravity(android.view.Gravity.CENTER);empty.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0);
        body.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);
        adapter=new Cards();list.setAdapter(adapter);
        list.setOnItemClickListener((parent,view,position,id)->{
            if(position<notes.size())startActivity(new Intent(this,AiDetailActivity.class).putExtra("noteId",notes.get(position).id));
        });
    }

    @Override protected void onStart(){super.onStart();if(adapter!=null){repo.observe(this);refresh();}}
    @Override protected void onStop(){if(repo!=null)repo.removeObserver(this);super.onStop();}
    @Override public void onNotesChanged(){refresh();}
    @Override protected void onSaveInstanceState(Bundle state){
        if(list!=null){rememberPosition();state.putString("noteAnchor",restoreId);state.putInt("noteTop",restoreTop);}
        super.onSaveInstanceState(state);
    }
    private void rememberPosition(){
        int first=list.getFirstVisiblePosition();View child=list.getChildAt(0);
        if(child!=null&&first>=0&&first<notes.size()){restoreId=notes.get(first).id;restoreTop=child.getTop();}
    }
    private boolean matches(Note note){
        if("folder".equals(kind))return value.equals(note.folder);
        for(String tag:note.tags.split("、"))if(value.equals(tag.trim()))return true;
        return false;
    }
    private void refresh(){
        if(adapter==null)return;
        rememberPosition();notes.clear();for(Note note:repo.all())if(matches(note))notes.add(note);
        count.setText(notes.size()+" 条");adapter.notifyDataSetChanged();
        if(!restoreId.isEmpty()){
            int at=-1;for(int i=0;i<notes.size();i++)if(restoreId.equals(notes.get(i).id)){at=i;break;}
            if(at>=0)list.setSelectionFromTop(at,restoreTop);
            else {restoreId="";restoreTop=0;list.setSelection(0);}
        }
    }

    private final class Cards extends BaseAdapter {
        @Override public int getCount(){return notes.size();}
        @Override public Note getItem(int position){return notes.get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            Row row;
            if(recycled==null){row=new Row();recycled=row.outer;recycled.setTag(row);}
            else row=(Row)recycled.getTag();
            Note note=getItem(position);String plain=NoteBodyParser.text(note.content).trim();
            String title=note.title.trim();
            if(title.isEmpty()){
                int newline=plain.indexOf('\n');title=newline>=0?plain.substring(0,newline):plain;
                if(newline>=0)plain=plain.substring(newline+1).trim();
            }
            if(title.isEmpty())title=NoteBodyParser.ids(note.content).isEmpty()?"新便签":"图片笔记";
            row.title.setText(NoteBodyParser.bounded(title,240));
            String preview=NoteBodyParser.bounded(plain.replaceAll("\\s+"," "),240);
            row.preview.setText(preview);row.preview.setVisibility(preview.isEmpty()?View.GONE:View.VISIBLE);
            List<String> tags=new ArrayList<>();for(String tag:note.tags.split("、"))if(!tag.trim().isEmpty())tags.add("# "+tag.trim());
            String labels=NoteBodyParser.bounded(String.join("   ",tags),240);
            row.tags.setText(labels);row.tags.setVisibility(labels.isEmpty()?View.GONE:View.VISIBLE);
            int images=NoteBodyParser.ids(note.content).size();
            String meta=(note.folder.isEmpty()?"未分类":NoteBodyParser.bounded(note.folder,60))+" · "+date.format(new Date(note.updatedAt));
            if(images>0)meta+=" · "+images+" 张图片";
            row.meta.setText(meta);
            row.outer.setContentDescription(row.title.getText()+(preview.isEmpty()?"":"，"+preview)+(labels.isEmpty()?"":"，"+labels)+"，"+meta+"，点开查看笔记");
            return recycled;
        }
    }

    private final class Row {
        final FrameLayout outer=new FrameLayout(NoteListActivity.this);
        final TextView title,preview,tags,meta;
        Row(){
            outer.setLayoutParams(new AbsListView.LayoutParams(-1,-2));outer.setPadding(0,0,0,Ui.dp(NoteListActivity.this,12));
            outer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            LinearLayout card=Ui.column(NoteListActivity.this);int p=Ui.dp(NoteListActivity.this,16);card.setPadding(p,p,p,p);
            card.setDuplicateParentStateEnabled(true);card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            StateListDrawable background=new StateListDrawable();
            background.addState(new int[]{android.R.attr.state_pressed},surface(0xFFEEF5EF));
            background.addState(new int[]{},surface(Color.WHITE));card.setBackground(background);
            outer.addView(card,new FrameLayout.LayoutParams(-1,-2));
            title=Ui.bold(NoteListActivity.this,"",17,Ui.INK);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);card.addView(title);
            preview=Ui.text(NoteListActivity.this,"",14,Ui.MUTED);preview.setMaxLines(3);preview.setEllipsize(TextUtils.TruncateAt.END);preview.setLineSpacing(Ui.dp(NoteListActivity.this,3),1);
            LinearLayout.LayoutParams content=new LinearLayout.LayoutParams(-1,-2);content.topMargin=Ui.dp(NoteListActivity.this,8);card.addView(preview,content);
            tags=Ui.text(NoteListActivity.this,"",12,Ui.GREEN);tags.setMaxLines(2);tags.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams label=new LinearLayout.LayoutParams(-1,-2);label.topMargin=Ui.dp(NoteListActivity.this,12);card.addView(tags,label);
            meta=Ui.text(NoteListActivity.this,"",11,Ui.MUTED);meta.setLineSpacing(Ui.dp(NoteListActivity.this,2),1);
            LinearLayout.LayoutParams footer=new LinearLayout.LayoutParams(-1,-2);footer.topMargin=Ui.dp(NoteListActivity.this,12);card.addView(meta,footer);
        }
        private GradientDrawable surface(int color){GradientDrawable d=Ui.round(NoteListActivity.this,color,16);d.setStroke(Ui.dp(NoteListActivity.this,1),0xFFDEE6DE);return d;}
    }
}

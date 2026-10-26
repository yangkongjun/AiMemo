package io.github.floatmemo.android;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.util.*;

public final class BrowseActivity extends BaseActivity implements NoteRepository.Listener {
    private NoteRepository repo;private LinearLayout root;private boolean review;
    @Override public void onCreate(Bundle s){super.onCreate(s);repo=NoteRepository.get(this);review=getIntent().getBooleanExtra("review",false);LinearLayout shell=Ui.column(this);Ui.insetActivity(this,shell,16);ScrollView sc=new ScrollView(this);shell.addView(sc);root=Ui.column(this);sc.addView(root);render();}
    @Override protected void onStart(){super.onStart();repo.observe(this);}@Override protected void onStop(){repo.removeObserver(this);super.onStop();}@Override public void onNotesChanged(){render();}
    private void open(Note n){startActivity(new Intent(this,AiDetailActivity.class).putExtra("noteId",n.id));}
    private void render(){root.removeAllViews();root.addView(Ui.button(this,"‹ 返回",false,v->finish()));Ui.space(root,18);root.addView(Ui.bold(this,review?"需要你看一眼":"主题与标签",27,Ui.INK));Ui.space(root,12);List<Note> notes=repo.all();
        if(review){int count=0;for(Note n:notes){if(n.reviewed&&!n.aiStatus.equals("整理失败"))continue;if(n.aiJson.isEmpty()&&!n.aiStatus.equals("整理失败"))continue;count++;
            Ui.space(root,10);root.addView(Ui.button(this,n.displayTitle()+" · "+n.aiStatus,false,v->open(n)));if(!n.aiError.isEmpty())root.addView(Ui.text(this,n.aiError,14,Ui.MUTED));}
            if(count==0)root.addView(Ui.text(this,"暂时没有需要确认的整理。你可以继续记录。",16,Ui.MUTED));return;}
        root.addView(Ui.text(this,"按主题或标签浏览积累。主题沿用原有文件夹，可在每条笔记中修改。",14,Ui.MUTED));Ui.space(root,12);root.addView(Ui.bold(this,"主题",19,Ui.INK));
        LinkedHashMap<String,List<Note>> folders=new LinkedHashMap<>(),tags=new LinkedHashMap<>();for(Note n:notes){folders.computeIfAbsent(n.folder.isEmpty()?"未分类":n.folder,k->new ArrayList<>()).add(n);
            for(String t:n.tags.split("、"))if(!t.trim().isEmpty())tags.computeIfAbsent(t.trim(),k->new ArrayList<>()).add(n);}
        for(Map.Entry<String,List<Note>> e:folders.entrySet()){Ui.space(root,8);root.addView(Ui.button(this,e.getKey()+"  ·  "+e.getValue().size()+" 条",false,v->show(e.getKey(),e.getValue())));}
        if(folders.isEmpty())root.addView(Ui.text(this,"记下第一条内容后，这里会出现主题。",15,Ui.MUTED));Ui.space(root,18);root.addView(Ui.bold(this,"标签",19,Ui.INK));
        for(Map.Entry<String,List<Note>> e:tags.entrySet()){Ui.space(root,8);root.addView(Ui.button(this,"# "+e.getKey()+"  ·  "+e.getValue().size()+" 条",false,v->show(e.getKey(),e.getValue())));}
        if(tags.isEmpty())root.addView(Ui.text(this,"AI 整理后或手动设置的标签会出现在这里。",15,Ui.MUTED));
    }
    private void show(String name,List<Note> list){String[] items=new String[list.size()];for(int i=0;i<list.size();i++)items[i]=list.get(i).displayTitle();new android.app.AlertDialog.Builder(this).setTitle(name).setItems(items,(d,w)->open(list.get(w))).setNegativeButton("关闭",null).show();}
}

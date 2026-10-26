package io.github.floatmemo.android;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.*;
import java.util.*;

public final class AiDetailActivity extends BaseActivity implements NoteRepository.Listener {
    private String id;private NoteRepository repo;private LinearLayout root;private ScrollView scroll;
    private void toast(String t){Toast.makeText(this,t,Toast.LENGTH_LONG).show();}
    @Override public void onCreate(Bundle s){super.onCreate(s);id=getIntent().getStringExtra("noteId");repo=NoteRepository.get(this);
        LinearLayout shell=Ui.column(this);Ui.insetActivity(this,shell,16);scroll=new ScrollView(this);shell.addView(scroll);root=Ui.column(this);scroll.addView(root);render();}
    @Override protected void onStart(){super.onStart();repo.observe(this);}
    @Override protected void onStop(){repo.removeObserver(this);super.onStop();}
    @Override public void onNotesChanged(){int y=scroll.getScrollY();render();scroll.post(()->scroll.scrollTo(0,y));}
    @Override protected void onResume(){super.onResume();if(root!=null)render();}
    private void render(){Note n=repo.find(id);if(n==null){finish();return;}root.removeAllViews();
        LinearLayout bar=Ui.row(this);bar.addView(Ui.button(this,"‹ 返回",false,v->finish()));TextView gap=Ui.text(this,"",12,Ui.MUTED);bar.addView(gap,new LinearLayout.LayoutParams(0,1,1));bar.addView(Ui.button(this,"编辑原文",false,v->edit("")));bar.addView(Ui.button(this,"悬浮",true,v->requestOverlay(OverlayService.SHOW,id)));root.addView(bar);Ui.space(root,16);
        root.addView(Ui.bold(this,n.displayTitle(),25,Ui.INK));Ui.space(root,6);
        String status=n.aiStatus+(n.aiRevision>0&&n.aiRevision!=n.revision?" · 原文已改变，以下是旧版本整理":"");root.addView(Ui.text(this,status,14,Ui.GREEN));if(!repo.error.isEmpty())root.addView(Ui.text(this,repo.error,14,Ui.MUTED));
        if(!n.aiError.isEmpty()){root.addView(Ui.text(this,n.aiError,14,Ui.MUTED));root.addView(Ui.button(this,"查看诊断记录",false,v->startActivity(new Intent(this,DiagnosticActivity.class))));}Ui.space(root,12);
        LinearLayout actions=Ui.row(this);actions.addView(Ui.button(this,n.aiJson.isEmpty()?"AI 整理":"重新整理",true,v->analyze(n)));
        actions.addView(Ui.button(this,n.reviewed?"已确认":"确认整理",false,v->{repo.update(id,x->{x.reviewed=true;x.aiStatus="已确认";});}));
        actions.addView(Ui.button(this,"撤销纠正",false,v->{if(!repo.undoEdit(id))toast("没有可撤销的内容纠正");}));root.addView(actions);Ui.space(root,18);
        root.addView(Ui.bold(this,"主题与标签",18,Ui.INK));root.addView(Ui.text(this,"主题："+(n.folder.isEmpty()?"未分类":n.folder)+(n.folderLocked?" · 人工指定":"")+"\n标签："+(n.tags.isEmpty()?"暂无":n.tags)+(n.tagsLocked?" · 人工指定":""),15,Ui.INK));
        if(!n.topicSuggestion.isEmpty()&&!n.topicSuggestion.equals(n.folder))root.addView(Ui.text(this,"AI 建议主题："+n.topicSuggestion,14,Ui.MUTED));
        LinearLayout edits=Ui.row(this);edits.addView(Ui.button(this,"修改主题",false,v->folder(n)));edits.addView(Ui.button(this,"修改标签",false,v->tags(n)));root.addView(edits);Ui.space(root,18);
        if(n.aiJson.isEmpty()){root.addView(Ui.text(this,"原文已保存在本机。配置并启用 AI 后，可以查看整理出的要点及原文依据。",15,Ui.MUTED));}
        else try{JSONObject j=new JSONObject(n.aiJson);String proposed=j.optString("title","");if(!proposed.isEmpty()){root.addView(Ui.text(this,"建议标题："+proposed,14,Ui.MUTED));Ui.space(root,10);}
            if(j.optInt("parts",1)>1)root.addView(Ui.text(this,"长笔记按 "+j.optInt("parts")+" 段分别整理，概括按段展示。",13,Ui.MUTED));
            for(String[] section:new String[][]{{"summaries","概括"},{"points","要点"},{"examples","原文中的例子"},{"advice","原文已有的建议"}}){JSONArray a=j.optJSONArray(section[0]);if(a==null||a.length()==0)continue;root.addView(Ui.bold(this,section[1],18,Ui.INK));
                for(int i=0;i<a.length();i++){JSONObject item=a.getJSONObject(i);String text=item.getString("text"),quote=item.getString("quote");final int index=i;String key=section[0];
                    LinearLayout card=Ui.column(this);int pad=Ui.dp(this,12);card.setPadding(pad,pad,pad,pad);card.setBackground(Ui.round(this,0xFFFFFFFF,12));
                    TextView value=Ui.text(this,text+(item.optBoolean("manual")?"\n〔人工修正〕":""),16,Ui.INK);value.setTextIsSelectable(true);card.addView(value);
                    LinearLayout tools=Ui.row(this);tools.addView(Ui.button(this,"原文依据",false,v->source(quote,n.aiRevision==n.revision)));tools.addView(Ui.button(this,"纠正这条",false,v->correct(n,key,index,item)));card.addView(tools);root.addView(card);Ui.space(root,8);
                }Ui.space(root,12);
            }
            JSONArray q=j.optJSONArray("questions");if(q!=null&&q.length()>0){root.addView(Ui.bold(this,"可以补充的背景",18,Ui.INK));for(int i=0;i<q.length();i++)root.addView(Ui.text(this,"• "+q.getString(i),15,Ui.MUTED));root.addView(Ui.button(this,"回原文补充",false,v->edit("")));}
        }catch(Exception e){root.addView(Ui.text(this,"整理内容读取失败，请重试；原文仍保留。",15,Ui.MUTED));}
        Ui.space(root,16);root.addView(Ui.bold(this,"可能相关的笔记",18,Ui.INK));root.addView(Ui.text(this,"按标签与措辞在本机匹配，尚未证明存在逻辑或因果关系。",12,Ui.MUTED));
        Set<String> dismissed=new HashSet<>(Arrays.asList(n.dismissedRelations.split(",")));int shown=0;
        for(SearchEngine.Hit hit:SearchEngine.related(n,repo.all())){if(dismissed.contains(hit.note.id))continue;shown++;Ui.space(root,8);root.addView(Ui.button(this,hit.note.displayTitle(),false,v->startActivity(new Intent(this,AiDetailActivity.class).putExtra("noteId",hit.note.id))));root.addView(Ui.text(this,hit.reason,13,Ui.MUTED));root.addView(Ui.button(this,"这条不相关",false,v->{Set<String> copy=new HashSet<>(Arrays.asList(n.dismissedRelations.split(",")));copy.remove("");copy.add(hit.note.id);repo.update(id,x->x.dismissedRelations=String.join(",",copy));}));}
        if(shown==0)root.addView(Ui.text(this,"暂未找到相关笔记。记录与整理更多内容后可再查看。",14,Ui.MUTED));Ui.space(root,20);
        root.addView(Ui.button(this,"查看完整原文",false,v->edit("")));
    }
    private void analyze(Note n){if(!AiConfig.get(this).enabled()){startActivity(new Intent(this,AiSettingsActivity.class));return;}
        if(n.aiLocked)new AlertDialog.Builder(this).setTitle("重新整理人工修正的内容？").setMessage("当前修正将保留在纠正历史中。新结果会替换当前要点，人工主题和标签继续保留。")
            .setNegativeButton("取消",null).setPositiveButton("重新整理",(d,w)->{repo.queue(id,true);toast("已加入后台整理，网络可用时处理");}).show();
        else {repo.queue(id,false);toast("已加入后台整理，网络可用时处理");}}
    private void edit(String quote){startActivity(new Intent(this,NoteEditorActivity.class).putExtra("noteId",id).putExtra("sourceQuote",quote));}
    private void source(String quote,boolean current){new AlertDialog.Builder(this).setTitle(current?"原文依据":"整理时的原文依据 旧版本").setMessage(quote).setNegativeButton("关闭",null).setPositiveButton(current?"定位原文":"查看当前原文",(d,w)->edit(current?quote:"")).show();}
    private void correct(Note n,String key,int index,JSONObject item){if(n.aiRevision!=n.revision){toast("原文已更新，请先重新整理再纠正");return;}EditText text=Ui.input(this,"按你的意思修改这条要点");text.setText(item.optString("text"));text.setMinLines(3);
        new AlertDialog.Builder(this).setTitle("纠正这条内容").setView(text).setNegativeButton("取消",null).setNeutralButton("移除这条",(d,w)->{try{JSONObject j=new JSONObject(n.aiJson);j.getJSONArray(key).remove(index);repo.editAi(id,n.revision,j.toString());}catch(Exception e){toast("纠正未保存，原文可能已改变，请刷新后重试");}})
            .setPositiveButton("保存并锁定",(d,w)->{String value=text.getText().toString().trim();if(value.isEmpty()){toast("内容不能为空");return;}try{JSONObject j=new JSONObject(n.aiJson);j.getJSONArray(key).getJSONObject(index).put("text",value).put("manual",true);repo.editAi(id,n.revision,j.toString());}catch(Exception e){toast("纠正未保存，原文可能已改变，请刷新后重试");}}).show();}
    private void folder(Note n){EditText text=Ui.input(this,"主题名称，留空设为未分类");text.setText(n.topicSuggestion.isEmpty()?n.folder:n.topicSuggestion);
        new AlertDialog.Builder(this).setTitle("修改主题").setView(text).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{String value=text.getText().toString().trim();if(value.length()>60){toast("名称最多60字");return;}if(!value.isEmpty())repo.addFolder(value);repo.update(id,x->{x.folder=value;x.folderLocked=true;});}).show();}
    private void tags(Note n){EditText text=Ui.input(this,"用顿号或逗号分隔标签");text.setText(n.tags);
        new AlertDialog.Builder(this).setTitle("修改标签").setView(text).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{LinkedHashSet<String> values=new LinkedHashSet<>();for(String t:text.getText().toString().split("[、,，\\n]")){if(!t.trim().isEmpty())values.add(t.trim());}repo.update(id,x->{x.tags=String.join("、",values);x.tagsLocked=true;});}).show();}
}

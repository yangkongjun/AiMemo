package io.github.floatmemo.android;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends BaseActivity implements NoteRepository.Listener {
    private NoteRepository repo;
    private LinearLayout cards;
    private TextView permissionLabel, countLabel;
    private Spinner folderPicker;
    private String query = "";
    private int folderIndex;
    private boolean updatingPicker;
    private java.util.List<SearchEngine.Hit> aiHits;
    private TextView searchMode;
    private Button aiSearch;
    private int searchGeneration;
    private final java.util.concurrent.ExecutorService searchExecutor=java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final int EXPORT = 41;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); repo = NoteRepository.get(this);
        if (state != null) { query = state.getString("query",""); folderIndex = state.getInt("folder",0); }
        LinearLayout shell = Ui.column(this); Ui.insetActivity(this,shell,0);
        ScrollView page = new ScrollView(this); page.setFillViewport(true);
        shell.addView(page,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout root = Ui.column(this);
        int padding = Ui.dp(this,20); root.setPadding(padding,padding,padding,padding);
        page.addView(root,new ScrollView.LayoutParams(-1,-2));
        LinearLayout brand = Ui.row(this);
        brand.addView(Ui.bold(this,"FLOATMEMO AI",18,Ui.GREEN),new LinearLayout.LayoutParams(0,-2,1));
        Button more = Ui.button(this,"更多",false,v->showMore()); brand.addView(more); root.addView(brand);
        Ui.space(root,20);
        root.addView(Ui.bold(this,"随手记下，\n以后找得到。",32,Ui.INK));
        Ui.space(root,8);
        root.addView(Ui.text(this,"原文留在本机，AI 帮你梳理和联系。",14,Ui.MUTED));
        Ui.space(root,16);
        permissionLabel = Ui.text(this,"",13,Ui.GREEN); root.addView(permissionLabel);
        Ui.space(root,12);
        LinearLayout actions = Ui.row(this);
        actions.addView(Ui.button(this,"＋ 新便签",true,v -> edit(repo.create().id)),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        View gap = new View(this); actions.addView(gap,new LinearLayout.LayoutParams(Ui.dp(this,8),1));
        actions.addView(Ui.button(this,"显示全部",false,v -> {
            if (repo.all().isEmpty()) toast("先创建一张便签吧");
            else requestOverlay(OverlayService.SHOW_ALL,null);
        }),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        root.addView(actions); Ui.space(root,14);
        LinearLayout nav=Ui.row(this);
        nav.addView(Ui.button(this,"主题标签",false,v->startActivity(new Intent(this,BrowseActivity.class))),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        nav.addView(Ui.button(this,"待确认",false,v->startActivity(new Intent(this,BrowseActivity.class).putExtra("review",true))),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        nav.addView(Ui.button(this,"AI 设置",false,v->startActivity(new Intent(this,AiSettingsActivity.class))),new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        root.addView(nav);Ui.space(root,14);
        EditText search = Ui.input(this,"记得几个词？也可以描述当时的情境"); search.setSingleLine(true); search.setText(query);
        search.addTextChangedListener(new Watcher(){public void afterTextChanged(Editable e){query=e.toString();aiHits=null;searchGeneration++;searchMode.setText("本地文字搜索 · 原文、标题、主题与标签");renderCards();}});
        root.addView(search); Ui.space(root,8);
        aiSearch=Ui.button(this,"按意思用 AI 查找",false,v->semanticSearch());root.addView(aiSearch);
        searchMode=Ui.text(this,"本地文字搜索 · 原文、标题、主题与标签",12,Ui.MUTED);root.addView(searchMode);Ui.space(root,8);
        LinearLayout filter = Ui.row(this);
        countLabel = Ui.text(this,"",13,Ui.MUTED);
        filter.addView(countLabel,new LinearLayout.LayoutParams(0,-2,1));
        folderPicker = new Spinner(this); filter.addView(folderPicker);
        folderPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int position,long id){
                if (!updatingPicker) { folderIndex=position; renderCards(); }
            }
            public void onNothingSelected(AdapterView<?> p){}
        });
        root.addView(filter);
        cards = Ui.column(this); root.addView(cards,new LinearLayout.LayoutParams(-1,-2));
        updateFolders(); renderCards();if(repo.hasPending())AiJobService.schedule(this);
    }
    @Override protected void onStart(){super.onStart();repo.observe(this);}
    @Override protected void onStop(){repo.removeObserver(this);repo.flush();super.onStop();}
    @Override protected void onResume(){super.onResume();if(cards!=null){updateStatus();updateFolders();renderCards();}}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("query",query);out.putInt("folder",folderIndex);}
    @Override public void onNotesChanged(){updateStatus();updateFolders();renderCards();}
    private void updateStatus(){
        permissionLabel.setText(!repo.error.isEmpty() ? repo.error : Settings.canDrawOverlays(this)
            ? "● 悬浮权限已开启 · 便签保存在本机" : "○ 首次悬浮时，需要开启显示权限");
    }
    private void updateFolders(){
        updatingPicker=true;
        java.util.ArrayList<String> options=new java.util.ArrayList<>();
        options.add("全部");options.add("未分类");options.addAll(repo.folders());
        folderPicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));
        folderIndex=Math.min(folderIndex,options.size()-1);folderPicker.setSelection(folderIndex);updatingPicker=false;
    }
    private void renderCards(){
        if(cards==null)return;
        cards.removeAllViews();int count=0;
        List<String> folders=repo.folders();
        List<SearchEngine.Hit> hits=aiHits==null?SearchEngine.local(repo.all(),query):aiHits;
        for(SearchEngine.Hit hit:hits){
            Note n=repo.find(hit.note.id);if(n==null)continue;
            if(aiHits!=null&&n.revision!=hit.note.revision)continue;
            if(folderIndex==1&&!n.folder.isEmpty())continue;
            if(folderIndex>=2 && (folderIndex-2>=folders.size()||!n.folder.equals(folders.get(folderIndex-2))))continue;
            count++;
            LinearLayout card=Ui.column(this);int p=Ui.dp(this,16);card.setPadding(p,p,p,p);
            card.setBackground(Ui.round(this,Ui.COLORS[n.color],18));
            card.addView(Ui.bold(this,n.displayTitle(),18,Ui.INK));
            Ui.space(card,8);
            TextView body=Ui.text(this,hit.quote.isEmpty()?"写下第一句话…":hit.quote,14,Ui.INK);body.setMaxLines(3);card.addView(body);
            if(!query.trim().isEmpty())card.addView(Ui.text(this,hit.reason,12,Ui.GREEN));
            if(!query.trim().isEmpty()&&!hit.quote.isEmpty())card.addView(Ui.button(this,"定位匹配段落",false,v->startActivity(new Intent(this,NoteEditorActivity.class).putExtra("noteId",n.id).putExtra("sourceQuote",hit.quote))));
            if(!n.tags.isEmpty())card.addView(Ui.text(this,"# "+n.tags,12,Ui.MUTED));
            card.addView(Ui.text(this,n.aiStatus+(n.aiLocked?" · 人工修正已锁定":""),12,Ui.MUTED));
            Ui.space(card,12);
            LinearLayout bottom=Ui.row(this);
            String date=new SimpleDateFormat("MM/dd HH:mm",Locale.CHINA).format(new Date(n.updatedAt));
            bottom.addView(Ui.text(this,(n.folder.isEmpty()?"未分类":n.folder)+" · "+date,11,Ui.MUTED),new LinearLayout.LayoutParams(0,-2,1));
            bottom.addView(Ui.button(this,"悬浮 ↗",false,v->requestOverlay(OverlayService.SHOW,n.id)));
            card.addView(bottom);card.setOnClickListener(v->startActivity(new Intent(this,AiDetailActivity.class).putExtra("noteId",n.id)));
            card.setOnLongClickListener(v->{confirmDelete(n);return true;});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=Ui.dp(this,12);cards.addView(card,lp);
        }
        countLabel.setText(getString(R.string.memo_count,count));
        if(count==0){
            TextView empty=Ui.text(this,query.isEmpty()?"这里还很安静。\n点“新便签”，记下第一件事。":"没有匹配的便签",17,Ui.MUTED);
            empty.setGravity(android.view.Gravity.CENTER);empty.setPadding(0,Ui.dp(this,50),0,0);cards.addView(empty);
        }
    }
    private void edit(String id){startActivity(new Intent(this,NoteEditorActivity.class).putExtra("noteId",id));}
    private void confirmDelete(Note n){new AlertDialog.Builder(this).setTitle("删除便签？").setMessage(n.displayTitle())
        .setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->repo.delete(n.id)).show();}
    private void showMore(){
        String[] options={"隐藏全部悬浮窗","停止悬浮服务","新建文件夹","删除文件夹","导出全部便签（JSON）","使用说明","导入 JSON 备份","批量整理未完成笔记","诊断记录"};
        new AlertDialog.Builder(this).setTitle("更多操作").setItems(options,(d,w)->{
            if(w==0||w==1){
                if(!OverlayService.running){toast("当前没有悬浮服务");return;}
                startService(new Intent(this,OverlayService.class).setAction(w==0?OverlayService.HIDE_ALL:OverlayService.STOP));
            }else if(w==2){
                EditText input=Ui.input(this,"文件夹名称");
                new AlertDialog.Builder(this).setTitle("新建文件夹").setView(input).setNegativeButton("取消",null)
                    .setPositiveButton("创建",(a,b)->{if(!repo.addFolder(input.getText().toString()))toast("名称为空或已存在");updateFolders();renderCards();}).show();
            }else if(w==3){
                List<String> folders=repo.folders();
                if(folders.isEmpty()){toast("还没有文件夹");return;}
                new AlertDialog.Builder(this).setTitle("删除文件夹，便签会保留").setItems(folders.toArray(new String[0]),(a,b)->{
                    repo.deleteFolder(folders.get(b));folderIndex=0;updateFolders();renderCards();}).show();
            }else if(w==4){
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE,"FloatMemo-backup.json"),EXPORT);
            }else if(w==6){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),43);
            }else if(w==7){batch();
            }else if(w==8){startActivity(new Intent(this,DiagnosticActivity.class));
            }else new AlertDialog.Builder(this).setTitle("让便签随处可见")
                .setMessage("1. 新建便签并输入内容。\n2. 点“悬浮”，按提示授予权限。\n3. 拖动便签标题栏移动，拖右下角调整大小。\n4. 点“编辑”直接输入，点“完成”收起键盘。\n5. “隐藏”保留窗口状态，可从通知恢复；“×”只关闭该悬浮窗，不删除内容。\n\n内容自动保存到本机，没有云同步。卸载或清除应用数据会删除笔记。请在“更多 → 导出全部便签（JSON）”中另存备份；重装后可导入。更新时优先直接覆盖安装。\n\n配置 AI 后，停止输入一段时间会安排后台整理。\n部分手机需在系统设置中允许后台运行。")
                .setPositiveButton("知道了",null).show();
        }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==43&&result==RESULT_OK&&data!=null&&data.getData()!=null){importBackup(data.getData());return;}
        if(request==EXPORT&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new java.io.IOException("No output stream");
                out.write(repo.exportJson().getBytes(StandardCharsets.UTF_8));toast("已导出备份");
            }catch(Exception e){toast("导出失败，请重试");}
        }
    }
    private void semanticSearch(){
        if(query.trim().isEmpty()){toast("先输入想找的内容");return;}
        if(!AiConfig.get(this).enabled()){startActivity(new Intent(this,AiSettingsActivity.class));return;}
        final String q=query;final int generation=++searchGeneration;final long epoch=AiConfig.get(this).epoch();
        java.util.List<Note> candidates=new java.util.ArrayList<>(SearchEngine.candidates(repo.all(),q));
        if(candidates.isEmpty()){toast("先记录一些内容再查找");return;}
        aiSearch.setEnabled(false);searchMode.setText(getString(R.string.ai_search_running,candidates.size()));
        searchExecutor.execute(()->{try{java.util.List<SearchEngine.Hit> found=AiClient.search(this,candidates,q,epoch);
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;aiSearch.setEnabled(true);if(generation!=searchGeneration)return;aiHits=found;
                searchMode.setText(getString(R.string.ai_search_finished,candidates.size(),repo.all().size(),found.isEmpty()?getString(R.string.ai_search_empty):""));renderCards();});
        }catch(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;aiSearch.setEnabled(true);if(generation!=searchGeneration)return;aiHits=null;searchMode.setText(R.string.ai_search_failed);toast(e.getMessage()==null?"搜索失败，请重试":e.getMessage());renderCards();});}});
    }
    private void batch(){if(!AiConfig.get(this).enabled()){startActivity(new Intent(this,AiSettingsActivity.class));return;}
        java.util.List<Note> todo=new java.util.ArrayList<>();for(Note n:repo.all())if(!n.content.trim().isEmpty()&&!n.aiLocked&&(n.aiJson.isEmpty()||n.aiRevision!=n.revision||n.aiStatus.equals("整理失败")))todo.add(n);
        new AlertDialog.Builder(this).setTitle("整理 "+todo.size()+" 条笔记？").setMessage("会将正文、原标题与已有主题名称发送至你配置的 AI 服务。长文会分段，请求计入每日上限；可在 AI 设置暂停。人工锁定的笔记跳过。")
            .setNegativeButton("取消",null).setPositiveButton("加入整理",(d,w)->{for(Note n:todo)repo.queue(n.id,false);toast("已加入后台整理");}).show();}
    private void importBackup(android.net.Uri uri){
        searchExecutor.execute(()->{try{String value;try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            if(in==null)throw new Exception("无法读取文件");byte[] b=new byte[8192];int len;while((len=in.read(b))!=-1){if(out.size()+len>20*1024*1024)throw new Exception("本版备份文件上限20MB");out.write(b,0,len);}value=out.toString("UTF-8");}
            org.json.JSONObject json=new org.json.JSONObject(value);int count=json.getJSONArray("notes").length();
            runOnUiThread(()->{if(isFinishing()||isDestroyed())return;new AlertDialog.Builder(this).setTitle("导入 "+count+" 条记录？").setMessage("相同备份再次导入会跳过；冲突记录保留双方。不会自动外发或打开悬浮窗。建议先导出当前备份。")
                .setNegativeButton("取消",null).setPositiveButton("导入",(d,w)->searchExecutor.execute(()->{try{int imported=repo.importJson(value);runOnUiThread(()->{aiHits=null;updateFolders();renderCards();toast("已导入 "+imported+" 条");});}catch(Exception e){runOnUiThread(()->toast("导入失败："+(e.getMessage()==null?"请检查备份":e.getMessage())));}})).show();});
        }catch(Exception e){runOnUiThread(()->toast("读取失败："+(e.getMessage()==null?"请检查备份":e.getMessage())));}});
    }
    @Override protected void onDestroy(){searchGeneration++;searchExecutor.shutdown();super.onDestroy();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    public abstract static class Watcher implements TextWatcher{
        public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        public void onTextChanged(CharSequence s,int start,int before,int count){}
    }
}

package io.github.floatmemo.android;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class NoteEditorActivity extends BaseActivity implements NoteRepository.Listener {
    private NoteRepository repo;
    private String id;
    private EditText title, body;
    private TextView folderLabel, opacityLabel;
    private NotePreviewRenderer preview;
    private int insertAt;private long insertRevision;private boolean importingImage;
    private static final int PICK_IMAGES=44;
    private ScrollView previewScroll;
    private boolean binding, previewing;
    private String exportType = "md";
    private LinearLayout root;
    private LinearLayout appearancePanel;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);repo=NoteRepository.get(this);id=getIntent().getStringExtra("noteId");
        if(repo.find(id)==null){finish();return;}
        if(state!=null){exportType=state.getString("exportType","md");previewing=state.getBoolean("preview",false);insertAt=state.getInt("insertAt",0);insertRevision=state.getLong("insertRevision",0);}
        root=Ui.column(this);Ui.insetActivity(this,root,16);
        LinearLayout bar=Ui.row(this);
        bar.addView(Ui.button(this,"‹ 返回",false,v->finish()));
        TextView heading=Ui.bold(this,"编辑便签",17,Ui.INK);heading.setGravity(Gravity.CENTER);
        bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(Ui.button(this,"悬浮 ↗",true,v->{repo.flush();requestOverlay(OverlayService.SHOW,id);}));
        root.addView(bar);Ui.space(root,12);
        title=Ui.input(this,"便签标题（可留空）");title.setSingleLine(true);title.setTextSize(21);root.addView(title);
        Ui.space(root,8);
        LinearLayout toolbar=Ui.row(this);
        toolbar.addView(Ui.button(this,"编辑 / 预览",false,v->{previewing=!previewing;updateMode();}));
        toolbar.addView(Ui.button(this,"外观",false,v->appearancePanel.setVisibility(appearancePanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE)));
        toolbar.addView(Ui.button(this,"插图",false,v->pickImages()));
        TextView spacer=Ui.text(this,"",12,Ui.MUTED);toolbar.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        toolbar.addView(Ui.button(this,"更多",false,v->more()));HorizontalScrollView toolbarScroll=new HorizontalScrollView(this);toolbarScroll.setHorizontalScrollBarEnabled(false);toolbarScroll.addView(toolbar);root.addView(toolbarScroll);Ui.space(root,10);
        body=Ui.input(this,"写点什么…\n\n支持 Markdown 标题、列表、粗体和代码块。");
        body.setGravity(Gravity.TOP|Gravity.START);
        body.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setHorizontallyScrolling(false);
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        previewScroll=new ScrollView(this);preview=new NotePreviewRenderer(this);
        preview.setPadding(Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16),Ui.dp(this,16));
        previewScroll.addView(preview);root.addView(previewScroll,new LinearLayout.LayoutParams(-1,0,1));
        Ui.space(root,10);
        appearancePanel=Ui.column(this);appearancePanel.setVisibility(View.GONE);
        HorizontalScrollView paletteScroll=new HorizontalScrollView(this);paletteScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout palette=Ui.row(this);
        for(int i=0;i<Ui.COLORS.length;i++){
            final int color=i;Button b=Ui.button(this,"●",false,v->{repo.update(id,n->n.color=color);applyAppearance();});
            b.setTextColor(Ui.INK);b.setBackground(Ui.round(this,Ui.COLORS[i],24));b.setContentDescription(Ui.COLOR_NAMES[i]);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,48),Ui.dp(this,48));lp.rightMargin=Ui.dp(this,6);palette.addView(b,lp);
        }
        paletteScroll.addView(palette);appearancePanel.addView(paletteScroll);Ui.space(appearancePanel,6);
        LinearLayout preferences=Ui.row(this);
        preferences.addView(Ui.button(this,"A−",false,v->changeFont(-1)));
        preferences.addView(Ui.button(this,"A＋",false,v->changeFont(1)));
        folderLabel=Ui.text(this,"",13,Ui.GREEN);folderLabel.setGravity(Gravity.CENTER);
        folderLabel.setMinHeight(Ui.dp(this,48));folderLabel.setOnClickListener(v->chooseFolder());
        folderLabel.setContentDescription("选择文件夹");preferences.addView(folderLabel,new LinearLayout.LayoutParams(0,-2,1));appearancePanel.addView(preferences);
        LinearLayout opacityRow=Ui.row(this);opacityLabel=Ui.text(this,"",12,Ui.MUTED);opacityRow.addView(opacityLabel);
        SeekBar opacity=new SeekBar(this);opacity.setMax(65);opacity.setProgress(Math.round(repo.find(id).opacity*100)-35);
        opacity.setContentDescription("悬浮便签透明度");
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean user){if(user){repo.update(id,n->n.opacity=(p+35)/100f);applyAppearance();}}
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){repo.flush();}
        });opacityRow.addView(opacity,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));appearancePanel.addView(opacityRow);root.addView(appearancePanel);
        title.addTextChangedListener(new MainActivity.Watcher(){public void afterTextChanged(Editable e){if(!binding)repo.update(id,n->n.title=e.toString());}});
        body.addTextChangedListener(new MainActivity.Watcher(){public void afterTextChanged(Editable e){if(!binding)repo.update(id,n->n.content=e.toString());}});
        load();
        String quote=getIntent().getStringExtra("sourceQuote");
        if(quote!=null&&!quote.isEmpty()){int at=getIntent().getIntExtra("sourceStart",-1);String raw=body.getText().toString();if(at<0||!raw.startsWith(quote,at))at=NoteBodyParser.locate(raw,quote);if(at>=0){previewing=false;updateMode();body.requestFocus();body.setSelection(at,at+quote.length());}}
    }
    @Override protected void onStart(){super.onStart();repo.observe(this);}
    @Override protected void onStop(){repo.removeObserver(this);super.onStop();}
    @Override public void onNotesChanged(){if(!isFinishing())load();}
    @Override protected void onResume(){super.onResume();if(body!=null)load();}
    @Override protected void onPause(){repo.flush();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("exportType",exportType);out.putBoolean("preview",previewing);out.putInt("insertAt",insertAt);out.putLong("insertRevision",insertRevision);}
    private void load(){
        Note n=repo.find(id);if(n==null){finish();return;}
        binding=true;
        if(!title.getText().toString().equals(n.title))title.setText(n.title);
        if(!body.getText().toString().equals(n.content)){int cursor=body.getSelectionStart();body.setText(n.content);body.setSelection(Math.max(0,Math.min(cursor,n.content.length())));}
        binding=false;applyAppearance();updateMode();
    }
    private void applyAppearance(){
        Note n=repo.find(id);if(n==null)return;
        body.setTextSize(n.fontSize);
        body.setBackground(Ui.round(this,Ui.COLORS[n.color],16));previewScroll.setBackground(Ui.round(this,Ui.COLORS[n.color],16));
        folderLabel.setText(getString(R.string.folder_name,n.folder.isEmpty()?"未分类":n.folder));
        opacityLabel.setText(getString(R.string.overlay_opacity,Math.round(n.opacity*100)));
    }
    private void updateMode(){
        body.setVisibility(previewing?View.GONE:View.VISIBLE);previewScroll.setVisibility(previewing?View.VISIBLE:View.GONE);
        if(previewing){
            Note note=repo.find(id);if(note!=null)preview.render(note);
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(body.getWindowToken(),0);
        }
    }
    private void changeFont(int change){repo.update(id,n->n.fontSize=Math.max(12,Math.min(26,n.fontSize+change)));applyAppearance();}
    private void chooseFolder(){
        List<String> folders=new ArrayList<>();folders.add("未分类");folders.addAll(repo.folders());
        new AlertDialog.Builder(this).setTitle("放入文件夹").setItems(folders.toArray(new String[0]),(d,w)->{
            repo.update(id,n->n.folder=w==0?"":folders.get(w));applyAppearance();}).show();
    }
    private void more(){
        String[] labels={"导出 Markdown","导出纯文本","分享文字","删除便签","AI 整理与关联"};
        new AlertDialog.Builder(this).setItems(labels,(d,w)->{
            Note n=repo.find(id);if(n==null)return;
            if(w==4){startActivity(new Intent(this,AiDetailActivity.class).putExtra("noteId",id));
            }else if(w<2){
                exportType=w==0?"md":"txt";
                String filename=n.displayTitle().replaceAll("[\\\\/:*?\"<>|]","_")+"."+exportType;
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(w==0?"text/markdown":"text/plain").putExtra(Intent.EXTRA_TITLE,filename),42);
            }else if(w==2){
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT,n.title.isEmpty()?n.content:n.title+"\n\n"+n.content),"分享便签"));
            }else new AlertDialog.Builder(this).setTitle("删除这张便签？").setMessage("删除后无法在应用中恢复。")
                .setNegativeButton("取消",null).setPositiveButton("删除",(a,b)->{repo.delete(id);finish();}).show();
        }).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_IMAGES&&result==RESULT_OK&&data!=null){receiveImages(data);return;}
        if(request==42&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Note n=repo.find(id);if(n==null)return;
            String content=exportType.equals("md")&&!n.title.isEmpty()?"# "+n.title+"\n\n"+n.content:n.content;
            try(OutputStream output=getContentResolver().openOutputStream(data.getData())){
                if(output==null)throw new java.io.IOException("No output stream");
                output.write(content.getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"已导出文字；图片请用首页的完整ZIP备份保存",Toast.LENGTH_LONG).show();
            }catch(Exception e){Toast.makeText(this,"导出失败，请重试",Toast.LENGTH_LONG).show();}
        }
    }
    private void pickImages(){
        if(importingImage){Toast.makeText(this,"图片正在保存，请稍候",Toast.LENGTH_SHORT).show();return;}
        Note n=repo.find(id);if(n==null)return;insertAt=Math.max(0,body.getSelectionStart());insertRevision=n.revision;
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","image/webp"}),PICK_IMAGES);
    }
    private void receiveImages(Intent data){
        List<android.net.Uri> selected=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)selected.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)selected.add(data.getData());
        if(selected.isEmpty())return;Note before=repo.find(id);if(before==null)return;
        if(selected.size()+NoteBodyParser.ids(before.content).size()>20){Toast.makeText(this,"每篇最多20张图片，请减少选择",Toast.LENGTH_LONG).show();return;}
        final int at=insertAt;final long rev=insertRevision;importingImage=true;Toast.makeText(this,"正在复制图片到本机…",Toast.LENGTH_SHORT).show();
        android.content.Context app=getApplicationContext();NotePreviewRenderer.IMAGES.execute(()->{
            AttachmentStore storage=new AttachmentStore(app);List<Attachment> copied=new ArrayList<>();boolean saved=false;
            try{long bytes=0;for(Attachment a:NoteBodyParser.referenced(before))bytes+=a.bytes;for(android.net.Uri uri:selected){Attachment a=storage.copy(uri);copied.add(a);bytes+=a.bytes;if(bytes>Attachment.MAX_NOTE_BYTES)throw new Exception("每篇原图合计最多100MiB");}
                repo.insertAttachments(id,rev,at,copied);saved=true;runOnUiThread(()->{importingImage=false;if(!isDestroyed()&&!isFinishing()){load();previewing=true;updateMode();Toast.makeText(this,"已保存原图副本。图片不会上传给AI",Toast.LENGTH_LONG).show();}});
            }catch(Exception e){runOnUiThread(()->{importingImage=false;if(!isDestroyed())Toast.makeText(this,"插图未完成："+(e.getMessage()==null?"请重试":e.getMessage()),Toast.LENGTH_LONG).show();});}
            finally{if(!saved)for(Attachment a:copied)storage.file(a.id).delete();}
        });
    }
}

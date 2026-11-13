package io.github.floatmemo.android;

import android.content.*;
import android.graphics.Bitmap;
import android.util.LruCache;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;

/** Local text/image blocks. Small sampled previews, bounded shared bitmap cache. */
public final class NotePreviewRenderer extends LinearLayout {
    static final ExecutorService IMAGES=Executors.newFixedThreadPool(2);
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(8*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}};
    private String bound="";
    public NotePreviewRenderer(Context c){super(c);setOrientation(VERTICAL);int p=Ui.dp(c,12);setPadding(p,p,p,p);}
    public void render(Note note){
        AttachmentStore store=new AttachmentStore(getContext());StringBuilder files=new StringBuilder();for(Attachment a:note.attachments)files.append(a.sha256).append(store.file(a.id).isFile());
        String key=note.id+":"+note.revision+":"+NoteBodyParser.hash(note.content)+":"+presentationKey(note)+":"+note.fontSize+":"+files;if(bound.equals(key))return;bound=key;removeAllViews();
        Map<String,Attachment> assets=new HashMap<>();for(Attachment a:note.attachments)assets.put(a.id,a);
        int imageCount=0;for(NoteBodyParser.Part p:NoteBodyParser.parts(note.content)){
            if(p.imageId.isEmpty()){textBlocks(note,p);continue;}
            if(++imageCount>20){if(imageCount==21)addView(Ui.text(getContext(),"预览最多20张图片，请移除多余引用后再备份",12,Ui.MUTED));continue;}
            Attachment a=assets.get(p.imageId);TextView label=Ui.text(getContext(),a==null||!store.file(p.imageId).exists()?"图片缺失：请导入包含图片的完整ZIP备份":"点击查看图片",12,Ui.MUTED);addView(label);
            if(a==null||!store.file(a.id).isFile())continue;
            ImageView view=new ImageView(getContext());view.setAdjustViewBounds(true);view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setContentDescription("笔记图片，点击放大");
            addView(view,new LayoutParams(-1,Ui.dp(getContext(),180)));
            view.setOnClickListener(v->{Context c=getContext();Intent i=new Intent(c,ImageViewerActivity.class).putExtra("noteId",note.id).putExtra("attachmentId",a.id);if(!(c instanceof android.app.Activity))i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);});
            String cacheKey=a.id+":"+a.sha256;Bitmap cached=CACHE.get(cacheKey);if(cached!=null){view.setImageBitmap(cached);continue;}
            WeakReference<ImageView> target=new WeakReference<>(view);WeakReference<TextView> status=new WeakReference<>(label);
            IMAGES.execute(()->{try{Bitmap b=AttachmentStore.sample(store.file(a.id),320);CACHE.put(cacheKey,b);ImageView image=target.get();if(image!=null)image.post(()->image.setImageBitmap(b));}
                catch(Exception|OutOfMemoryError e){TextView t=status.get();if(t!=null)t.post(()->t.setText("图片暂时无法加载，请检查完整备份或稍后重试"));}});
        }
        if(getChildCount()==0)addView(Ui.text(getContext(),"这篇还没有内容",note.fontSize,Ui.MUTED));
    }
    private static String presentationKey(Note n){try{return TextFormat.json(n.formats).toString()+n.checks.toString();}catch(Exception e){return "";}}
    private void textBlocks(Note n,NoteBodyParser.Part part){
        int cursor=0;
        for(TextFormat.Item source:TextFormat.items(n.content)){
            if(source.start<part.start||source.end>part.end)continue;
            TextFormat.Item item=new TextFormat.Item(source.start-part.start,source.end-part.start,source.textStart-part.start,source.checked);
            if(item.start>cursor)addText(n,part.text.substring(cursor,item.start),part.start+cursor);
            int absolute=part.start+item.start;boolean checked=n.checks.containsKey(absolute)?n.checks.get(absolute):item.checked;
            LinearLayout row=Ui.row(getContext());CheckBox box=new CheckBox(getContext());box.setChecked(checked);box.setContentDescription(checked?"取消勾选":"勾选完成");
            TextView text=Ui.text(getContext(),"",n.fontSize,Ui.INK);text.setText(Markdown.render(getContext(),part.text.substring(item.textStart,item.end),n,part.start+item.textStart));
            if(checked)text.setPaintFlags(text.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);text.setAlpha(checked?.55f:1f);text.setTextIsSelectable(true);
            row.addView(box);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));addView(row);
            box.setOnCheckedChangeListener((button,value)->{if(!NoteRepository.get(getContext()).checkItem(n.id,n.revision,absolute,value)){box.setOnCheckedChangeListener(null);box.setChecked(checked);Toast.makeText(getContext(),"内容已改变或状态未保存，请刷新后重试",Toast.LENGTH_LONG).show();}});
            cursor=item.end<part.text.length()?item.end+1:item.end;
        }
        if(cursor<part.text.length())addText(n,part.text.substring(cursor),part.start+cursor);
    }
    private void addText(Note n,String raw,int offset){TextView text=Ui.text(getContext(),"",n.fontSize,Ui.INK);text.setText(Markdown.render(getContext(),raw,n,offset));text.setTextIsSelectable(true);addView(text);}

}

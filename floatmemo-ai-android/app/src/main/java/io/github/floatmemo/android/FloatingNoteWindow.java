package io.github.floatmemo.android;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.os.Build;
import android.text.Editable;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

/** One TYPE_APPLICATION_OVERLAY window. Input focus is acquired only in explicit edit mode. */
public final class FloatingNoteWindow {
    private final Context context;
    private final NoteRepository repo;
    private final String id;
    private final WindowManager manager;
    private final WindowManager.LayoutParams params;
    private final OverlayRoot root;
    private final EditText body;
    private final DragTextView title;
    private final TextView preview, editButton;
    private final LinearLayout content;
    private final ScrollView previewScroll;
    private final View resizeHandle;
    private boolean editing, binding, attached, previewing;
    private Note current;

    public FloatingNoteWindow(Context context,NoteRepository repo,Note note){
        this.context=context;this.repo=repo;id=note.id;current=note;
        manager=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        params=new WindowManager.LayoutParams(Ui.dp(context,note.width),Ui.dp(context,note.height),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.START;params.x=Ui.dp(context,note.x);params.y=Ui.dp(context,note.y);
        params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        root=new OverlayRoot(context);root.setOrientation(LinearLayout.VERTICAL);
        root.setElevation(Ui.dp(context,12));root.setClipToOutline(true);
        LinearLayout header=Ui.row(context);
        header.setPadding(Ui.dp(context,10),0,Ui.dp(context,4),0);
        title=new DragTextView(context);title.setText(note.displayTitle());title.setTextSize(14);
        title.setTextColor(Ui.INK);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setContentDescription("拖动这里移动便签");
        header.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(context,48),1));title.setGravity(Gravity.CENTER_VERTICAL);
        title.setOnTouchListener(new DragListener(false));
        title.setOnClickListener(v->{setEditing(false);repo.update(id,n->n.compact=!n.compact);});
        editButton=control("编辑",v->setEditing(!editing));header.addView(editButton);
        header.addView(control("−",v->{setEditing(false);repo.update(id,n->n.compact=!n.compact);}));
        header.addView(control("×",v->{setEditing(false);repo.update(id,n->n.open=false);repo.flush();}));
        root.addView(header,new LinearLayout.LayoutParams(-1,Ui.dp(context,48)));
        View divider=new View(context);divider.setBackgroundColor(0x18233322);root.addView(divider,new LinearLayout.LayoutParams(-1,Ui.dp(context,1)));
        content=Ui.column(context);
        body=Ui.input(context,"写点什么…");body.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        body.setGravity(Gravity.TOP|Gravity.START);body.setSingleLine(false);
        body.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setTextIsSelectable(true);body.setFocusable(false);body.setFocusableInTouchMode(false);
        body.addTextChangedListener(new MainActivity.Watcher(){
            public void afterTextChanged(Editable value){if(!binding&&editing)repo.update(id,n->n.content=value.toString());}
        });
        content.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        previewScroll=new ScrollView(context);preview=Ui.text(context,"",note.fontSize,Ui.INK);
        preview.setPadding(Ui.dp(context,12),Ui.dp(context,10),Ui.dp(context,12),Ui.dp(context,10));
        preview.setTextIsSelectable(true);previewScroll.addView(preview);
        content.addView(previewScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=Ui.row(context);
        TextView previewButton=control("预览 / 原文",v->{setEditing(false);previewing=!previewing;updateContentMode();});
        footer.addView(previewButton);
        footer.addView(Ui.text(context,"自动保存",10,Ui.MUTED),new LinearLayout.LayoutParams(0,-2,1));
        resizeHandle=control("◢",v->{});resizeHandle.setContentDescription("拖动调整便签大小");resizeHandle.setOnTouchListener(new DragListener(true));footer.addView(resizeHandle);
        content.addView(footer,new LinearLayout.LayoutParams(-1,Ui.dp(context,40)));
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        refresh(note);fitScreen();
        manager.addView(root,params);attached=true;
    }
    private TextView control(String label,View.OnClickListener click){
        TextView v=Ui.text(context,label,13,Ui.GREEN);v.setGravity(Gravity.CENTER);v.setMinWidth(Ui.dp(context,40));
        v.setPadding(Ui.dp(context,6),0,Ui.dp(context,6),0);v.setMinHeight(Ui.dp(context,40));
        v.setContentDescription(label.equals("×")?"关闭悬浮便签，保留内容":label.equals("−")?"折叠或展开便签":label);
        v.setOnClickListener(click);return v;
    }
    public void refresh(Note note){
        boolean sizeChanged=current.compact!=note.compact;
        current=note;
        root.setBackground(Ui.round(context,Ui.COLORS[note.color],16));root.setAlpha(note.opacity);
        title.setText(note.displayTitle());body.setTextSize(note.fontSize);preview.setTextSize(note.fontSize);
        if(!body.getText().toString().equals(note.content)){
            binding=true;int cursor=body.getSelectionStart();body.setText(note.content);
            if(editing)body.setSelection(Math.max(0,Math.min(cursor,body.length())));binding=false;
        }
        if(previewing)preview.setText(Markdown.render(note.content));
        content.setVisibility(note.compact?View.GONE:View.VISIBLE);
        editButton.setVisibility(note.compact?View.GONE:View.VISIBLE);
        if(sizeChanged){fitScreen();}
        updateContentMode();
    }
    private void updateContentMode(){
        body.setVisibility(previewing?View.GONE:View.VISIBLE);
        previewScroll.setVisibility(previewing?View.VISIBLE:View.GONE);
        if(previewing)preview.setText(Markdown.render(body.getText().toString()));
    }
    private void setEditing(boolean value){
        if(!attached)return;
        editing=value;
        if(value){
            previewing=false;updateContentMode();
            params.flags&=~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            body.setFocusableInTouchMode(true);body.setFocusable(true);editButton.setText("完成");updateLayout();
            body.requestFocus();
            body.postDelayed(()->{if(attached&&editing)((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(body,InputMethodManager.SHOW_IMPLICIT);},100);
        }else{
            ((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(body.getWindowToken(),0);
            body.clearFocus();body.setFocusable(false);body.setFocusableInTouchMode(false);
            params.flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;editButton.setText("编辑");updateLayout();repo.flush();
        }
    }
    private int[] screenSize(){
        if(Build.VERSION.SDK_INT>=30){
            WindowMetrics metrics=manager.getMaximumWindowMetrics();
            Insets insets=metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            return new int[]{metrics.getBounds().width()-insets.left-insets.right,metrics.getBounds().height()-insets.top-insets.bottom};
        }
        DisplayMetrics metrics=new DisplayMetrics();manager.getDefaultDisplay().getMetrics(metrics);
        return new int[]{metrics.widthPixels,metrics.heightPixels};
    }
    public void fitScreen(){
        int[] bounds=screenSize();
        WindowGeometry fit=WindowGeometry.fit(Ui.toDp(context,params.x),Ui.toDp(context,params.y),Ui.toDp(context,params.width),
            current.height,Ui.toDp(context,bounds[0]),Ui.toDp(context,bounds[1]));
        params.x=Ui.dp(context,fit.x);params.y=Ui.dp(context,fit.y);params.width=Ui.dp(context,fit.width);
        params.height=Ui.dp(context,current.compact?48:fit.height);updateLayout();
    }
    private void updateLayout(){
        if(attached){
            try{manager.updateViewLayout(root,params);}
            catch(IllegalArgumentException|SecurityException e){close();}
        }
    }
    private void persistGeometry(){
        final int x=Ui.toDp(context,params.x),y=Ui.toDp(context,params.y),w=Ui.toDp(context,params.width),h=Ui.toDp(context,params.height);
        repo.update(id,n->{n.x=x;n.y=y;n.width=w;if(!n.compact)n.height=h;});
    }
    public void close(){
        if(!attached)return;
        if(editing)setEditing(false);
        attached=false;
        try{manager.removeViewImmediate(root);}catch(IllegalArgumentException ignored){}
    }
    private final class OverlayRoot extends LinearLayout{
        OverlayRoot(Context c){super(c);}
        @Override public boolean dispatchKeyEventPreIme(KeyEvent event){
            if(event.getKeyCode()==KeyEvent.KEYCODE_BACK&&editing){if(event.getAction()==KeyEvent.ACTION_UP)setEditing(false);return true;}
            return super.dispatchKeyEventPreIme(event);
        }
        @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
            if(event.getAction()==MotionEvent.ACTION_OUTSIDE){if(editing)setEditing(false);return false;}
            return super.dispatchTouchEvent(event);
        }
    }
    private static final class DragTextView extends TextView{
        DragTextView(Context context){super(context);}
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private final class DragListener implements View.OnTouchListener{
        private final boolean resize;private float startX,startY;private int x,y,w,h;
        DragListener(boolean resize){this.resize=resize;}
        @Override public boolean onTouch(View view,MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    startX=event.getRawX();startY=event.getRawY();x=params.x;y=params.y;w=params.width;h=params.height;return true;
                case MotionEvent.ACTION_MOVE:
                    int dx=Math.round(event.getRawX()-startX),dy=Math.round(event.getRawY()-startY);
                    int[] screen=screenSize();
                    int targetX=resize?x:x+dx,targetY=resize?y:y+dy;
                    int targetW=resize?Math.min(w+dx,screen[0]-x):w;
                    int targetH=resize?Math.min(h+dy,screen[1]-y):h;
                    WindowGeometry fit=WindowGeometry.fit(Ui.toDp(context,targetX),Ui.toDp(context,targetY),Ui.toDp(context,targetW),
                        Ui.toDp(context,targetH),Ui.toDp(context,screen[0]),Ui.toDp(context,screen[1]));
                    params.x=Ui.dp(context,fit.x);params.y=Ui.dp(context,fit.y);params.width=Ui.dp(context,fit.width);
                    params.height=current.compact?Ui.dp(context,48):Ui.dp(context,fit.height);updateLayout();return true;
                case MotionEvent.ACTION_UP:
                    persistGeometry();repo.flush();
                    int slop=ViewConfiguration.get(context).getScaledTouchSlop();
                    if(Math.abs(event.getRawX()-startX)<slop&&Math.abs(event.getRawY()-startY)<slop)view.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    persistGeometry();repo.flush();return true;
                default:return false;
            }
        }
    }
}

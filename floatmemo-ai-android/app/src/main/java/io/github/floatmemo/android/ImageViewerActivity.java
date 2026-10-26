package io.github.floatmemo.android;

import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;

public final class ImageViewerActivity extends BaseActivity {
    @Override public void onCreate(Bundle state){super.onCreate(state);LinearLayout root=Ui.column(this);Ui.insetActivity(this,root,12);root.addView(Ui.button(this,"‹ 返回",false,v->finish()));
        root.addView(Ui.text(this,"双指缩放，拖动查看；双击恢复。为控制内存，查看图按最长边2048像素采样，原文件仍完整保存。",13,Ui.MUTED));
        TextView status=Ui.text(this,"正在读取本机图片…",14,Ui.MUTED);root.addView(status);ZoomImage image=new ZoomImage(this);root.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        Note n=NoteRepository.get(this).find(getIntent().getStringExtra("noteId"));String id=getIntent().getStringExtra("attachmentId");
        if(n==null||!NoteBodyParser.ids(n.content).contains(id)){status.setText("图片引用已删除");return;}
        boolean known=false;for(Attachment a:n.attachments)if(a.id.equals(id))known=true;if(!known){status.setText("图片信息缺失，请导入完整备份");return;}
        java.io.File file=new AttachmentStore(this).file(id);
        NotePreviewRenderer.IMAGES.execute(()->{try{Bitmap b=AttachmentStore.sample(file,2048);runOnUiThread(()->{if(isFinishing()||isDestroyed()){b.recycle();return;}status.setText("");image.show(b);});}
            catch(Exception|OutOfMemoryError e){runOnUiThread(()->{if(!isDestroyed())status.setText("图片缺失或暂时无法加载，请从完整备份恢复");});}});
    }
    private static final class ZoomImage extends ImageView {
        final Matrix matrix=new Matrix();final ScaleGestureDetector scale;final GestureDetector gestures;Bitmap bitmap;float fit=1,current=1,lastX,lastY;
        ZoomImage(android.content.Context c){super(c);setScaleType(ScaleType.MATRIX);setContentDescription("图片查看，支持双指缩放与拖动");
            scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){float factor=Math.max(fit,Math.min(fit*8,current*d.getScaleFactor()))/current;current*=factor;matrix.postScale(factor,factor,d.getFocusX(),d.getFocusY());setImageMatrix(matrix);return true;}});
            gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){public boolean onDown(MotionEvent e){return true;}public boolean onDoubleTap(MotionEvent e){reset();return true;}});
        }
        void show(Bitmap b){bitmap=b;setImageBitmap(b);post(this::reset);}
        void reset(){if(bitmap==null||getWidth()==0)return;fit=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());current=fit;matrix.setScale(fit,fit);matrix.postTranslate((getWidth()-bitmap.getWidth()*fit)/2,(getHeight()-bitmap.getHeight()*fit)/2);setImageMatrix(matrix);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);reset();}
        @Override public boolean onTouchEvent(MotionEvent e){scale.onTouchEvent(e);gestures.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!scale.isInProgress()&&e.getPointerCount()==1){matrix.postTranslate(e.getX()-lastX,e.getY()-lastY);setImageMatrix(matrix);}lastX=e.getX();lastY=e.getY();if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}

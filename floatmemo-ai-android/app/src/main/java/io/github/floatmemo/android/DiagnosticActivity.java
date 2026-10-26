package io.github.floatmemo.android;

import android.app.AlertDialog;
import android.content.*;
import android.os.Bundle;
import android.graphics.Typeface;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class DiagnosticActivity extends BaseActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView report,status;private Button test;private String exportText;
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        if(saved!=null)exportText=saved.getString("diagnosticExport");
        LinearLayout root=Ui.column(this);Ui.insetActivity(this,root,16);
        root.addView(Ui.button(this,"‹ 返回",false,v->finish()));Ui.space(root,12);
        root.addView(Ui.bold(this,"诊断记录",25,Ui.INK));Ui.space(root,8);
        root.addView(Ui.text(this,"测试使用已保存且启用的 AI 配置，只发送一条测试短消息，不发送笔记。会计入请求次数，服务商可能收费。测试使用普通接收，不代表长回答或流式也一定成功。未保存的设置不会用于测试。",13,Ui.MUTED));Ui.space(root,8);
        test=Ui.button(this,"测试当前已保存的配置",true,v->test());root.addView(test);Ui.space(root,6);
        LinearLayout actions=Ui.row(this);
        actions.addView(Ui.button(this,"刷新",false,v->refresh()));
        actions.addView(Ui.button(this,"复制",false,v->{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("FloatMemo诊断",report.getText()));toast("已复制，请检查内容后再分享");}));
        actions.addView(Ui.button(this,"导出",false,v->{exportText=report.getText().toString();try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"FloatMemo-diagnostics.txt"),61);}catch(Exception e){toast("无法打开文件保存页面，可使用复制");}}));
        actions.addView(Ui.button(this,"清空",false,v->new AlertDialog.Builder(this).setTitle("清空诊断记录？").setMessage("仅删除诊断记录，保留全部笔记和 AI 设置。正在进行的请求结束后仍会产生新记录。").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{DiagnosticStore.clear(this);refresh();}).show()));root.addView(actions);
        status=Ui.text(this,"",13,Ui.GREEN);root.addView(status);Ui.space(root,8);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        report=Ui.text(this,"正在读取…",12,Ui.INK);report.setTypeface(Typeface.MONOSPACE);report.setTextIsSelectable(true);scroll.addView(report);
    }
    @Override protected void onResume(){super.onResume();if(report!=null)refresh();}
    private void refresh(){worker.execute(()->{String text=DiagnosticStore.report(getApplicationContext());runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())report.setText(text);});});}
    private void test(){
        test.setEnabled(false);status.setText("正在测试，请等待；不会自动更换请求地址…");
        worker.execute(()->{String result;try{result=AiClient.testConnection(getApplicationContext());}catch(Exception e){result=e.getMessage()==null?"测试失败，请查看下方记录":e.getMessage();}
            String message=result,text=DiagnosticStore.report(getApplicationContext());runOnUiThread(()->{if(isFinishing()||isDestroyed())return;test.setEnabled(true);status.setText(message);report.setText(text);});});
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("diagnosticExport",exportText);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request!=61||result!=RESULT_OK||data==null||data.getData()==null)return;
        String text=exportText==null?report.getText().toString():exportText;android.net.Uri uri=data.getData();
        worker.execute(()->{String message;try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new Exception();out.write(text.getBytes(StandardCharsets.UTF_8));message="诊断记录已导出，请检查内容后分享";}catch(Exception e){message="诊断导出失败，可使用复制";}
            String value=message;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())toast(value);});});
    }
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}

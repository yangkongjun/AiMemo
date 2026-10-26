package io.github.floatmemo.android;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import android.text.InputType;

public final class AiSettingsActivity extends BaseActivity {
    @Override public void onCreate(Bundle state){super.onCreate(state);AiConfig config=AiConfig.get(this);
        LinearLayout shell=Ui.column(this);Ui.insetActivity(this,shell,16);ScrollView sc=new ScrollView(this);shell.addView(sc);LinearLayout root=Ui.column(this);sc.addView(root);
        root.addView(Ui.button(this,"‹ 返回",false,v->finish()));Ui.space(root,16);root.addView(Ui.bold(this,"让 AI 帮你整理",26,Ui.INK));Ui.space(root,10);
        root.addView(Ui.text(this,"使用支持 Chat Completions 格式的模型服务。启用后，新建或修改的笔记会自动整理；已有笔记可在首页批量整理。",15,Ui.MUTED));Ui.space(root,18);
        root.addView(Ui.bold(this,"服务地址",15,Ui.INK));EditText endpoint=Ui.input(this,"https://api-inference.modelscope.cn");endpoint.setSingleLine(true);endpoint.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);endpoint.setText(config.endpoint());root.addView(endpoint);
        root.addView(Ui.text(this,"直接请求你填写的 HTTPS 地址，不自动添加 /v1 或 /chat/completions，也不删除末尾斜杠。仅忽略输入前后的空白。",13,Ui.MUTED));Ui.space(root,10);
        root.addView(Ui.bold(this,"模型名称",15,Ui.INK));EditText model=Ui.input(this,"填写服务商提供的模型 ID");model.setSingleLine(true);model.setText(config.model());root.addView(model);Ui.space(root,10);
        root.addView(Ui.bold(this,"API Key",15,Ui.INK));EditText key=Ui.input(this,"已有密钥时留空保留；更换地址须重新填写");key.setSingleLine(true);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);root.addView(key);Ui.space(root,10);
        root.addView(Ui.bold(this,"每日请求次数上限",15,Ui.INK));EditText limit=Ui.input(this,"1 至 500");limit.setInputType(InputType.TYPE_CLASS_NUMBER);limit.setText(String.valueOf(config.limit()));root.addView(limit);Ui.space(root,12);
        root.addView(Ui.text(this,"今日已尝试 "+config.used()+" 次。长笔记分段会请求多次，失败请求也计数。实际价格由服务商收取，本版不估算费用。",13,Ui.MUTED));Ui.space(root,16);
        RequestOptions budgets=config.options();
        root.addView(Ui.bold(this,"生成与等待",18,Ui.INK));
        EditText analyze=number(root,"单次整理生成上限（token，512–32768）",budgets.analyzeTokens);
        EditText searchTokens=number(root,"单次搜索生成上限（token，512–16384）",budgets.searchTokens);
        EditText seconds=number(root,"响应读取等待（秒，30–300）",budgets.readSeconds);
        root.addView(Ui.text(this,"上限不是固定消耗，模型还受总上下文和服务额度限制；应用允许的范围不代表服务一定支持。短诊断仍为64 token，不自动加量重试。",13,Ui.MUTED));Ui.space(root,12);
        CheckBox streaming=new CheckBox(this);streaming.setText("流式接收长回答");streaming.setTextColor(Ui.INK);streaming.setChecked(budgets.streaming);root.addView(streaming);
        root.addView(Ui.text(this,"逐段接收整理和搜索回答，完整结束后才保存。可降低长时间无数据时的断连风险，但不能保证绕过服务/网关限制。不兼容时关闭；软件不自动切换后重试。短连接测试仍使用普通接收。",13,Ui.MUTED));Ui.space(root,12);
        root.addView(Ui.bold(this,"搜索预算",18,Ui.INK));
        EditText candidates=number(root,"候选笔记上限（篇，5–30）",budgets.candidates);
        EditText chars=number(root,"正文摘录总预算（字符，2000–30000）",budgets.searchChars);
        root.addView(Ui.text(this,"先在选定范围内本地筛选，优先截取命中附近，每篇最多1200字符。正文预算不等于请求总token；无缓存时最多发一次搜索请求。",13,Ui.MUTED));Ui.space(root,16);
        root.addView(Ui.bold(this,"数据发送范围",17,Ui.INK));root.addView(Ui.text(this,"整理会发送笔记正文、原标题及已有主题名称；AI 查找会发送查询和候选笔记文字片段；图片及其路径不会上传。请使用你信任的服务。服务商可能留存接收到的数据，其政策由你选择的服务决定。原文仍保存在本机，密钥不进入导出备份。",14,Ui.MUTED));Ui.space(root,12);
        CheckBox enabled=new CheckBox(this);enabled.setText(R.string.ai_consent);enabled.setTextColor(Ui.INK);enabled.setChecked(config.enabled());root.addView(enabled);Ui.space(root,12);
        root.addView(Ui.button(this,"保存设置",true,v->{try{int cap=Integer.parseInt(limit.getText().toString());if(cap<1||cap>500)throw new Exception("次数上限须为1至500");config.save(endpoint.getText().toString(),model.getText().toString(),key.getText().toString().trim(),cap,enabled.isChecked(),new RequestOptions(value(analyze),value(searchTokens),value(seconds),value(candidates),value(chars),streaming.isChecked()));key.setText("");
            if(enabled.isChecked())AiJobService.schedule(this);Toast.makeText(this,enabled.isChecked()?"已启用。新记录将自动整理":"已保存，AI 保持关闭",Toast.LENGTH_LONG).show();finish();}catch(Exception e){Toast.makeText(this,e.getMessage()==null?"保存失败，请检查配置":e.getMessage(),Toast.LENGTH_LONG).show();}}));Ui.space(root,10);
        root.addView(Ui.button(this,"诊断记录 / 测试已保存配置",false,v->startActivity(new android.content.Intent(this,DiagnosticActivity.class))));Ui.space(root,10);
        root.addView(Ui.button(this,"暂停 AI",false,v->{config.disable();enabled.setChecked(false);Toast.makeText(this,"不再发送新请求。已发送的请求无法撤回",Toast.LENGTH_LONG).show();}));Ui.space(root,10);
        root.addView(Ui.button(this,"清除 AI 配置和密钥",false,v->new AlertDialog.Builder(this).setTitle("清除 AI 配置？").setMessage("保留笔记和已整理结果，不再发送新请求。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{config.clear();finish();}).show()));
    }
    private EditText number(LinearLayout root,String label,int current){root.addView(Ui.bold(this,label,15,Ui.INK));EditText v=Ui.input(this,label);v.setSingleLine(true);v.setInputType(InputType.TYPE_CLASS_NUMBER);v.setText(String.valueOf(current));root.addView(v);Ui.space(root,10);return v;}
    private int value(EditText v){try{return Integer.parseInt(v.getText().toString().trim());}catch(Exception e){throw new IllegalArgumentException("请填写有效的整数预算");}}
}

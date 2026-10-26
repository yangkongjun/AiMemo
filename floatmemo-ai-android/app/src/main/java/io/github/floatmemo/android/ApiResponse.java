package io.github.floatmemo.android;

import org.json.JSONArray;
import org.json.JSONObject;

/** Parse the transport envelope separately from the AI-generated note JSON. */
public final class ApiResponse {
    private ApiResponse(){}
    public static final class Failure extends Exception {
        public final String kind;
        public Failure(String kind,String message){super(message);this.kind=kind;}
    }
    public static final class Answer {
        public final String text;
        public final boolean truncated;
        private Answer(String text,boolean truncated){this.text=text;this.truncated=truncated;}
    }
    public static String httpHint(int status){
        if(status==401||status==403)return "鉴权或访问权限：检查 API Key、模型权限和服务地址";
        if(status==404)return "接口地址或模型不存在：请核对服务商接口说明";
        if(status==405)return "该地址不接受 POST 请求：请核对接口地址";
        if(status==400||status==422)return "请求参数或模型不兼容：查看服务响应中的说明";
        if(status==429)return "服务限流或额度不足：请查看账户额度或稍后重试";
        if(status>=300&&status<400)return "服务要求重定向；软件未自动跳转，请核对填写的地址";
        if(status>=500)return "服务端或网关异常；请查看服务响应并稍后重试";
        return "服务返回非成功状态；请查看诊断记录";
    }
    public static String content(int status,String body)throws Exception{
        return parse(status,body,false).text;
    }
    public static Answer parse(int status,String body,boolean connectionTest)throws Exception{
        if(status<200||status>=300)throw new Failure("HTTP_"+status,"HTTP "+status+" · "+httpHint(status));
        JSONObject root;
        try{root=new JSONObject(body);}catch(Exception e){throw new Failure("NOT_JSON","服务响应不是 JSON，可能返回了网页或网关提示。请查看诊断记录");}
        if(root.has("error")&&!root.isNull("error"))throw new Failure("SERVICE_ERROR","服务返回错误对象（HTTP "+status+"），请查看诊断记录中的服务响应");
        JSONArray choices=root.optJSONArray("choices");
        if(choices==null)throw new Failure("MISSING_CHOICES","服务响应缺少 choices（HTTP "+status+"），当前返回格式不是预期的模型回答。请查看诊断记录");
        if(choices.length()==0)throw new Failure("EMPTY_CHOICES","服务返回了空 choices，没有模型回答。请查看诊断记录");
        JSONObject choice=choices.optJSONObject(0);
        if(choice==null)throw new Failure("INVALID_CHOICE","choices 中的回答格式无效，请查看诊断记录");
        boolean truncated="length".equals(choice.optString("finish_reason"));
        if(truncated&&!connectionTest)throw new Failure("OUTPUT_TRUNCATED","生成达到本次输出上限，结果未完成。可先缩短笔记再试，并查看诊断记录中的上限与用量");
        JSONObject message=choice.optJSONObject("message");
        Object content=message==null?null:message.opt("content");
        // A short probe only checks inference connectivity; reasoning may consume its budget
        // before visible text is produced. Do not accept this as a complete note result.
        if(connectionTest&&truncated&&message!=null&&(content==null||content==JSONObject.NULL||content instanceof String))
            return new Answer(content instanceof String?(String)content:"",true);
        if(!(content instanceof String)||((String)content).trim().isEmpty())throw new Failure("MISSING_CONTENT","服务未返回非空的 message.content 文本，请查看诊断记录");
        return new Answer((String)content,false);
    }
}

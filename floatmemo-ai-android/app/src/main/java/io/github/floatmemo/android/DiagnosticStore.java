package io.github.floatmemo.android;

import android.content.Context;
import android.os.Build;
import android.net.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public final class DiagnosticStore {
    private static final int MAX_RECORDS=30;
    private DiagnosticStore(){}
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"ai-diagnostics.json"));}
    private static JSONArray read(Context c)throws Exception{
        AtomicFile f=file(c);if(!f.getBaseFile().exists())return new JSONArray();
        return new JSONArray(new String(f.readFully(),StandardCharsets.UTF_8));
    }
    public static synchronized boolean append(Context c,DiagnosticRecord record){
        AtomicFile f=file(c);FileOutputStream out=null;
        try{JSONArray existing=read(c),next=new JSONArray();int first=Math.max(0,existing.length()-(MAX_RECORDS-1));
            for(int i=first;i<existing.length();i++)next.put(existing.get(i));next.put(record.value);
            out=f.startWrite();out.write(next.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);return true;
        }catch(Exception e){if(out!=null)f.failWrite(out);return false;}
    }
    public static synchronized void clear(Context c){file(c).delete();}
    public static String network(Context c){
        try{ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active=cm.getActiveNetwork();NetworkCapabilities n=active==null?null:cm.getNetworkCapabilities(active);
            if(n==null)return "无活动网络";
            List<String> names=new ArrayList<>();if(n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))names.add("Wi-Fi");
            if(n.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))names.add("移动网络");
            if(n.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))names.add("以太网");
            if(n.hasTransport(NetworkCapabilities.TRANSPORT_VPN))names.add("VPN");
            return String.join("、",names)+"；系统互联网验证="+n.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }catch(Exception e){return "无法获取网络状态";}
    }
    public static void environment(Context c,DiagnosticRecord r){
        try{r.put("应用版本",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);}catch(Exception ignored){}
        r.put("Android",Build.VERSION.RELEASE);r.put("API级别",Build.VERSION.SDK_INT);
        r.put("设备",Build.MANUFACTURER+" "+Build.MODEL);r.put("网络",network(c));
    }
    public static synchronized String report(Context c){
        StringBuilder text=new StringBuilder("FloatMemo AI 诊断记录\n\n仅保存最近30次请求。记录保存在本机，不自动上传，不包含请求正文或请求头。\n错误响应片段已限长和脱敏，但可能仍含服务商返回的信息，分享前请检查。\n系统网络验证状态仅供参考，不能单独证明接口可达。\n\n");
        DiagnosticRecord current=new DiagnosticRecord("当前环境");environment(c,current);
        try{text.append(current.value.toString(2)).append("\n\n");JSONArray a=read(c);
            if(a.length()==0)text.append("还没有记录。更新前发生的错误无法补记；请点击测试或重现一次失败。\n");
            SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT);
            for(int i=a.length()-1;i>=0;i--){JSONObject item=a.getJSONObject(i);text.append("—— ").append(fmt.format(new Date(item.optLong("时间戳")))).append(" ——\n").append(item.toString(2)).append("\n\n");}
        }catch(Exception e){text.append("诊断文件读取失败，请导出当前页面后清空记录再测试。\n");}
        return text.toString();
    }
}

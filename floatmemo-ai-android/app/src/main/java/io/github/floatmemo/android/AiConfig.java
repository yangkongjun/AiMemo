package io.github.floatmemo.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.net.URI;
import java.security.KeyStore;
import java.util.Calendar;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials are encrypted with a non-exportable Android Keystore key. */
public final class AiConfig {
    private final SharedPreferences p;
    private AiConfig(Context c){p=c.getSharedPreferences("ai-config",0);}
    public static AiConfig get(Context c){return new AiConfig(c.getApplicationContext());}
    public boolean enabled(){return p.getBoolean("enabled",false);}
    public String endpoint(){return p.getString("endpoint","");}
    public String model(){return p.getString("model","");}
    public long epoch(){return p.getLong("epoch",0);}
    public int limit(){return p.getInt("limit",20);}
    public String key()throws Exception{
        String value=p.getString("key","");if(value.isEmpty())return "";
        String[] a=value.split(":");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,secret(),new GCMParameterSpec(128,Base64.decode(a[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(a[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
    private SecretKey secret()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias("floatmemo-ai"))return (SecretKey)ks.getKey("floatmemo-ai",null);
        KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder("floatmemo-ai",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();
    }
    public static String validatedEndpoint(String value)throws Exception{
        String endpoint=value.trim();URI u=new URI(endpoint);
        if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null)throw new Exception("接口必须是无账号、查询参数和片段的 HTTPS 地址");
        return endpoint;
    }
    public void save(String endpoint,String model,String newKey,int limit,boolean enable)throws Exception{
        String e=endpoint.trim().isEmpty()&&!enable?"":validatedEndpoint(endpoint);
        if(enable&&model.trim().isEmpty())throw new Exception("请填写模型名称");
        SharedPreferences.Editor edit=p.edit();
        if(!newKey.isEmpty()){Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,secret());byte[] encrypted=c.doFinal(newKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            edit.putString("key",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP));}
        if(newKey.isEmpty()&&!endpoint().isEmpty()&&!validatedEndpoint(endpoint()).equals(e))throw new Exception("更换服务地址时请重新填写 API Key");
        if(enable&&newKey.isEmpty()&&key().isEmpty())throw new Exception("请填写 API Key");
        edit.putString("endpoint",e).putString("model",model.trim()).putInt("limit",Math.max(1,Math.min(500,limit)))
            .putBoolean("enabled",enable).putLong("epoch",epoch()+1);if(!edit.commit())throw new Exception("配置保存失败");
    }
    public boolean disable(){return p.edit().putBoolean("enabled",false).putLong("epoch",epoch()+1).commit();}
    public boolean clear(){return p.edit().clear().putLong("epoch",epoch()+1).commit();}
    private String today(){Calendar c=Calendar.getInstance();return c.get(Calendar.YEAR)+"-"+c.get(Calendar.DAY_OF_YEAR);}
    public int used(){return today().equals(p.getString("day",""))?p.getInt("used",0):0;}
    public static synchronized void reserve(Context c)throws Exception{
        AiConfig a=get(c);if(!a.enabled())throw new Exception("AI 已关闭");int used=a.used();
        if(used>=a.limit())throw new Exception("今日请求次数已达上限，请明日继续或调整设置");
        if(!a.p.edit().putString("day",a.today()).putInt("used",used+1).commit())throw new Exception("无法保存请求计数");
    }
}

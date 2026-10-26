package io.github.floatmemo.android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Permission flow stays in a visible Activity, never starts a service from a boot receiver. */
public abstract class BaseActivity extends Activity {
    private String pendingAction, pendingId;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            pendingAction = state.getString("overlayAction");
            pendingId = state.getString("overlayId");
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("overlayAction",pendingAction); state.putString("overlayId",pendingId);
    }
    protected void requestOverlay(String action, String id) {
        pendingAction = action; pendingId = id;
        if (!Settings.canDrawOverlays(this)) {
            new android.app.AlertDialog.Builder(this).setTitle("允许便签悬浮显示")
                .setMessage("请在下一页打开“显示在其他应用上层”，返回后会显示便签。")
                .setPositiveButton("去设置", (d,w) -> {
                    try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:"+getPackageName()))); }
                    catch (Exception e) {
                        pendingAction = null;
                        Toast.makeText(this,"无法打开权限页，请到系统设置授予悬浮窗权限",Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("取消",(d,w)->pendingAction=null)
                .setOnCancelListener(d->pendingAction=null).show();
            return;
        }
        continueOverlay();
    }
    @Override protected void onResume() {
        super.onResume();
        if (pendingAction != null && Settings.canDrawOverlays(this)) continueOverlay();
    }
    private void continueOverlay() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED && !getPreferences(0).getBoolean("notificationAsked",false)) {
            getPreferences(0).edit().putBoolean("notificationAsked",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},71);
            return;
        }
        String action = pendingAction, id = pendingId;
        pendingAction = null; pendingId = null;
        if (action == null) return;
        try { startForegroundService(new Intent(this,OverlayService.class).setAction(action).putExtra("noteId",id)); }
        catch (RuntimeException e) {
            Toast.makeText(this,"悬浮服务启动失败，请保持应用在前台后重试",Toast.LENGTH_LONG).show();
        }
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);
        if (code == 71 && pendingAction != null && Settings.canDrawOverlays(this)) continueOverlay();
    }
}

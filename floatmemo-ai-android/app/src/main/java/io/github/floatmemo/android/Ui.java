package io.github.floatmemo.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BG = Color.rgb(246,244,239), INK = Color.rgb(28,47,39);
    public static final int MUTED = Color.rgb(102,114,105), GREEN = Color.rgb(35,99,75);
    public static final int[] COLORS = {0xFFFFEDAB,0xFFDCECF8,0xFFDDEEDC,0xFFF8DEDE,
        0xFFE9E0F5,0xFFF9DFC5,0xFFE6E8E6,0xFFFFFEFA};
    public static final String[] COLOR_NAMES = {"杏黄","晴蓝","浅绿","樱粉","淡紫","暖橙","雾灰","纸白"};
    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
    public static int toDp(Context c, int px) { return Math.round(px / c.getResources().getDisplayMetrics().density); }
    public static GradientDrawable round(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }
    public static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    public static LinearLayout row(Context c) {
        LinearLayout v = new LinearLayout(c); v.setGravity(android.view.Gravity.CENTER_VERTICAL); return v;
    }
    public static TextView text(Context c, String value, int sp, int color) {
        TextView v = new TextView(c); v.setText(value); v.setTextSize(sp); v.setTextColor(color); return v;
    }
    public static TextView bold(Context c, String value, int sp, int color) {
        TextView v = text(c,value,sp,color); v.setTypeface(null, Typeface.BOLD); return v;
    }
    public static Button button(Context c, String label, boolean primary, View.OnClickListener listener) {
        Button b = new Button(c); b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(primary ? Color.WHITE : GREEN); b.setMinHeight(dp(c,48)); b.setMinimumHeight(dp(c,48));
        b.setPadding(dp(c,12),0,dp(c,12),0);
        b.setBackground(round(c,primary ? GREEN : 0xFFE7EDE6,12)); b.setOnClickListener(listener);
        return b;
    }
    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c); e.setTextColor(INK); e.setHintTextColor(MUTED); e.setTextSize(16);
        e.setHint(hint); e.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));
        e.setBackground(round(c,Color.WHITE,12)); return e;
    }
    public static void space(LinearLayout parent, int height) {
        View v = new View(parent.getContext()); parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),height)));
    }
    public static void insetActivity(Activity activity, LinearLayout root, int padding) {
        root.setBackgroundColor(BG);
        int p = dp(activity,padding);
        root.setPadding(p,p,p,p);
        root.setOnApplyWindowInsetsListener((v,insets) -> {
            int bottom;
            int top;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                bottom = bars.bottom; top = bars.top;
                v.setPadding(p+bars.left,p+top,p+bars.right,p+bottom);
            } else {
                v.setPadding(p+insets.getSystemWindowInsetLeft(),p+insets.getSystemWindowInsetTop(),
                    p+insets.getSystemWindowInsetRight(),p+insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        activity.setContentView(root); root.requestApplyInsets();
    }
}

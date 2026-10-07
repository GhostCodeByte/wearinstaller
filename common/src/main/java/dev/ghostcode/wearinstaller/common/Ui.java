package dev.ghostcode.wearinstaller.common;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;

public final class Ui {
    public static final int BG = Color.rgb(16,23,16), CARD = Color.rgb(28,37,28),
            GREEN = Color.rgb(184,245,104), WHITE = Color.rgb(239,245,234), MUTED = Color.rgb(163,180,157);
    public static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    public static LinearLayout column(Context c, int padding) {
        LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c,padding); v.setPadding(p,p,p,p); return v;
    }
    public static GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    public static TextView text(Context c, String text, int size, int color) {
        TextView v = new TextView(c); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setPadding(0,dp(c,4),0,dp(c,4)); return v;
    }
    public static TextView title(Context c, String text, int size) {
        TextView t = text(c,text,size,WHITE); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t;
    }
    public static Button button(Context c, String text, boolean primary) {
        Button b = new Button(c); b.setText(text); b.setAllCaps(false); b.setTextSize(16);
        b.setTextColor(primary ? BG : WHITE); b.setBackground(shape(primary ? GREEN : CARD,dp(c,16)));
        b.setMinHeight(dp(c,52));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(c,56));
        lp.topMargin = dp(c,12); b.setLayoutParams(lp); return b;
    }
    public static void gap(LinearLayout parent, int size) {
        android.view.View v = new android.view.View(parent.getContext()); parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),size)));
    }
    private Ui() {}
}

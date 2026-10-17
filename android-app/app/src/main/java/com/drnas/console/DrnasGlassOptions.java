package com.drnas.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Explorer-inspired translucent options panel. Clickable rounded rows retain
 * clear, live green ON / gray OFF indicators without dismissing the menu.
 * Non-toggle actions close it before invoking their existing handler.
 *
 * This only styles Android-owned emulator controls. It does not alter Wine's
 * native Explorer menus, so no unsupported Wine theming is implied.
 */
final class DrnasGlassOptions {
    interface OnChoose { void onChoose(DialogInterface menu,int index); }
    private static final int[] ORDER={
            21,20,19,17,18,22,28,29,1,23,24,25,26,27,
            0,2,6,7,8,4,5,9,3,
            10,11,12,13,14,15,16
    };
    private static final int TEXT=0xffe7f3fb;
    private static final int GREEN=0xff80edbd;
    private static final int MUTED=0xff9aadc1;

    private final Activity activity;
    private final String[] labels;
    private final IntPredicate enabled;
    private final OnChoose onChoose;
    private final List<Runnable> refresh=new ArrayList<>();
    private final int scale;
    private AlertDialog dialog;

    private DrnasGlassOptions(Activity activity,String[] labels,IntPredicate enabled,OnChoose onChoose) {
        this.activity=activity;this.labels=labels;this.enabled=enabled;this.onChoose=onChoose;
        this.scale=0; // dimension helper uses current display density
    }
    static void show(Activity activity,String[] labels,IntPredicate enabled,OnChoose onChoose) {
        new DrnasGlassOptions(activity,labels,enabled,onChoose).show();
    }
    private int dp(float value) {
        return Math.round(value*activity.getResources().getDisplayMetrics().density);
    }
    private GradientDrawable box(int from,int to,int border,int radius) {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{from,to});
        d.setCornerRadius(dp(radius));
        if(border!=0)d.setStroke(dp(1),border);
        return d;
    }
    private TextView text(String value,int sp,int color,boolean bold) {
        TextView t=new TextView(activity);
        t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif-medium",bold?Typeface.BOLD:Typeface.NORMAL));
        return t;
    }
    private boolean toggle(int index) {
        return index==1||index==17||index==18||index==19||
               index==20||index==21||index==22||index==28||index==29;
    }
    private void title(LinearLayout content,String title) {
        TextView caption=text(title,10,0xff75bfcd,true);
        caption.setLetterSpacing(.12f);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,dp(30));
        p.topMargin=dp(8);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(caption,p);
    }
    private void option(LinearLayout content,int index) {
        if(index>=labels.length)return;
        final boolean isToggle=toggle(index);
        LinearLayout row=new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14),0,dp(12),0);
        LinearLayout.LayoutParams param=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,dp(46));
        param.bottomMargin=dp(6);
        content.addView(row,param);

        TextView label=text(labels[index],12,TEXT,true);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label,new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT,1));

        TextView status=text(isToggle?"OFF":"›",11,MUTED,true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(8),dp(4),dp(8),dp(4));
        row.addView(status,new LinearLayout.LayoutParams(dp(57),dp(27)));

        Runnable recolor=()->{
            boolean active=isToggle&&enabled.test(index);
            label.setTextColor(active?GREEN:TEXT);
            status.setText(isToggle?(active?"ON":"OFF"):"›");
            status.setTextColor(active?GREEN:MUTED);
            status.setBackground(box(active?0x7740aa80:0x4a283e56,
                    active?0x62417d6a:0x41233549,
                    active?0x9a76d6ab:0x437c9dbb,12));
            row.setBackground(box(active?0xcf203d42:0x9b182b3f,
                    active?0xca142f35:0x95122237,
                    active?0x9b4daf91:0x47678da4,14));
        };
        recolor.run();
        refresh.add(recolor);

        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(labels[index]+(isToggle?" prekidač":" akcija"));
        row.setOnClickListener(v->{
            if(dialog==null)return;
            if(!isToggle)dialog.dismiss();
            onChoose.onChoose(dialog,index);
            if(isToggle)for(Runnable r:refresh)r.run();
        });
    }
    private void show() {
        ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout panel=new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(17),dp(13),dp(17),dp(19));
        panel.setBackground(box(0xee1b3142,0xf60c1a2d,0x99789fb6,24));
        scroll.addView(panel);

        TextView header=text("EmuDrnas   /   CONTROL CENTER",17,TEXT,true);
        panel.addView(header);
        TextView subtitle=text("preAlpha 0.1  •  changes apply immediately",11,MUTED,false);
        LinearLayout.LayoutParams subParams=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin=dp(5);subParams.bottomMargin=dp(10);
        panel.addView(subtitle,subParams);

        title(panel,"LIVE CONTROLS");
        for(int i=0;i<13;i++)option(panel,ORDER[i]);
        title(panel,"RENDER A/B I MJERENJA");
        option(panel,27);
        title(panel,"WINDOWS / GENERAL");
        for(int i=13;i<22;i++)if(ORDER[i]!=27)option(panel,ORDER[i]);
        title(panel,"WSI DIJAGNOSTIKA");
        for(int i=22;i<ORDER.length;i++)if(ORDER[i]!=27)option(panel,ORDER[i]);

        AlertDialog.Builder builder=new AlertDialog.Builder(activity);
        dialog=builder.setView(scroll).create();
        dialog.setOnShowListener(d->{
            Window window=dialog.getWindow();
            if(window!=null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                DisplayMetrics m=activity.getResources().getDisplayMetrics();
                int width=Math.min(dp(515),m.widthPixels-dp(28));
                int maxHeight=(int)(m.heightPixels*.83f);
                window.setLayout(width,Math.min(maxHeight,dp(670)));
                WindowManager.LayoutParams params=window.getAttributes();
                params.dimAmount=.35f;
                window.setAttributes(params);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
        });
        dialog.show();
    }
}

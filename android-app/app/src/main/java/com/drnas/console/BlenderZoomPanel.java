package com.drnas.console;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;

/** Compact Blender viewport zoom via ordinary X11 mouse wheel buttons.
 * No Blender Python frame callback, Wine fullscreen or native GPU calls.
 * Touching the slider does not move the 3D viewport pointer location.
 */
final class BlenderZoomPanel extends LinearLayout {
    interface WheelSink { void send(int button,boolean pressed); }
    private final WheelSink wheel;
    private final SharedPreferences prefs;
    private final BlenderZoomSteps zoomSteps=new BlenderZoomSteps();
    private final SeekBar slider;
    private final Button expandButton;
    private final View minus,plus;
    private boolean expanded;

    BlenderZoomPanel(Context context,WheelSink sink) {
        super(context);
        wheel=sink;
        prefs=context.getSharedPreferences("blender-zoom",Context.MODE_PRIVATE);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(4),dp(3),dp(4),dp(3));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xa31c3042,0x8a101825});
        bg.setCornerRadius(dp(13));
        bg.setStroke(dp(1),0x7095c5db);
        setBackground(bg);
        setElevation(dp(6));
        setFocusable(false);
        expandButton=button("ZOOM",45);
        expandButton.setTextSize(11);
        expandButton.setContentDescription("Show or hide Blender 3D viewport zoom slider");
        addView(expandButton,new LayoutParams(dp(50),dp(40)));
        minus=button("−",36);
        ((Button)minus).setContentDescription("Zoom out Blender 3D viewport");
        addView(minus,new LayoutParams(dp(38),dp(40)));
        slider=new SeekBar(context);
        slider.setMax(100);
        slider.setProgress(BlenderZoomSteps.CENTER);
        slider.setFocusable(false);
        slider.setContentDescription("Blender 3D viewport zoom: slide right to zoom in, left to zoom out");
        slider.setProgressTintList(ColorStateList.valueOf(0xff8ce5ec));
        slider.setThumbTintList(ColorStateList.valueOf(0xffd4f7ff));
        LayoutParams sliderParams=new LayoutParams(dp(116),dp(40));
        sliderParams.setMargins(dp(0),0,dp(0),0);
        addView(slider,sliderParams);
        plus=button("+",36);
        ((Button)plus).setContentDescription("Zoom in Blender 3D viewport");
        addView(plus,new LayoutParams(dp(38),dp(40)));
        expandButton.setOnClickListener(v->setExpanded(!expanded));
        minus.setOnClickListener(v->pulseWheel(5,2));
        plus.setOnClickListener(v->pulseWheel(4,2));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {
                zoomSteps.reset(bar.getProgress());
            }
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser) {
                if(!fromUser)return;
                int steps=zoomSteps.update(progress);
                if(steps!=0)pulseWheel(steps>0?4:5,Math.abs(steps));
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                // Spring-loaded center. Reset without an opposite wheel event.
                zoomSteps.reset(BlenderZoomSteps.CENTER);
                bar.setProgress(BlenderZoomSteps.CENTER);
            }
        });
        setExpanded(prefs.getBoolean("expanded",true));
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private Button button(String title,int width) {
        Button b=new Button(getContext());
        b.setText(title);
        b.setTextSize(17);
        b.setTextColor(0xfff2f8ff);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(dp(2),0,dp(2),0);
        b.setFocusable(false);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0x88527389);
        bg.setCornerRadius(dp(9));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x5078dce8),bg,null));
        return b;
    }
    private void pulseWheel(int button,int clicks) {
        // X11 wheel buttons 4=in, 5=out are now actually mapped by Pointer.
        // The guest dispatches wheel events to the editor currently under
        // the cursor. For viewport zoom, hover the 3D View (not Properties).
        for(int i=0;i<Math.min(10,clicks);i++) {
            wheel.send(button,true);
            wheel.send(button,false);
        }
    }
    private void setExpanded(boolean show) {
        expanded=show;
        minus.setVisibility(show?View.VISIBLE:View.GONE);
        slider.setVisibility(show?View.VISIBLE:View.GONE);
        plus.setVisibility(show?View.VISIBLE:View.GONE);
        prefs.edit().putBoolean("expanded",show).apply();
    }
}

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

/** Blender viewport zoom. Smooth mode drives Blender's view_distance through a
 * tiny async app-private bridge; long-press ZOOM for the original wheel mode.
 * Never adjusts Wine HWND style or touches the Vulkan renderer.
 */
final class BlenderZoomPanel extends LinearLayout {
    interface WheelSink { void send(int button,boolean pressed); }
    interface SmoothSink { void sendDelta(int delta); }
    private final WheelSink wheel;
    private final SmoothSink smooth;
    private boolean wheelFallback;
    private int wheelRemainder;
    private final SharedPreferences prefs;
    private final BlenderZoomSteps zoomSteps=new BlenderZoomSteps();
    private final SeekBar slider;
    private final Button expandButton;
    private final View minus,plus;
    private boolean expanded;

    BlenderZoomPanel(Context context,WheelSink wheelSink,SmoothSink smoothSink) {
        super(context);
        wheel=wheelSink;
        smooth=smoothSink;
        prefs=context.getSharedPreferences("blender-zoom",Context.MODE_PRIVATE);
        // Smooth is always the default for a new version; allow explicit
        // long-press fallback when Blender's Python startup is unavailable.
        wheelFallback=prefs.getBoolean("wheel_fallback",false);
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
        expandButton.setOnLongClickListener(v->{
            wheelFallback=!wheelFallback;
            wheelRemainder=0;
            prefs.edit().putBoolean("wheel_fallback",wheelFallback).apply();
            updateModeLabel();
            android.widget.Toast.makeText(getContext(),wheelFallback?
                    "ZOOM: wheel fallback":"ZOOM: smooth",android.widget.Toast.LENGTH_SHORT).show();
            return true;
        });
        minus.setOnClickListener(v->zoomDelta(-12));
        plus.setOnClickListener(v->zoomDelta(12));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {
                zoomSteps.reset(bar.getProgress());
            }
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser) {
                if(!fromUser)return;
                int delta=zoomSteps.update(progress);
                if(delta!=0)zoomDelta(delta);
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                // Spring-loaded center. Reset without an opposite wheel event.
                zoomSteps.reset(BlenderZoomSteps.CENTER);
                bar.setProgress(BlenderZoomSteps.CENTER);
            }
        });
        setExpanded(prefs.getBoolean("expanded",true));
        updateModeLabel();
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
    private void updateModeLabel() {
        expandButton.setText(wheelFallback?"WHEEL":"ZOOM");
        expandButton.setContentDescription(wheelFallback?
                "Wheel zoom fallback. Long press for smooth zoom.":
                "Smooth Blender viewport zoom. Long press for wheel fallback.");
    }
    private void zoomDelta(int delta) {
        if(delta==0)return;
        if(!wheelFallback) {
            smooth.sendDelta(delta);
            return;
        }
        // Preserve r59's verified wheel path for compatibility, only
        // coalescing movement in multiples of five slider units.
        wheelRemainder+=delta;
        int clicks=wheelRemainder/5;
        wheelRemainder-=clicks*5;
        if(clicks!=0)pulseWheel(clicks>0?4:5,Math.abs(clicks));
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

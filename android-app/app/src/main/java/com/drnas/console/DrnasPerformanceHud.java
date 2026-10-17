package com.drnas.console;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.View;
import java.util.Locale;

/**
 * Compact, opt-in, glass-like DRNAS HUD. Exactly three independently
 * readable tiles, not a long string that gets hidden under toolbar buttons.
 *
 * Data is sampled by WineGuiActivity's low-frequency 650ms ticker. This view
 * never touches Wine, DXVK, X11 bitmaps or the game draw path.
 *
 * FPS = Android/X11 displayed screen compositions, not DXVK Present FPS.
 * RAM = host Android system RAM percentage, not allocated guest Wine RAM.
 */
final class DrnasPerformanceHud extends View {
    private static final int[] ACCENTS={0xff64e6cf,0xffa5baff,0xffffc878};
    private static final String[] TITLES={"DISPLAY FPS","SYSTEM RAM","BATTERY"};

    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint divider=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();
    private final ActivityManager manager;
    private final ActivityManager.MemoryInfo memoryInfo=new ActivityManager.MemoryInfo();
    private final String[] values={"--","--%","--°C"};
    private final float density,scaledDensity;
    private final Typeface labelFont=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    private final Typeface valueFont=Typeface.create("sans-serif-medium",Typeface.BOLD);
    private Shader glassGradient;
    private boolean active;

    DrnasPerformanceHud(Context context) {
        super(context);
        manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        DisplayMetrics metrics=getResources().getDisplayMetrics();
        density=metrics.density;
        scaledDensity=metrics.scaledDensity;
        setClickable(false);
        setFocusable(false);
        // The HUD remains an isolated GPU-composited View, not a redraw of
        // the Wine desktop framebuffer whenever a metric changes.
        setLayerType(View.LAYER_TYPE_NONE,null);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Math.max(1f,density));
        divider.setStrokeWidth(Math.max(1f,0.8f*density));
        text.setTextAlign(Paint.Align.CENTER);
    }

    void setActive(boolean enabled) {
        active=enabled;
        setVisibility(enabled?VISIBLE:GONE);
    }
    boolean isActive(){return active;}

    void sample(float displayedFps) {
        if(!active)return;
        values[0]=String.format(Locale.ROOT,"%.0f",displayedFps);

        if(manager!=null) {
            manager.getMemoryInfo(memoryInfo);
            if(memoryInfo.totalMem>0) {
                int percent=(int)Math.round(100.0d*(memoryInfo.totalMem-memoryInfo.availMem)/
                        memoryInfo.totalMem);
                values[1]=percent+"%";
            } else values[1]="--%";
        }

        Intent battery=getContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery!=null) {
            int temp=battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
            values[2]=temp==Integer.MIN_VALUE?"--°C":
                    String.format(Locale.ROOT,"%.1f°C",temp/10.0);
        } else values[2]="--°C";

        postInvalidateOnAnimation();
    }

    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        super.onSizeChanged(w,h,oldW,oldH);
        // Cache shader once per size change. No new gradients on every draw.
        if(w>0 && h>0) {
            glassGradient=new LinearGradient(0,0,0,h,
                    new int[]{0xc5172736,0xb10b1728,0xd4132030},
                    new float[]{0f,0.47f,1f},Shader.TileMode.CLAMP);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(!active)return;
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        float inset=1.5f*density;
        float radius=Math.min(19f*density,h*.39f);
        box.set(inset,inset,w-inset,h-inset);

        // Transparent glass-style capsule, fine cool border and top glint.
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(glassGradient);
        canvas.drawRoundRect(box,radius,radius,fill);
        fill.setShader(null);

        border.setColor(0x8a9ad6f4);
        canvas.drawRoundRect(box,radius,radius,border);
        border.setColor(0x3cffffff);
        canvas.drawRoundRect(inset+2f*density,inset+1.6f*density,
                w-inset-2f*density,h*.48f,radius*.72f,radius*.72f,border);

        float segment=w/3f;
        float titleSize=Math.min(10.5f*scaledDensity,h*.225f);
        float valueSize=Math.min(17f*scaledDensity,h*.365f);
        // Guard against accessibility font scaling / narrower device widths.
        valueSize=Math.min(valueSize,segment/5.9f);
        titleSize=Math.min(titleSize,segment/10.5f);
        float topLine=h*.365f;
        float valueLine=h*.730f;

        for(int i=0;i<3;i++) {
            float cx=(i+.5f)*segment;
            if(i>0) {
                divider.setColor(0x35cad9ec);
                float x=i*segment;
                canvas.drawLine(x,h*.25f,x,h*.76f,divider);
            }
            fill.setColor(ACCENTS[i]);
            canvas.drawCircle(cx-segment*.34f,h*.325f,Math.max(2.0f*density,h*.043f),fill);

            text.setTypeface(labelFont);
            text.setTextSize(titleSize);
            text.setColor(0xffb2c5d4);
            canvas.drawText(TITLES[i],cx,topLine,text);

            text.setTypeface(valueFont);
            text.setTextSize(valueSize);
            text.setColor(i==0?0xffeffff9:0xfff5faff);
            canvas.drawText(values[i],cx,valueLine,text);
        }
    }
}

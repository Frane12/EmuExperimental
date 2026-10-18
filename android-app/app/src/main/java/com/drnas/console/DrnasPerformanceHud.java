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

    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint divider=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF();
    private final ActivityManager manager;
    private final ActivityManager.MemoryInfo memoryInfo=new ActivityManager.MemoryInfo();
    private final String[] values={"--","--","--","--","--","--"};
    private final HudMetrics stats=new HudMetrics();
    private final float density,scaledDensity;
    private final Typeface valueFont=Typeface.create("sans-serif-medium",Typeface.BOLD);
    private Shader glassGradient;
    private volatile boolean active;
    private final java.util.concurrent.atomic.AtomicBoolean sampling=new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.ThreadPoolExecutor METRICS=new java.util.concurrent.ThreadPoolExecutor(
            0,1,10,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.LinkedBlockingQueue<Runnable>(),
            task->{Thread thread=new Thread(task,"drnas-hud-metrics");thread.setDaemon(true);return thread;});

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

    void sample(float displayedFps,float displayedFrameMs) {
        if(!active || !sampling.compareAndSet(false,true))return;
        // /proc, KGSL and battery Binder calls must not block the UI/render thread.
        METRICS.execute(()->{
            try {
                String[] snapshot=collect(displayedFps,displayedFrameMs);
                post(()->{
                    if(!active || !isAttachedToWindow())return;
                    System.arraycopy(snapshot,0,values,0,values.length);
                    postInvalidateOnAnimation();
                });
            } finally {sampling.set(false);}
        });
    }

    private String[] collect(float displayedFps,float displayedFrameMs) {
        String[] values=new String[6];
        values[0]=displayedFps>0?String.format(Locale.ROOT,"%.0f",displayedFps):"--";
        values[1]=stats.cpu();
        values[2]=stats.gpu();
        values[5]=displayedFrameMs>0?
                String.format(Locale.ROOT,"%.1fms",displayedFrameMs):"--";

        if(manager!=null) {
            manager.getMemoryInfo(memoryInfo);
            if(memoryInfo.totalMem>0) {
                int percent=(int)Math.round(100.0d*(memoryInfo.totalMem-memoryInfo.availMem)/
                        memoryInfo.totalMem);
                values[3]=percent+"%";
            } else values[3]="--";
        }

        Intent battery=getContext().registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery!=null) {
            int temp=battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
            values[4]=temp==Integer.MIN_VALUE?"--°C":
                    String.format(Locale.ROOT,"%.1f°C",temp/10.0);
        } else values[4]="--°C";

        return values;
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
        if(w<=0 || h<=0)return;
        float inset=Math.max(1f,density);
        float radius=Math.min(14f*density,h*.43f);
        box.set(inset,inset,w-inset,h-inset);
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(glassGradient);
        canvas.drawRoundRect(box,radius,radius,fill);
        fill.setShader(null);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(Math.max(1f,density));
        border.setColor(0x668ac6de);
        canvas.drawRoundRect(box,radius,radius,border);
        // Six honest metrics: host display FPS / total device (or labeled APP)
        // CPU / KGSL GPU busy when readable / host RAM / battery / display FT.
        // Five thin dividers; no nested oval or giant typography.
        float segment=w/6f;
        final String[] titles={"FPS","CPU","GPU","RAM","BAT","FT"};
        for(int i=0;i<6;i++) {
            float x=(i+.5f)*segment;
            if(i>0){
                divider.setColor(0x6084adbf);
                canvas.drawLine(i*segment,h*.19f,i*segment,h*.81f,divider);
            }
            String result=titles[i]+" "+values[i];
            text.setTypeface(valueFont);
            float txt=Math.min(10.5f*scaledDensity,segment/7.0f);
            text.setTextSize(txt);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(i==0?0xff8ff0cf:0xffecf7fb);
            canvas.drawText(result,x,h*.68f,text);
        }
    }
}


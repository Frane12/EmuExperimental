package com.drnas.console;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Subtle, full-page bora-blown pollen BEHIND the GUI (never over button text).
 * 42 deterministic particles, distinct drift/fall speeds, changing gusts and
 * directions. Single Canvas view; no bitmaps, I/O, allocations per frame or
 * worker threads. Frames are requested only while shown.
 */
final class BoraPollenBackground extends View {
    private static final int COUNT=42;
    private static final long FRAME_MS=55L; // ~18 FPS; does not compete with Wine.
    private final float[] startX=new float[COUNT],startY=new float[COUNT];
    private final float[] drift=new float[COUNT],fall=new float[COUNT];
    private final float[] phase=new float[COUNT],radius=new float[COUNT];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sky=new Paint();
    private final Rect visible=new Rect();
    private final boolean enabled;
    private final long started=SystemClock.uptimeMillis();
    private final float density;
    BoraPollenBackground(Context context) {
        super(context);
        density=getResources().getDisplayMetrics().density;
        enabled=context.getSharedPreferences("WineGuiActivity",Context.MODE_PRIVATE)
                .getBoolean("ui_bora_pollen",true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        for(int i=0;i<COUNT;i++) {
            // Deterministic low-discrepancy distribution; stable across redraws.
            startX[i]=fract((i+1)*.61803398875f);
            startY[i]=fract((i+1)*.41421356237f);
            phase[i]=i*1.618f;
            drift[i]=-.018f+(i%7)*.0063f;  // both directions in local swirls
            fall[i]=.009f+(i%9)*.0039f;    // differing downward velocities
            radius[i]=(.48f+(i%4)*.30f)*density;
        }
        paint.setColor(0xfff9edc8);
    }
    private static float fract(float v){return v-(float)Math.floor(v);}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
        super.onSizeChanged(w,h,oldW,oldH);
        sky.setShader(new LinearGradient(0,0,Math.max(1,w),Math.max(1,h),
                new int[]{0xff080f1c,0xff0c2331,0xff07101e},
                new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        canvas.drawRect(0,0,w,h,sky);
        boolean animate=enabled&&isShown()&&getWindowVisibility()==VISIBLE;
        if(!animate)return;
        float t=(SystemClock.uptimeMillis()-started)/1000f;
        // Bora gusts are layered waves. They change direction smoothly rather
        // than snapping, while each mote has its own drift and falling rate.
        float strong=(float)(.080*Math.sin(t*.17)+.025*Math.sin(t*.49+1.2));
        for(int i=0;i<COUNT;i++){
            float p=phase[i];
            float wind=strong+(float)(.014*Math.sin(t*.33+p*.7));
            float x=fract(startX[i]+t*drift[i]+wind+
                    (float)(.023*Math.sin(t*.61+p)));
            float y=fract(startY[i]+t*fall[i]+
                    (float)(.008*Math.sin(t*.44+p*.8)));
            // Fade motes as they enter/leave the viewport, no hard wrap pops.
            float edge=Math.min(Math.min(x,1-x),Math.min(y,1-y));
            float fade=Math.min(1f,Math.max(0f,edge*15));
            paint.setAlpha((int)((38+(i%5)*11)*fade));
            canvas.drawCircle(x*w,y*h,radius[i],paint);
        }
        if(getLocalVisibleRect(visible))postInvalidateDelayed(FRAME_MS);
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if(visibility==VISIBLE)invalidate();
    }
    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if(visible)invalidate();
    }
}

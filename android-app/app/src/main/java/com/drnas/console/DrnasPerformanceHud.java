package com.drnas.console;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.util.Locale;

/**
 * Deliberately small HUD: displayed-frame rate, Android system RAM use and
 * battery temperature. Sampling is performed at 500 ms intervals, never in
 * the graphics upload loop. FPS measures Android X11 screen draws, NOT engine
 * simulation FPS or DXVK's internal Present count.
 */
final class DrnasPerformanceHud extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ActivityManager manager;
    private final ActivityManager.MemoryInfo memoryInfo=new ActivityManager.MemoryInfo();
    private final RectF background=new RectF();
    private String label="DISPLAY -- FPS   |   RAM --%   |   BAT --";
    private boolean active;

    DrnasPerformanceHud(Context context) {
        super(context);
        manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        setClickable(false);
        setFocusable(false);
        setLayerType(View.LAYER_TYPE_NONE,null);
        setWillNotDraw(false);
    }
    void setActive(boolean enabled) {
        active=enabled;
        setVisibility(enabled?VISIBLE:GONE);
    }
    boolean isActive(){return active;}
    void sample(float displayedFps) {
        if(!active)return;
        int ramPercent=-1;
        if(manager!=null) {
            manager.getMemoryInfo(memoryInfo);
            if(memoryInfo.totalMem>0)
                ramPercent=(int)Math.round(100.0d*(memoryInfo.totalMem-memoryInfo.availMem)/
                        memoryInfo.totalMem);
        }
        String bat="--";
        Intent battery=getContext().registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery!=null) {
            int temp=battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
            if(temp!=Integer.MIN_VALUE)bat=String.format(Locale.ROOT,"%.1f°C",temp/10.0);
        }
        label=String.format(Locale.ROOT,"DISPLAY %.0f FPS   |   RAM %s   |   BAT %s",
                displayedFps,ramPercent>=0?ramPercent+"%":"--",bat);
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(!active)return;
        float w=getWidth(),h=getHeight();
        background.set(0,0,w,h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xB5091628);
        canvas.drawRoundRect(background,h*.26f,h*.26f,paint);
        paint.setColor(0xFFF0F7FC);
        float textSize=Math.min(h*.50f,w/34f);
        paint.setTextSize(textSize);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label,w/2f,h*.65f,paint);
    }
}

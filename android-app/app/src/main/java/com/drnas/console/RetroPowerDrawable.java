package com.drnas.console;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Font-independent retro power symbol, with a soft neon outline. */
final class RetroPowerDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring=new RectF();
    private final int size,color;
    private int opacity=255;
    RetroPowerDrawable(int size,int color) {
        this.size=size;this.color=color;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void draw(Canvas canvas) {
        Rect bounds=getBounds();
        int save=canvas.save();
        canvas.translate(bounds.left,bounds.top);
        canvas.scale(bounds.width()/32f,bounds.height()/32f);
        ring.set(6,6,26,26);
        paint.setColor(color);
        for(int layer=0;layer<3;layer++) {
            paint.setStrokeWidth(layer==0?7f:layer==1?4.5f:2.3f);
            paint.setAlpha(opacity*(layer==0?24:layer==1?60:255)/255);
            canvas.drawArc(ring,-48,276,false,paint);
            canvas.drawLine(16,3.5f,16,14,paint);
        }
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){opacity=alpha;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

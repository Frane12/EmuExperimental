package com.drnas.console;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Original code-drawn four-colour retro flag for the Windows desktop button. */
final class RetroWindowsDrawable extends Drawable {
    private final int size;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] panes=new Path[4];
    private final int[] colors={0xffff695b,0xff72dc69,0xff62b7ff,0xffffd467};
    RetroWindowsDrawable(int size){
        this.size=size;
        for(int i=0;i<4;i++){
            float x=i%2==0?7:19,y=i/2==0?5:18;
            Path p=new Path();p.moveTo(x,y+2);p.cubicTo(x+4,y+3,x+7,y,x+10,y);
            p.lineTo(x+10,y+10);p.cubicTo(x+7,y+10,x+4,y+13,x,y+12);p.close();panes[i]=p;
        }
    }
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();int saved=canvas.save();canvas.translate(b.left,b.top);
        canvas.scale(b.width()/32f,b.height()/32f);
        for(int i=0;i<4;i++){paint.setStyle(Paint.Style.FILL);paint.setColor(colors[i]);canvas.drawPath(panes[i],paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.6f);paint.setColor(0xff102b39);canvas.drawPath(panes[i],paint);}
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xff79d9ca);
        canvas.drawRect(1,10,4,13,paint);canvas.drawRect(3,17,5,19,paint);canvas.drawRect(1,23,4,26,paint);
        canvas.restoreToCount(saved);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

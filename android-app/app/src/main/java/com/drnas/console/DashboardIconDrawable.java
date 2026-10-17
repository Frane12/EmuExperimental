package com.drnas.console;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Self-drawn dashboard pictograms, rather than font glyphs / missing emoji.
 * All use the same optical 64x64 grid and soft two-tone retro-glass highlights.
 */
final class DashboardIconDrawable extends Drawable {
    static final String POWER="power", RESET="reset", FILES="files", GAMEPAD="gamepad",
            GPU="gpu", DXVK="dxvk", LOG="log", SETTINGS="settings", FOLDER="folder";
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final Path path=new Path();
    private final String kind;
    private final int size;
    private int alpha=255;
    DashboardIconDrawable(String kind,int pixels) {this.kind=kind;size=pixels;}
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    private int tint(int color){return (color&0x00ffffff)|((ColorAlpha(color)*alpha/255)<<24);}
    private static int ColorAlpha(int color){return color>>>24;}
    private void color(int argb){paint.setColor(tint(argb));paint.setStyle(Paint.Style.FILL);paint.setStrokeWidth(1);}
    private void round(Canvas c,float x,float y,float w,float h,float r,int argb){
        color(argb);rect.set(x,y,x+w,y+h);c.drawRoundRect(rect,r,r,paint);
    }
    private void disc(Canvas c,float x,float y,float r,int argb){color(argb);c.drawCircle(x,y,r,paint);}
    private void stroke(Canvas c,int argb,float width,float... xy){
        color(argb);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeWidth(width);
        path.reset();path.moveTo(xy[0],xy[1]);
        for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);
        c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
    }
    private void ring(Canvas c,float x,float y,float r,int argb,float sw,float from,float sweep){
        color(argb);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(sw);rect.set(x-r,y-r,x+r,y+r);
        c.drawArc(rect,from,sweep,false,paint);paint.setStyle(Paint.Style.FILL);
    }
    @Override public void draw(Canvas canvas){
        if(getBounds().isEmpty())return;
        int save=canvas.save();
        canvas.translate(getBounds().left,getBounds().top);
        canvas.scale(getBounds().width()/64f,getBounds().height()/64f);
        switch(kind){
            case POWER:
                ring(canvas,32,33,23,0x77460016,10,-49,278);
                ring(canvas,32,33,21,0xffff647c,7,-49,278);
                stroke(canvas,0xffffb0ac,7,32,6,32,31);
                break;
            case RESET:
                ring(canvas,32,32,22,0xff6ebcf6,6,205,235);
                ring(canvas,32,32,22,0xffc4efff,2,220,200);
                stroke(canvas,0xff97e5ff,7,48,13,52,28,38,27);
                break;
            case FILES:
            case FOLDER:
                round(canvas,5,17,54,39,7,0xff3b7fb0);
                round(canvas,8,11,27,18,4,0xffffde85);
                round(canvas,7,22,51,31,5,0xffffb72e);
                round(canvas,11,26,42,17,4,0xffffdc68);
                break;
            case GAMEPAD:
                round(canvas,5,19,54,30,14,0xff4f91cd);
                round(canvas,8,21,48,24,12,0xffa1dfff);
                stroke(canvas,0xff254e7b,5,17,32,31,32);
                stroke(canvas,0xff254e7b,5,24,25,24,39);
                disc(canvas,42,29,4,0xff377bc2);disc(canvas,49,37,4,0xff4c74bb);
                break;
            case GPU:
                round(canvas,5,9,54,41,7,0xff497ab0);
                round(canvas,9,13,46,30,4,0xffa1e9ff);
                round(canvas,13,17,38,22,3,0xff163e66);
                stroke(canvas,0xffe4fdff,5,32,51,32,58);
                stroke(canvas,0xffe4fdff,4,23,58,41,58);
                gear(canvas,47,44,13,0xff5da9e7);
                break;
            case DXVK:
                gear(canvas,32,33,23,0xff74bcf3);break;
            case SETTINGS:
                gear(canvas,32,33,23,0xff75c8f6);
                break;
            case LOG:
                round(canvas,13,5,39,55,6,0xff578abb);
                round(canvas,17,8,31,49,4,0xffd8f3ff);
                round(canvas,22,14,22,5,2,0xff58bdeb);
                for(int i=0;i<4;i++)round(canvas,22,26+i*7,19,2,1,0xff4783ac);
                break;
            default:gear(canvas,32,32,20,0xff69c8e5);
        }
        canvas.restoreToCount(save);
    }
    private void gear(Canvas c,float x,float y,float radius,int base){
        int save=c.save();c.translate(x,y);
        for(int n=0;n<8;n++){
            c.save();c.rotate(n*45);
            round(c,-5,-radius,10,13,2,base);c.restore();
        }
        disc(c,0,0,radius*.70f,base);
        disc(c,0,0,radius*.45f,0xffd8f7ff);
        disc(c,0,0,radius*.28f,0xff2f6492);
        c.restoreToCount(save);
    }
    @Override public void setAlpha(int value){alpha=Math.max(0,Math.min(255,value));invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

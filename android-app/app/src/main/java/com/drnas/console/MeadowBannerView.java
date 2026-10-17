package com.drnas.console;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import java.util.Random;

/** Original XP-era meadow illustration. Procedural art, not Microsoft's wallpaper. */
public final class MeadowBannerView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public MeadowBannerView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    private void color(int color){p.reset();p.setAntiAlias(true);p.setColor(color);}
    private void oval(Canvas c,float a,float b,float x,float y,int co){
        color(co);c.drawOval(a,b,x,y,p);
    }
    private void cloud(Canvas c,float x,float y,float s){
        oval(c,x,y+10*s,x+120*s,y+34*s,0xEFFFFFFF);
        oval(c,x+18*s,y+2*s,x+66*s,y+35*s,0xFFF8FFFF);
        oval(c,x+47*s,y-12*s,x+94*s,y+36*s,0xFFFFFFFF);
        oval(c,x+77*s,y+4*s,x+116*s,y+33*s,0xFFE8F6FF);
    }
    private void hill(Canvas c,float y,int color,float wave){
        Path h=new Path();h.moveTo(0,240);h.lineTo(0,y);
        h.cubicTo(170,y-wave,310,y+wave,480,y-wave*.8f);
        h.cubicTo(660,y-1.6f*wave,770,y+wave*.2f,900,y-wave);
        h.lineTo(900,240);h.close();
        color(color);c.drawPath(h,p);
    }
    private void flower(Canvas c,float x,float y,float size){
        color(0xFF2C7D2E);p.setStrokeWidth(2);c.drawLine(x,y,x+2,y+9,p);
        for(int i=0;i<5;i++){
            double a=i*Math.PI*2/5;
            oval(c,x+(float)Math.cos(a)*size-size*.4f,y+(float)Math.sin(a)*size-size*.4f,
                    x+(float)Math.cos(a)*size+size*.4f,y+(float)Math.sin(a)*size+size*.4f,0xFFF5FFFF);
        }
        oval(c,x-size*.38f,y-size*.38f,x+size*.38f,y+size*.38f,0xFFFFCF29);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        int saved=canvas.save();
        Path clip=new Path();float rad=Math.min(w,h)*.1f;
        clip.addRoundRect(new RectF(0,0,w,h),rad,rad,Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.scale(w/900f,h/240f);
        color(0xFF4AAFFF);
        p.setShader(new LinearGradient(0,0,0,200,0xFF0572DF,0xFFABEDFF,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,900,240,p);p.setShader(null);
        oval(canvas,740,-88,940,115,0x35FFFFFF);
        oval(canvas,804,-20,861,37,0xFFFFEAA3);
        cloud(canvas,60,23,1.1f);cloud(canvas,320,30,.68f);cloud(canvas,570,55,.85f);
        // Mountains and trees mark the depth of the horizon.
        Path mountain=new Path();mountain.moveTo(580,168);
        mountain.lineTo(640,107);mountain.lineTo(680,141);
        mountain.lineTo(731,95);mountain.lineTo(802,157);
        mountain.lineTo(860,119);mountain.lineTo(900,167);mountain.close();
        color(0xFF4A9DA6);canvas.drawPath(mountain,p);
        hill(canvas,163,0xFF65C63B,19);
        hill(canvas,194,0xFF4BAC32,17);
        hill(canvas,224,0xFF318C2B,15);
        // Warm, winding trail.
        Path trail=new Path();trail.moveTo(880,176);
        trail.cubicTo(795,178,802,186,701,198);
        trail.cubicTo(628,205,683,215,635,240);
        color(0xFFE0BD6F);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(9);
        canvas.drawPath(trail,p);p.setStyle(Paint.Style.FILL);
        // A single tree with a visible trunk, blended green leaves.
        color(0xFF6E4529);
        Path trunk=new Path();trunk.moveTo(156,185);trunk.lineTo(164,114);
        trunk.lineTo(155,96);trunk.lineTo(170,111);
        trunk.lineTo(191,94);trunk.lineTo(172,131);
        trunk.lineTo(178,185);trunk.close();canvas.drawPath(trunk,p);
        oval(canvas,115,79,198,133,0xFF266E32);
        oval(canvas,132,59,220,122,0xFF3A9437);
        oval(canvas,94,88,166,145,0xFF2C7933);
        oval(canvas,162,89,243,145,0xFF2A812D);
        oval(canvas,117,59,188,107,0xFF5CB53B);
        oval(canvas,169,68,211,114,0xFF62B23D);
        // Deterministic flowers and highlights keep rendering stable and cheap.
        Random rand=new Random(41027);
        for(int i=0;i<95;i++) {
            float fx=rand.nextInt(900),fy=180+rand.nextInt(65);
            if(fy<190 && rand.nextBoolean())continue;
            if(i%3==0)flower(canvas,fx,fy,1.6f+rand.nextFloat()*1.6f);
            else {
                color(i%2==0?0xAAE9FFAD:0xAA8BE65A);
                canvas.drawCircle(fx,fy,1+rand.nextFloat()*1.8f,p);
            }
        }
        color(0xFF59EBDA);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);
        canvas.drawRoundRect(new RectF(2,2,898,238),19,19,p);
        canvas.restoreToCount(saved);
    }
}

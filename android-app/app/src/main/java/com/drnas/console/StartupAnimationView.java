package com.drnas.console;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Native startup animation; no images, I/O or background work. */
final class StartupAnimationView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RetroWindowsDrawable flag;
    private final long origin=SystemClock.uptimeMillis();
    private boolean running=true;
    StartupAnimationView(Context context){
        super(context);flag=new RetroWindowsDrawable(96);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void stop(){running=false;invalidate();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        paint.setShader(new LinearGradient(0,0,w,h,
                new int[]{0xff06121f,0xff163b47,0xff081629},null,Shader.TileMode.CLAMP));
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        c.drawRect(0,0,getWidth(),getHeight(),paint);
        float d=getResources().getDisplayMetrics().density;
        float t=(SystemClock.uptimeMillis()-origin)/1000f;
        Paint p=detail;
        p.setColor(0xff8cf5d0);
        for(int i=0;i<24;i++){
            float x=((i*73f+t*(5+i%4))%(getWidth()+20))-10;
            float y=(i*47f%Math.max(1,getHeight()))+(float)Math.sin(t*.7+i)*7*d;
            p.setAlpha(24+(i%4)*12);c.drawCircle(x,y,d*(.6f+i%3*.35f),p);
        }
        int size=(int)(72*d),x=(getWidth()-size)/2;
        int y=(int)(getHeight()*.29f+Math.sin(t*1.4)*3*d);
        flag.setBounds(x,y,x+size,y+size);flag.draw(c);
        p.setAlpha(255);p.setColor(0xff244b56);
        float left=getWidth()*.36f,right=getWidth()*.64f,top=getHeight()*.68f;
        c.drawRoundRect(left,top,right,top+3*d,2*d,2*d,p);
        p.setColor(0xff7fe5c5);
        for(int i=0;i<3;i++){
            float v=(t*.32f+i*.13f)%1;
            c.drawCircle(left+(right-left)*v,top+1.5f*d,(3-i*.65f)*d,p);
        }
        if(running&&isShown())postInvalidateDelayed(40);
    }
    private final Paint detail=new Paint(Paint.ANTI_ALIAS_FLAG);
}

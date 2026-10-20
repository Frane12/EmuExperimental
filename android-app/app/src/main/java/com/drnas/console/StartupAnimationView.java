package com.drnas.console;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Native startup animation; no images, I/O or background work. */
final class StartupAnimationView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.drawable.Drawable flag;
    private final long origin=SystemClock.uptimeMillis();
    private boolean running=true;
    private final boolean minimalBlender;
    StartupAnimationView(Context context){
        this(context,false);
    }
    StartupAnimationView(Context context,boolean blender){
        super(context);
        minimalBlender=blender;
        flag=blender?context.getDrawable(R.drawable.ic_blender):new RetroWindowsDrawable(96);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Shared by initial setup and every subsequent Blender launch. */
    static android.widget.LinearLayout blenderPanel(Context context, android.widget.TextView status) {
        float density=context.getResources().getDisplayMetrics().density;
        android.widget.LinearLayout panel=new android.widget.LinearLayout(context);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER);
        panel.setBackgroundColor(0xff202024);
        android.widget.ImageView logo=new android.widget.ImageView(context);
        logo.setImageResource(R.drawable.ic_blender);
        int size=Math.round(100*density);
        panel.addView(logo,new android.widget.LinearLayout.LayoutParams(size,size));
        android.widget.TextView name=new android.widget.TextView(context);
        name.setText("Blender");name.setTextSize(25);name.setTextColor(0xffeeeeee);
        panel.addView(name);
        View dots=new View(context) {
            final Paint dotPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
            final long start=SystemClock.uptimeMillis();
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float d=getResources().getDisplayMetrics().density;
                float cx=getWidth()/2f,cy=getHeight()/2f;
                float phase=((SystemClock.uptimeMillis()-start)%1500L)/1500f;
                dotPaint.setColor(0xffeeeeee);
                for(int i=0;i<6;i++) {
                    float angle=(float)(2*Math.PI*(phase-i*0.085f)-Math.PI/2);
                    dotPaint.setAlpha(255-i*31);
                    canvas.drawCircle(cx+13f*d*(float)Math.cos(angle),
                            cy+13f*d*(float)Math.sin(angle),2.2f*d,dotPaint);
                }
                if(isShown())postInvalidateOnAnimation();
            }
            @Override protected void onWindowVisibilityChanged(int visibility) {
                super.onWindowVisibilityChanged(visibility);
                if(visibility==VISIBLE)invalidate();
            }
        };
        panel.addView(dots,new android.widget.LinearLayout.LayoutParams(
                Math.round(60*density),Math.round(52*density)));
        status.setTextSize(13);status.setTextColor(0xffaaaab2);
        status.setPadding(16,20,16,20);status.setGravity(android.view.Gravity.CENTER);
        panel.addView(status);
        return panel;
    }

    void stop(){running=false;invalidate();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        paint.setShader(new LinearGradient(0,0,w,h,
                new int[]{0xff06121f,0xff163b47,0xff081629},null,Shader.TileMode.CLAMP));
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        c.drawRect(0,0,getWidth(),getHeight(),paint);
        // Blender preview: only the startup label and real-milestone 0..100 bar
        // added by WineGuiActivity, not an animated desktop or technical steps.
        if(minimalBlender)return;
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


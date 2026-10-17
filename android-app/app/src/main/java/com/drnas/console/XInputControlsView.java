package com.drnas.console;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

/** On-screen Xbox-style touch controls. Two isolated panels leave X11 center
 * and native touch/mouse events untouched. Supports multi-touch per panel. */
public final class XInputControlsView extends View {
    private static final int STICK=-1;
    private static final int NONE=0;
    private final XInputSharedState state;
    private final boolean left;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseIntArray fingers=new SparseIntArray(8);
    private float stickX,stickY;

    public XInputControlsView(Context context,XInputSharedState state,boolean left) {
        super(context);this.state=state;this.left=left;
        setFocusable(false);setClickable(true);
        setContentDescription(left?"XInput left analog, dpad and left shoulder":
                                   "XInput right analog, ABXY, right shoulder and Start");
    }
    private float dimension(){return Math.min(getWidth(),getHeight());}
    private float sx(){return (left?.33f:.25f)*getWidth();}
    private float sy(){return .72f*getHeight();}
    private float radius(){return .235f*dimension();}
    private float d(float x,float y,float cx,float cy){
        return (float)Math.hypot(x-cx,y-cy);
    }
    private float bx(int bit) {
        float w=getWidth();
        if(left) {
            if(bit==XInputSharedState.LB)return .16f*w;
            if(bit==XInputSharedState.BACK)return .52f*w;
            if(bit==XInputSharedState.LTHUMB)return .75f*w;
            if(bit==0x10000)return .83f*w; // LT touch region
            if(bit==XInputSharedState.DPAD_UP||bit==XInputSharedState.DPAD_DOWN)return .76f*w;
            if(bit==XInputSharedState.DPAD_LEFT)return .62f*w;
            if(bit==XInputSharedState.DPAD_RIGHT)return .90f*w;
        } else {
            if(bit==XInputSharedState.START)return .31f*w;
            if(bit==XInputSharedState.RB)return .61f*w;
            if(bit==XInputSharedState.RTHUMB)return .09f*w;
            if(bit==0x20000)return .90f*w; // RT touch region
            if(bit==XInputSharedState.Y || bit==XInputSharedState.A)return .76f*w;
            if(bit==XInputSharedState.X)return .61f*w;
            if(bit==XInputSharedState.B)return .91f*w;
        }
        return .5f*w;
    }
    private float by(int bit) {
        float h=getHeight();
        if(left){
            if(bit==XInputSharedState.LB||bit==0x10000)return .17f*h;
            if(bit==XInputSharedState.BACK||bit==XInputSharedState.LTHUMB)return .36f*h;
            if(bit==XInputSharedState.DPAD_UP)return .59f*h;
            if(bit==XInputSharedState.DPAD_DOWN)return .86f*h;
            if(bit==XInputSharedState.DPAD_LEFT||bit==XInputSharedState.DPAD_RIGHT)return .725f*h;
        }else{
            if(bit==XInputSharedState.START||bit==XInputSharedState.RB||bit==0x20000)return .17f*h;
            if(bit==XInputSharedState.RTHUMB)return .37f*h;
            if(bit==XInputSharedState.Y)return .54f*h;
            if(bit==XInputSharedState.A)return .87f*h;
            if(bit==XInputSharedState.X||bit==XInputSharedState.B)return .70f*h;
        }
        return .5f*h;
    }
    private static final int[] LEFT_BTNS={
        XInputSharedState.LB,0x10000,XInputSharedState.BACK,XInputSharedState.LTHUMB,
        XInputSharedState.DPAD_UP,XInputSharedState.DPAD_LEFT,
        XInputSharedState.DPAD_RIGHT,XInputSharedState.DPAD_DOWN
    };
    private static final int[] RIGHT_BTNS={
        XInputSharedState.START,XInputSharedState.RB,0x20000,XInputSharedState.RTHUMB,
        XInputSharedState.Y,XInputSharedState.X,
        XInputSharedState.B,XInputSharedState.A
    };
    private int target(float x,float y) {
        // The two panels' controls have disjoint touch regions.
        if(d(x,y,sx(),sy())<radius()*1.06f)return STICK;
        int[] keys=left?LEFT_BTNS:RIGHT_BTNS;
        float threshold=.10f*dimension();
        int target=NONE;float nearest=Float.MAX_VALUE;
        for(int key:keys) {
            float delta=d(x,y,bx(key),by(key));
            if(delta<threshold && delta<nearest){target=key;nearest=delta;}
        }
        return target;
    }
    private void pointStick(float x,float y) {
        float r=radius();
        float dx=Math.max(-1f,Math.min(1f,(x-sx())/r));
        float dy=Math.max(-1f,Math.min(1f,(y-sy())/r));
        float norm=(float)Math.hypot(dx,dy);
        if(norm>1f){dx/=norm;dy/=norm;}
        stickX=dx;stickY=dy;
        if(left)state.setTouchLeft(dx,dy);
        else state.setTouchRight(dx,dy);
        postInvalidateOnAnimation();
    }
    private void pressed(int target,boolean down) {
        if(target==NONE)return;
        if(target==STICK) {
            if(!down){
                stickX=stickY=0f;
                if(left)state.setTouchLeft(0,0);
                else state.setTouchRight(0,0);
                postInvalidateOnAnimation();
            }
        }else if(target==0x10000)state.setTouchTrigger(true,down?255:0);
        else if(target==0x20000)state.setTouchTrigger(false,down?255:0);
        else state.setTouchButton(target,down);
    }
    public void release() {
        for(int i=0;i<fingers.size();i++)pressed(fingers.valueAt(i),false);
        fingers.clear();
        pressed(STICK,false);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action=event.getActionMasked();
        int index=event.getActionIndex();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
            int id=event.getPointerId(index);
            int choice=target(event.getX(index),event.getY(index));
            if(choice!=NONE) {
                fingers.put(id,choice);
                if(choice==STICK)pointStick(event.getX(index),event.getY(index));
                else pressed(choice,true);
            }
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE) {
            for(int i=0;i<event.getPointerCount();i++){
                int choice=fingers.get(event.getPointerId(i),NONE);
                if(choice==STICK)pointStick(event.getX(i),event.getY(i));
            }
            return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) {
            int id=event.getPointerId(index);
            int released=fingers.get(id,NONE);
            fingers.delete(id);
            // A second finger may still hold the same virtual button.
            // Do not emit a release until its last touch is lifted.
            boolean stillHeld=false;
            for(int i=0;i<fingers.size();i++) {
                if(fingers.valueAt(i)==released){stillHeld=true;break;}
            }
            if(!stillHeld)pressed(released,false);
            if(action==MotionEvent.ACTION_UP)performClick();
            return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){release();return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}

    private void circle(Canvas canvas,float x,float y,float r,String label) {
        p.setStyle(Paint.Style.FILL);p.setColor(0xA5133145);
        canvas.drawCircle(x,y,r,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);
        p.setColor(0xDF80D8F2);canvas.drawCircle(x,y,r,p);
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFFFFFFF);p.setTextSize(Math.max(11f,r*.84f));
        canvas.drawText(label,x,y+p.getTextSize()*.35f,p);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float r=radius();
        p.setColor(0x65152A3E);p.setStyle(Paint.Style.FILL);
        canvas.drawCircle(sx(),sy(),r*1.15f,p);
        p.setColor(0xAF72C4E5);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3f);
        canvas.drawCircle(sx(),sy(),r,p);
        circle(canvas,sx()+stickX*r,sy()+stickY*r,r*.35f,left?"L":"R");
        int[] btns=left?LEFT_BTNS:RIGHT_BTNS;
        String[] labels=left?new String[]{"LB","LT","BACK","L3","↑","←","→","↓"}:
                new String[]{"START","RB","RT","R3","Y","X","B","A"};
        for(int i=0;i<btns.length;i++)circle(canvas,bx(btns[i]),by(btns[i]),
                .092f*dimension(),labels[i]);
    }
}

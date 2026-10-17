package com.drnas.console;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Floating, touch-anywhere FPS camera controller.
 *
 * Unlike the previous X11 pointer delta mode, this feeds the *working*
 * XInput right thumbstick which Crysis recognizes. Any touch point becomes
 * a temporary thumbstick center; moving the finger steers the camera while
 * releasing immediately returns right analog to zero. No polling threads.
 *
 * Keep it below the existing controller buttons so left stick, A/B/X/Y and
 * top toolbar receive their touches without interception.
 */
final class RelativeLookOverlay extends View {
    private final XInputSharedState state;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int pointer=-1;
    private float centerX,centerY;
    private float dx,dy;
    private float sensitivity=1.5f;

    RelativeLookOverlay(Context context,XInputSharedState state) {
        super(context);
        this.state=state;
        density=getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(false);
        setContentDescription("Floating relative camera: touch and move anywhere to steer XInput right thumbstick");
        setWillNotDraw(false);
    }

    void setSensitivity(float amount) {
        sensitivity=Math.max(.5f,Math.min(3.5f,amount));
    }

    void release() {
        if(pointer==-1)return;
        pointer=-1;
        dx=dy=0;
        state.setTouchRight(0f,0f);
        postInvalidateOnAnimation();
    }

    private void move(float x,float y) {
        float radius=65f*density;
        dx=Math.max(-1f,Math.min(1f,((x-centerX)/radius)*sensitivity));
        dy=Math.max(-1f,Math.min(1f,((y-centerY)/radius)*sensitivity));
        float length=(float)Math.hypot(dx,dy);
        if(length>1f){dx/=length;dy/=length;}
        state.setTouchRight(dx,dy);
        postInvalidateOnAnimation();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                pointer=event.getPointerId(0);
                centerX=event.getX(0);
                centerY=event.getY(0);
                dx=dy=0f;
                state.setTouchRight(0f,0f);
                postInvalidateOnAnimation();
                return true;
            case MotionEvent.ACTION_MOVE:
                if(pointer!=-1) {
                    int index=event.findPointerIndex(pointer);
                    if(index>=0)move(event.getX(index),event.getY(index));
                    else release();
                }
                return true;
            case MotionEvent.ACTION_UP:
                release();performClick();return true;
            case MotionEvent.ACTION_CANCEL:
                release();return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(pointer==-1)return;
        float radius=65f*density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x351d4c66);
        canvas.drawCircle(centerX,centerY,radius,paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f*density);
        paint.setColor(0x8c65d9f3);
        canvas.drawCircle(centerX,centerY,radius,paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xa962dced);
        canvas.drawCircle(centerX+dx*radius,centerY+dy*radius,14f*density,paint);
    }
}

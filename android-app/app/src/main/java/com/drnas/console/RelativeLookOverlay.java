package com.drnas.console;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

/**
 * Invisible, touch-anywhere relative camera control via already-working XInput.
 * Axis signal follows FINGER DELTA (direction/velocity), not displacement
 * from first touch. A deferred neutral release prevents a stuck camera.
 *
 * Relative XInput emulation is intentionally not a Windows RawInput mouse:
 * it preserves current Crysis gamepad mappings and supports a separate
 * left joystick and A/B/X/Y overlays.
 */
final class RelativeLookOverlay extends View {
    private final XInputSharedState state;
    private final float density;
    private int pointer=-1;
    private float previousX,previousY;
    private float sensitivity=1.45f;
    private int neutralGeneration;
    private final Runnable neutralize=()->state.setTouchRight(0f,0f);

    RelativeLookOverlay(Context context,XInputSharedState state) {
        super(context);
        this.state=state;
        density=getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(false);
        setWillNotDraw(true);
        setContentDescription("Invisible relative camera: slide finger anywhere");
    }
    void setSensitivity(float value){sensitivity=Math.max(.45f,Math.min(3.5f,value));}
    void release(){
        removeCallbacks(neutralize);
        pointer=-1;
        state.setTouchRight(0f,0f);
    }
    private void motion(float x,float y){
        float dx=x-previousX,dy=y-previousY;
        previousX=x;previousY=y;
        if(Math.abs(dx)<.05f && Math.abs(dy)<.05f)return;
        // A finger traveling one centimeter per ~20 ms generates sensible
        // analog deflection regardless of where its swipe began. Unlike the
        // previous absolute radius rule, moving in a given direction always
        // steers that way and releasing/motion stopping returns to neutral.
        float scale=Math.max(12f*density,1f);
        float vx=Math.max(-1f,Math.min(1f,dx/scale*sensitivity));
        float vy=Math.max(-1f,Math.min(1f,dy/scale*sensitivity));
        float magnitude=(float)Math.hypot(vx,vy);
        if(magnitude>1f){vx/=magnitude;vy/=magnitude;}
        state.setTouchRight(vx,vy);
        removeCallbacks(neutralize);
        postDelayed(neutralize,68L);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        switch(action){
            case MotionEvent.ACTION_DOWN:
                release();
                pointer=event.getPointerId(0);
                previousX=event.getX(0);
                previousY=event.getY(0);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(pointer==-1)return true;
                int index=event.findPointerIndex(pointer);
                if(index<0){release();return true;}
                // Use latest point; Android batches coalesced touch history
                // and the next OS sample supersedes older deltas.
                motion(event.getX(index),event.getY(index));
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if(pointer==event.getPointerId(event.getActionIndex()))release();
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
}

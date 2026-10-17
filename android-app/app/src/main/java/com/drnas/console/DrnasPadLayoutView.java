package com.drnas.console;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.SparseIntArray;
import android.util.SparseArray;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;

/** Runtime pad and drag-to-place editor share identical coordinate geometry. */
final class DrnasPadLayoutView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final XInputSharedState state; // null in offline editor
    private XInputLayoutPreset layout;
    private final boolean editing;
    private boolean cameraEnabled;
    private float cameraGain=3f;
    private final SparseIntArray touches=new SparseIntArray();
    private final SparseArray<PointF> cameraFingers=new SparseArray<>();
    private final Runnable neutralRight;
    private boolean buttonsVisible;
    private int dragged=-1;
    private float heldLX,heldLY,heldRX,heldRY;
    private final float density;
    private static final int CAMERA=-2;
    private static final int NONE=-1;
    private static final int[] BITS={
        0,0,XInputSharedState.LB,0,XInputSharedState.BACK,XInputSharedState.LTHUMB,
        XInputSharedState.DPAD_UP,XInputSharedState.DPAD_LEFT,
        XInputSharedState.DPAD_RIGHT,XInputSharedState.DPAD_DOWN,
        XInputSharedState.START,XInputSharedState.RB,0,XInputSharedState.RTHUMB,
        XInputSharedState.Y,XInputSharedState.X,XInputSharedState.B,XInputSharedState.A
    };
    DrnasPadLayoutView(Context ctx,XInputSharedState state,boolean editing) {
        super(ctx);this.state=state;this.editing=editing;
        neutralRight=()->{
            if(this.state!=null && touches.indexOfValue(XInputLayoutPreset.R)<0)
                this.state.setTouchRight(0f,0f);
        };
        density=getResources().getDisplayMetrics().density;
        setFocusable(false);
        setWillNotDraw(false);
        setClickable(editing);
        setContentDescription(editing?"Povuci tipke i analogne gljivice na željeno mjesto":"XInput prilagodljive kontrole");
    }
    void setPreset(XInputLayoutPreset preset){release();layout=preset;invalidate();}
    XInputLayoutPreset getPreset(){return layout;}
    void setCameraEnabled(boolean value){cameraEnabled=value;if(!value)stopCamera();}
    void setButtonsVisible(boolean value){if(!value)release();buttonsVisible=value;invalidate();}
    void setCameraGain(float value){cameraGain=Math.max(.5f,Math.min(10f,value));}
    private float px(int i){return getWidth()*layout.x[i];}
    private float py(int i){return getHeight()*layout.y[i];}
    private float rad(int i){return (i==0||i==1?49f:19f)*density;}
    private float dist(int i,float x,float y){
        return (float)Math.hypot(px(i)-x,py(i)-y);
    }
    private int hit(float x,float y){
        if(layout==null || (!editing && !buttonsVisible))return NONE;
        int best=NONE;float nearest=Float.MAX_VALUE;
        for(int i=0;i<XInputLayoutPreset.NAMES.length;i++){
            float d=dist(i,x,y);
            float threshold=rad(i)*(editing?1.25f:1.1f);
            if(d<threshold && d<nearest){best=i;nearest=d;}
        }
        return best;
    }
    private void releaseControl(int choice){
        if(state==null || choice<0)return;
        if(choice==0){state.setTouchLeft(0,0);heldLX=heldLY=0;}
        else if(choice==1){state.setTouchRight(0,0);heldRX=heldRY=0;}
        else if(choice==3)state.setTouchTrigger(true,0);
        else if(choice==12)state.setTouchTrigger(false,0);
        else if(BITS[choice]!=0)state.setTouchButton(BITS[choice],false);
    }
    private void pressControl(int choice) {
        if(state==null || choice<0)return;
        if(choice==3)state.setTouchTrigger(true,255);
        else if(choice==12)state.setTouchTrigger(false,255);
        else if(BITS[choice]!=0)state.setTouchButton(BITS[choice],true);
    }
    private void stick(int choice,float x,float y){
        if(state==null || choice>1)return;
        float r=49f*density;
        float ax=Math.max(-1f,Math.min(1f,(x-px(choice))/r));
        float ay=Math.max(-1f,Math.min(1f,(y-py(choice))/r));
        float norm=(float)Math.hypot(ax,ay);
        if(norm>1){ax/=norm;ay/=norm;}
        if(choice==0){heldLX=ax;heldLY=ay;state.setTouchLeft(ax,ay);}
        else {heldRX=ax;heldRY=ay;state.setTouchRight(ax,ay);}
        invalidate();
    }
    private void stopCamera(){
        removeCallbacks(neutralRight);
        cameraFingers.clear();
        if(state!=null && touches.indexOfValue(1)<0)state.setTouchRight(0,0);
    }
    void release(){
        for(int i=0;i<touches.size();i++)releaseControl(touches.valueAt(i));
        touches.clear();dragged=-1;stopCamera();invalidate();
    }
    private void camera(int pointer,float x,float y) {
        if(state==null||!cameraEnabled)return;
        PointF previous=cameraFingers.get(pointer);
        if(previous==null){cameraFingers.put(pointer,new PointF(x,y));return;}
        float dx=x-previous.x,dy=y-previous.y;
        previous.set(x,y);
        if(Math.abs(dx)<.02f && Math.abs(dy)<.02f)return;
        // Finger DELTAS, never distance from the beginning of swipe.
        float denominator=12f*density;
        float ax=Math.max(-1f,Math.min(1f,dx/denominator*cameraGain));
        float ay=Math.max(-1f,Math.min(1f,dy/denominator*cameraGain));
        state.setTouchRight(ax,ay);
        removeCallbacks(neutralRight);
        postDelayed(neutralRight,60);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(layout==null)return false;
        int action=event.getActionMasked(),i=event.getActionIndex();
        if(editing){
            if(action==MotionEvent.ACTION_DOWN){
                dragged=hit(event.getX(),event.getY());return true;
            }
            if(action==MotionEvent.ACTION_MOVE && dragged>=0){
                layout.set(dragged,event.getX()/getWidth(),event.getY()/getHeight());
                invalidate();return true;
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                dragged=-1;performClick();return true;
            }
            return true;
        }
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            int id=event.getPointerId(i);
            int target=hit(event.getX(i),event.getY(i));
            if(target==NONE && !cameraEnabled && action==MotionEvent.ACTION_DOWN)
                return false; // allow normal X11 pointer to own empty space
            if(target==NONE)target=CAMERA;
            touches.put(id,target);
            if(target>=0){
                if(target<=1)stick(target,event.getX(i),event.getY(i));
                else pressControl(target);
            } else cameraFingers.put(id,new PointF(event.getX(i),event.getY(i)));
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            for(int j=0;j<event.getPointerCount();j++){
                int id=event.getPointerId(j);
                int target=touches.get(id,NONE);
                if(target==0||target==1)stick(target,event.getX(j),event.getY(j));
                else if(target==CAMERA && touches.indexOfValue(1)<0)
                    camera(id,event.getX(j),event.getY(j));
            }
            return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_UP){
            int id=event.getPointerId(i),choice=touches.get(id,NONE);
            touches.delete(id);cameraFingers.remove(id);
            // Retain overlapping presses held by another touch pointer.
            if(choice>=0 && touches.indexOfValue(choice)<0)releaseControl(choice);
            if(choice==CAMERA && cameraFingers.size()==0 && touches.indexOfValue(1)<0){
                removeCallbacks(neutralRight);state.setTouchRight(0,0);
            }
            if(action==MotionEvent.ACTION_UP)performClick();
            return true;
        }
        if(action==MotionEvent.ACTION_CANCEL){release();return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}
    private void control(Canvas c,int index){
        float x=px(index),y=py(index),r=rad(index);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0x95112639);
        c.drawCircle(x,y,r,paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f*density);paint.setColor(index==dragged?0xffa5f3cf:0xbb82d5f0);
        c.drawCircle(x,y,r,paint);
        if(index<2){
            float ax=index==0?heldLX:heldRX,ay=index==0?heldLY:heldRY;
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xcc30617a);
            c.drawCircle(x+ax*r,y+ay*r,r*.32f,paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize((index<=1?17f:11f)*density);
        paint.setColor(0xffeffaff);
        c.drawText(XInputLayoutPreset.NAMES[index],x,y+paint.getTextSize()*.35f,paint);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(layout==null || (!editing && !buttonsVisible))return;
        for(int i=0;i<XInputLayoutPreset.NAMES.length;i++)control(canvas,i);
    }
}

package com.drnas.console;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import au.com.darkside.xserver.DrnasScreenTransform;
import android.view.View;

/**
 * Android GPU-composited cursor for the legacy X11 framebuffer. The only
 * invalidated layer during pointer movement is this tiny drawing workload;
 * the Wine desktop is not re-rasterized for every motion event.
 *
 * This is Android HWUI composition, NOT X11 DRI3/Present or Vulkan WSI.
 */
final class CursorOverlayView extends View {
    private static final class Frame {
        final Bitmap bitmap;
        final int x,y,hotX,hotY;
        Frame(Bitmap bitmap,int x,int y,int hotX,int hotY) {
            this.bitmap=bitmap;this.x=x;this.y=y;this.hotX=hotX;this.hotY=hotY;
        }
    }
    private volatile Frame frame;
    private final int guestWidth, guestHeight;
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF destination=new android.graphics.RectF();
    private volatile boolean stretchFullscreen=true;
    private volatile DrnasScreenTransform cachedTransform;
    void setStretchFullscreen(boolean enabled){
        stretchFullscreen=enabled;
        refreshTransform();
        postInvalidateOnAnimation();
    }
    private void refreshTransform(){
        cachedTransform=DrnasScreenTransform.forSize(getWidth(),getHeight(),
                guestWidth,guestHeight,stretchFullscreen);
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        super.onSizeChanged(w,h,oldW,oldH);
        refreshTransform();
    }

    CursorOverlayView(Context context,int guestWidth,int guestHeight) {
        super(context);
        this.guestWidth=guestWidth;
        this.guestHeight=guestHeight;
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_NONE,null);
    }

    void setCursor(Bitmap bitmap,int x,int y,int hotspotX,int hotspotY) {
        frame=new Frame(bitmap,x,y,hotspotX,hotspotY);
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Frame current=frame;
        if(current==null || current.bitmap==null || current.bitmap.isRecycled())return;
        DrnasScreenTransform t=cachedTransform;
        if(t==null){refreshTransform();t=cachedTransform;}
        int w=current.bitmap.getWidth(),h=current.bitmap.getHeight();
        // Small pointer/resize/text cursors have a constant physical size, even
        // when the desktop is stretched. Keep large brush-radius cursors intact.
        if(w<=64 && h<=64) {
            float scale=CursorSizing.compactScale(w,h,getResources().getDisplayMetrics().density);
            float px=t.physicalX(current.x),py=t.physicalY(current.y);
            destination.set(CursorSizing.edge(px,0,current.hotX,scale),
                    CursorSizing.edge(py,0,current.hotY,scale),
                    CursorSizing.edge(px,w,current.hotX,scale),
                    CursorSizing.edge(py,h,current.hotY,scale));
            canvas.drawBitmap(current.bitmap,null,destination,paint);
        } else {
            int saved=canvas.save();
            canvas.translate(t.offsetX,t.offsetY);canvas.scale(t.scaleX,t.scaleY);
            canvas.drawBitmap(current.bitmap,current.x-current.hotX,current.y-current.hotY,paint);
            canvas.restoreToCount(saved);
        }
    }
}


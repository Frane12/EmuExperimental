package com.drnas.console;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
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
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);

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
        float scale=Math.min(getWidth()/(float)guestWidth,getHeight()/(float)guestHeight);
        if(scale<=0)return;
        int saved=canvas.save();
        canvas.translate((getWidth()-guestWidth*scale)*0.5f,
                         (getHeight()-guestHeight*scale)*0.5f);
        canvas.scale(scale,scale);
        canvas.drawBitmap(current.bitmap,current.x-current.hotX,
                          current.y-current.hotY,paint);
        canvas.restoreToCount(saved);
    }
}

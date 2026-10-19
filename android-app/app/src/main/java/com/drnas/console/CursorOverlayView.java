package com.drnas.console;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import java.util.function.Consumer;
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
    // Blender's 3D viewport can ask Wine for an intentionally transparent
    // X11 cursor. Our HWUI overlay is otherwise above every guest window:
    // when there are zero visible cursor pixels, show a small, contrasting
    // Android-side pointer for Blender only. This does not move X11 input.
    private boolean blenderVisibleCursor;
    private Bitmap fallbackArrow;
    private Bitmap lastAuditedBitmap;
    private boolean lastAuditedWasBlank;
    private int previousCursorState=-1;
    private int cursorStateLogs;
    private Consumer<String> diagnosticSink;
    void setBlenderVisibleCursor(boolean enabled) { blenderVisibleCursor=enabled; }
    void setDiagnosticSink(Consumer<String> sink) { diagnosticSink=sink; }

    private boolean transparentCursor(Bitmap bitmap) {
        if(bitmap==null)return true;
        if(bitmap==lastAuditedBitmap)return lastAuditedWasBlank;
        lastAuditedBitmap=bitmap;
        lastAuditedWasBlank=false;
        if(bitmap.isRecycled() || bitmap.getWidth()>64 || bitmap.getHeight()>64)
            return false;
        int w=bitmap.getWidth(),h=bitmap.getHeight();
        int[] pixels=new int[w*h];
        bitmap.getPixels(pixels,0,w,0,0,w,h);
        boolean empty=true;
        for(int pixel:pixels)if((pixel>>>24)!=0) {empty=false;break;}
        lastAuditedWasBlank=empty;
        return empty;
    }
    private Bitmap arrowForInvisibleCursor() {
        if(fallbackArrow!=null && !fallbackArrow.isRecycled())return fallbackArrow;
        Bitmap arrow=Bitmap.createBitmap(20,24,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(arrow);
        Path silhouette=new Path();
        silhouette.moveTo(1.5f,1.5f);
        silhouette.lineTo(1.5f,20f);
        silhouette.lineTo(6.5f,15.5f);
        silhouette.lineTo(10f,22f);
        silhouette.lineTo(13.2f,20.5f);
        silhouette.lineTo(9.5f,14f);
        silhouette.lineTo(18.5f,14f);
        silhouette.close();
        Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setStyle(Paint.Style.FILL);ink.setColor(0xff111923);
        canvas.drawPath(silhouette,ink);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(1.65f);
        ink.setStrokeJoin(Paint.Join.ROUND);ink.setColor(0xffffffff);
        canvas.drawPath(silhouette,ink);
        fallbackArrow=arrow;
        return arrow;
    }
    private final int guestWidth, guestHeight;
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF destination=new android.graphics.RectF();
    private volatile boolean stretchFullscreen=true;
    private volatile int hostCropTopGuestPx;
    private volatile DrnasScreenTransform cachedTransform;
    void setHostCropTopGuestPx(int top) {
        hostCropTopGuestPx=Math.max(0,Math.min(48,top));
        refreshTransform();
        postInvalidateOnAnimation();
    }
    void setStretchFullscreen(boolean enabled){
        stretchFullscreen=enabled;
        refreshTransform();
        postInvalidateOnAnimation();
    }
    private void refreshTransform(){
        cachedTransform=DrnasScreenTransform.forSize(getWidth(),getHeight(),
                guestWidth,guestHeight,stretchFullscreen,hostCropTopGuestPx);
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
        if(blenderVisibleCursor) {
            int state=bitmap==null?1:(transparentCursor(bitmap)?2:0);
            if(state!=previousCursorState) {
                previousCursorState=state;
                if(cursorStateLogs++<24 && diagnosticSink!=null)
                    diagnosticSink.accept("DRNAS_BLENDER_CURSOR source="+
                            (state==0?"visible":state==1?"null":"transparent")+
                            " overlayFallback="+(state!=0)+
                            " hotspotX="+hotspotX+" hotspotY="+hotspotY);
            }
            if(state!=0) {
                bitmap=arrowForInvisibleCursor();
                hotspotX=1;hotspotY=1;
            }
        }
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


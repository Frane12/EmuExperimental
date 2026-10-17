package com.drnas.console;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import java.io.IOException;
import java.io.InputStream;

/** Original r6 artwork with a quiet breeze and drifting pollen.
 * Fixed mesh and particles: no frame allocations, decoding, threads or I/O.
 * Only visible draws request another frame, at 25 FPS; no background timer.
 */
public final class MeadowBannerView extends View {
    private static final String ASSET="emudrnas-meadow-r6.webp";
    private static final int MESH_X=20, MESH_Y=8, POLLEN_COUNT=26;
    private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint borderPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pollenPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect source=new Rect();
    private final Rect visible=new Rect();
    private final RectF destination=new RectF();
    private final RectF border=new RectF();
    private final Path rounded=new Path();
    private final float[] vertices=new float[(MESH_X+1)*(MESH_Y+1)*2];
    private final Bitmap meadow;
    private final long origin=SystemClock.uptimeMillis();
    private float radius;

    public MeadowBannerView(Context context) {
        super(context);
        Bitmap decoded=null;
        try(InputStream input=context.getAssets().open(ASSET)) {
            decoded=BitmapFactory.decodeStream(input);
            if(decoded==null)Log.e("EmuDrnas-Meadow","Bundled meadow could not be decoded");
        } catch(IOException error) {
            Log.e("EmuDrnas-Meadow","Bundled meadow missing",error);
        }
        meadow=decoded;
        if(meadow!=null)source.set(0,0,meadow.getWidth(),meadow.getHeight());
        setContentDescription("Livada i stablo uz blag povjetarac i lepršanje peludi");
    }

    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        radius=Math.min(w,h)*.11f;
        destination.set(0,0,w,h);
        border.set(1,1,w-1,h-1);
        rounded.reset();
        rounded.addRoundRect(destination,radius,radius,Path.Direction.CW);
        fallbackPaint.setShader(new LinearGradient(0,0,0,Math.max(1,h),
                0xff087cde,0xff56cd55,Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float width=getWidth(),height=getHeight();
        if(width<2||height<2)return;
        // This decorative scene is explicitly enabled by the user; do not let
        // the system transition-duration scale freeze its Canvas frames.
        final boolean animate=isShown()&&getWindowVisibility()==VISIBLE;
        // 120 seconds is a seamless common period for breeze and pollen.
        final double t=animate?((SystemClock.uptimeMillis()-origin)%120000)/1000.0:0;
        int saved=canvas.save();
        canvas.clipPath(rounded);
        if(meadow!=null&&!meadow.isRecycled()) {
            if(animate) {
                int index=0;
                for(int y=0;y<=MESH_Y;y++) {
                    float fy=(float)y/MESH_Y;
                    for(int x=0;x<=MESH_X;x++) {
                        float fx=(float)x/MESH_X;
                        // Fix the image edges and trunk; let the canopy gently sway
                        // a few physical pixels, with a softer breeze in the grass.
                        double edge=Math.sin(Math.PI*fx)*Math.sin(Math.PI*fy);
                        double canopy=Math.exp(-18*(fx-.25)*(fx-.25)-12*(fy-.38)*(fy-.38));
                        double grass=fy>.68?Math.sin((fy-.68)/.32*Math.PI)*.20:0;
                        double breeze=Math.sin(t*Math.PI/6+fx*5+fy*2);
                        vertices[index++]=fx*width+(float)(edge*(canopy+grass)*breeze*height*.022);
                        vertices[index++]=fy*height+(float)(edge*canopy*Math.sin(t*Math.PI/10+fx*4)*height*.006);
                    }
                }
                canvas.drawBitmapMesh(meadow,MESH_X,MESH_Y,vertices,0,null,0,bitmapPaint);
            } else canvas.drawBitmap(meadow,source,destination,bitmapPaint);
        } else canvas.drawRect(destination,fallbackPaint);
        if(animate) {
            for(int i=0;i<POLLEN_COUNT;i++) {
                double cycle=(t/20.0+i*.61803398875)%1;
                float x=(float)(cycle*width);
                float base=(float)((i*.38196601125)%1);
                float y=height*(.14f+base*.72f)+(float)Math.sin(t*Math.PI/6+i)*height*.028f;
                float fade=(float)Math.min(1,Math.min(cycle,1-cycle)*9);
                pollenPaint.setColor(0xfffff6d5);
                pollenPaint.setAlpha((int)((120+(i%4)*25)*fade));
                float r=height*(.006f+(i%3)*.0025f);
                canvas.drawCircle(x,y,Math.max(.45f,r),pollenPaint);
            }
        }
        canvas.restoreToCount(saved);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(Math.max(1f,getResources().getDisplayMetrics().density));
        borderPaint.setColor(0xff4ae9f5);
        canvas.drawRoundRect(border,radius,radius,borderPaint);
        if(animate&&isShown()&&getWindowVisibility()==VISIBLE&&getLocalVisibleRect(visible))
            postInvalidateDelayed(40);
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if(visibility==VISIBLE)invalidate();
    }
    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if(visible)invalidate();
    }
}

package au.com.darkside.xserver;

/**
 * Physical Android View <-> fixed-size X11 guest transform.
 *
 * FILL: independently scale X and Y to remove aspect-ratio black bars while
 * keeping the X11/Wine desktop unchanged (e.g. 1280x720).
 * FIT: uniform scale with traditional letterbox/pillarbox.
 *
 * This pure geometric value type is shared by desktop draw and input mapping.
 * Cursor overlay uses the same equations. No new framebuffer allocation,
 * screenshot, GPU readback or additional frame copy.
 */
public final class DrnasScreenTransform {
    public final float scaleX,scaleY,offsetX,offsetY;
    public final int guestW,guestH;
    public final boolean fill;

    private DrnasScreenTransform(float sx,float sy,float ox,float oy,
                                 int w,int h,boolean stretch) {
        scaleX=sx;scaleY=sy;offsetX=ox;offsetY=oy;
        guestW=w;guestH=h;fill=stretch;
    }
    public static DrnasScreenTransform forSize(int viewW,int viewH,
                                               int guestW,int guestH,boolean fill){
        return forSize(viewW,viewH,guestW,guestH,fill,0);
    }
    // Android-only top-strip crop; X11 window geometry remains unchanged.
    // Input, compositor and cursor use the same transform.
    public static DrnasScreenTransform forSize(int viewW,int viewH,
                                               int guestW,int guestH,boolean fill,int cropTop){
        int safeCrop=fill?Math.max(0,Math.min(Math.max(0,guestH/10),cropTop)):0;
        if(viewW<=0||viewH<=0||guestW<=0||guestH<=0)
            return new DrnasScreenTransform(1f,1f,0f,0f,
                    Math.max(1,guestW),Math.max(1,guestH),fill);
        float sx=(float)viewW/guestW,sy=(float)viewH/(guestH-safeCrop);
        if(fill)return new DrnasScreenTransform(sx,sy,0f,-safeCrop*sy,guestW,guestH,true);
        float s=Math.min(sx,sy);
        return new DrnasScreenTransform(s,s,(viewW-guestW*s)*.5f,
                (viewH-guestH*s)*.5f,guestW,guestH,false);
    }
    public int guestX(float screenX){
        return Math.max(0,Math.min(guestW-1,Math.round((screenX-offsetX)/scaleX)));
    }
    public int guestY(float screenY){
        return Math.max(0,Math.min(guestH-1,Math.round((screenY-offsetY)/scaleY)));
    }
    public float deltaX(float physicalDelta){return physicalDelta/scaleX;}
    public float deltaY(float physicalDelta){return physicalDelta/scaleY;}
    public float physicalX(float x){return offsetX+x*scaleX;}
    public float physicalY(float y){return offsetY+y*scaleY;}
}

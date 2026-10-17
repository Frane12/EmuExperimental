package au.com.darkside.xserver;

/**
 * Experimental WSI compositor presentation *mask*, not a framebuffer clear.
 * Candidate: repeated full-width opaque X11 PutImage operations whose height
 * is 1..32 pixels below the X11 root height (observed 1280x707 vs 1280x720).
 *
 * Applies only as the LAST Android Canvas composition. Never edits the X11
 * drawable, flushes its content, or interferes with subsequent partial damage.
 * This is not proof the bottom artifact originates in these missing rows.
 */
final class X11PresentedFrameMask {
    private int recentHeight,repeat;
    private volatile int rows;
    private volatile long until;
    void report(int width,int height,int x,int y,int rootW,int rootH,boolean opaque,long now){
        if(!opaque||x!=0||y!=0||width!=rootW||height>=rootH||
                height<rootH-32||height<rootH-32||rootH<=0){
            return;
        }
        if(height!=recentHeight){recentHeight=height;repeat=1;}
        else repeat=Math.min(32,repeat+1);
        if(repeat>=8){
            rows=rootH-height;
            until=now+2_000_000_000L;
        }
    }
    int visibleRows(long now){return now<until?rows:0;}
    void clear(){rows=0;repeat=0;recentHeight=0;until=0;}
}

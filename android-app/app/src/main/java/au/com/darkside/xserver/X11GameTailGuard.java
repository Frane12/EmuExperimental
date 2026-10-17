package au.com.darkside.xserver;

/**
 * Root compositor guard for the case reported in issue #10: a fast 1280x707
 * opaque game window on a 1280x720 virtual desktop can leave 13 stale rows
 * OUTSIDE the game's own drawable. Clearing only drawable tails does nothing.
 *
 * Activation requires repeated observed full-window PutImage frames. It never
 * modifies guest pixels, only blackens the uncovered root compositing stripe.
 * A timed expiry ensures the normal desktop comes back after the game stops.
 */
final class X11GameTailGuard {
    private int candidateHeight;
    private int matches;
    private volatile int gap;
    private volatile long expiresAtNanos;

    static int candidateGap(int srcW,int srcH,int targetW,int targetH,
                            int rootW,int rootH,int dstX,int dstY,
                            boolean opaqueCopy) {
        if(!opaqueCopy || dstX!=0 || dstY!=0 || srcW!=rootW ||
           srcH!=targetH || targetW!=rootW ||
           rootH<=targetH || rootH-targetH>32 || rootH-targetH<1)
            return 0;
        return rootH-targetH;
    }
    synchronized void observe(int proposedGap,int height,long nowNanos) {
        if(proposedGap<=0){
            matches=0;
            candidateHeight=0;
            return;
        }
        if(height==candidateHeight)matches++;
        else {candidateHeight=height;matches=1;}
        if(matches>=6) {
            gap=proposedGap;
            expiresAtNanos=nowNanos+3_000_000_000L;
            if(matches>6)matches=6;
        }
    }
    int visibleGap(long nowNanos) {
        return nowNanos<expiresAtNanos ? gap:0;
    }
    void reset() {
        gap=0;expiresAtNanos=0L;matches=0;candidateHeight=0;
    }
}

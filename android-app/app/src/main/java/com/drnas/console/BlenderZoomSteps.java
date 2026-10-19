package com.drnas.console;

/** Fixed-tick, bounded seekbar movement -> X11 scroll wheel pulses.
 * A user-dragged progress value changes zoom; programmatic recenter is silent.
 */
final class BlenderZoomSteps {
    static final int CENTER=50;
    private static final int STEP=5;
    private int last=CENTER;
    void reset(int position){last=Math.max(0,Math.min(100,position));}
    int update(int position){
        int delta=Math.max(0,Math.min(100,position))-last;
        int steps=delta/STEP;
        // Keep each UI callback bounded even on huge slider jumps.
        steps=Math.max(-10,Math.min(10,steps));
        if(steps!=0)last+=steps*STEP;
        return steps;
    }
}

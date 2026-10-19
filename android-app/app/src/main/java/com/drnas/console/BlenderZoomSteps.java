package com.drnas.console;

/** Raw signed user-slider deltas, delivered to Blender for sub-wheel precision.
 * Programmatic spring recenter is never forwarded as a user zoom.
 */
final class BlenderZoomSteps {
    static final int CENTER=50;
    private int last=CENTER;
    void reset(int position){last=Math.max(0,Math.min(100,position));}
    int update(int position) {
        int bounded=Math.max(0,Math.min(100,position));
        int delta=bounded-last;
        last=bounded;
        return delta;
    }
}

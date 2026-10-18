package com.drnas.console;

/** Cursor geometry in physical pixels; never moves the X11 pointer hotspot. */
final class CursorSizing {
    static float compactScale(int width,int height,float density) {
        if(width<=0||height<=0)return 1;
        float target=Math.max(16,Math.min(28,14*density));
        return target/Math.max(width,height);
    }
    static float edge(float pointer,int edge,int hotspot,float scale) {
        return pointer+(edge-hotspot)*scale;
    }
    private CursorSizing(){}
}

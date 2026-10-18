package au.com.darkside.xserver;

import java.nio.IntBuffer;

/** Bulk decoder for the X11 server's advertised little-endian ZPixmap/XRGB8888.
 * Pure Java so pixel correctness can be regression-tested without Android UI.
 */
public final class X11PixelCodec {
    private X11PixelCodec() {}

    /**
     * Experimental Java bulk-copy decoder: one int buffer transfer and one
     * alpha fixup. The caller caches an LITTLE_ENDIAN IntBuffer view of the
     * current X11 BGRX byte-array, avoiding per-frame object creation.
     * Expected output matches decodeBgrxToArgb() byte-for-byte.
     */
    public static void decodeBgrxBulk(IntBuffer words,int[] destination,int pixels){
        if(pixels<0||pixels>destination.length||words.capacity()<pixels)
            throw new IllegalArgumentException("Invalid X11 bulk pixel buffer");
        words.position(0);
        words.get(destination,0,pixels);
        for(int i=0;i<pixels;i++)destination[i]|=0xff000000;
    }

    /** Input is B,G,R,unused byte per pixel; output is Android ARGB_8888 int. */
    public static void decodeBgrxToArgb(byte[] source, int[] destination, int pixels) {
        decodeBgrxToArgb(source, 0, destination, pixels);
    }

    public static void decodeBgrxToArgb(byte[] source, int offset, int[] destination, int pixels) {
        if (pixels < 0 || offset < 0 || (long)offset + (long)pixels * 4 > source.length || pixels > destination.length)
            throw new IllegalArgumentException("Invalid X11 pixel buffer");
        for (int i = 0; i < pixels; i++, offset += 4) {
            destination[i] = 0xff000000
                    | ((source[offset + 2] & 0xff) << 16)
                    | ((source[offset + 1] & 0xff) << 8)
                    | (source[offset] & 0xff);
        }
    }
}


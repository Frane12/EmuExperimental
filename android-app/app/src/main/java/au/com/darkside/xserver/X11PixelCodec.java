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
        decodeBgrxBulk(words,0,destination,pixels);
    }
    public static void decodeBgrxBulk(IntBuffer words,int wordOffset,int[] destination,int pixels){
        if(wordOffset<0 || pixels<0 || pixels>destination.length ||
                (long)wordOffset+pixels>words.limit())
            throw new IllegalArgumentException("Invalid X11 bulk pixel buffer");
        words.position(wordOffset);
        words.get(destination,0,pixels);
        for(int i=0;i<pixels;i++)destination[i]|=0xff000000;
    }

    /** Input is B,G,R,unused byte per pixel; output is Android ARGB_8888 int. */
    public static void decodeBgrxToArgb(byte[] source, int[] destination, int pixels) {
        decodeBgrxToArgb(source,0,destination,pixels);
    }
    public static void decodeBgrxToArgb(byte[] source,int sourceOffset,int[] destination,int pixels) {
        if (sourceOffset<0 || pixels < 0 || (long)sourceOffset+(long)pixels * 4 > source.length || pixels > destination.length)
            throw new IllegalArgumentException("Invalid X11 pixel buffer");
        for (int i = 0, offset = sourceOffset; i < pixels; i++, offset += 4) {
            destination[i] = 0xff000000
                    | ((source[offset + 2] & 0xff) << 16)
                    | ((source[offset + 1] & 0xff) << 8)
                    | (source[offset] & 0xff);
        }
    }
}


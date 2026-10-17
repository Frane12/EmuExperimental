package au.com.darkside.xserver;

/** Bulk decoder for the X11 server's advertised little-endian ZPixmap/XRGB8888.
 * Pure Java so pixel correctness can be regression-tested without Android UI.
 */
public final class X11PixelCodec {
    private X11PixelCodec() {}

    /** Input is B,G,R,unused byte per pixel; output is Android ARGB_8888 int. */
    public static void decodeBgrxToArgb(byte[] source, int[] destination, int pixels) {
        if (pixels < 0 || (long)pixels * 4 > source.length || pixels > destination.length)
            throw new IllegalArgumentException("Invalid X11 pixel buffer");
        for (int i = 0, offset = 0; i < pixels; i++, offset += 4) {
            destination[i] = 0xff000000
                    | ((source[offset + 2] & 0xff) << 16)
                    | ((source[offset + 1] & 0xff) << 8)
                    | (source[offset] & 0xff);
        }
    }
}

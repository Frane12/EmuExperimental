package au.com.darkside.xserver;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Single reader, bounded request storage. Never shared across client threads. */
final class X11RequestBuffer extends InputStream {
    private byte[] bytes = new byte[0];
    private int position, length;

    int capacity() { return bytes.length; }

    void receive(InputStream source, int size) throws IOException {
        if (size < 0 || size > InputOutput.MAX_REQUEST_WORDS * 4 - 4)
            throw new IOException("Invalid X11 request body length");
        position = length = 0;
        if (bytes.length < size) bytes = new byte[size];
        int offset = 0;
        while (offset < size) {
            int n = source.read(bytes, offset, size - offset);
            if (n < 0) throw new EOFException("Truncated X11 request body");
            if (n == 0) {
                int value = source.read();
                if (value < 0) throw new EOFException("Truncated X11 request body");
                bytes[offset++] = (byte)value;
            } else offset += n;
        }
        length = size;
    }

    @Override public int read() { return position < length ? bytes[position++] & 255 : -1; }

    @Override public int read(byte[] target, int offset, int size) {
        if (offset < 0 || size < 0 || offset > target.length - size)
            throw new IndexOutOfBoundsException();
        if (size == 0) return 0;
        if (position == length) return -1;
        int n = Math.min(size, length - position);
        System.arraycopy(bytes, position, target, offset, n);
        position += n;
        return n;
    }

    @Override public long skip(long size) {
        int n = (int)Math.min(Math.max(0L, size), length - position);
        position += n;
        return n;
    }

    @Override public int available() { return length - position; }

    void readBgrxColors(int[] colors, int pixels, boolean bulk) throws IOException {
        if (pixels < 0 || pixels > colors.length || (long)pixels * 4 > available())
            throw new EOFException("BGRX exceeds request boundary");
        if (bulk) {
            X11PixelCodec.decodeBgrxBulk(ByteBuffer.wrap(bytes, position, pixels * 4)
                    .slice().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer(), colors, pixels);
        } else X11PixelCodec.decodeBgrxToArgb(bytes, position, colors, pixels);
        position += pixels * 4;
    }
}

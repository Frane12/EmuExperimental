package au.com.darkside.xserver;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/** One client-owned request, fully received before taking the X server lock.
 * Reuse ordinary frames, but never retain an exceptional >4 MiB body.
 * No references to this storage escape to a drawable or Android renderer.
 */
final class X11RequestBuffer extends ByteArrayInputStream {
    static final int RETAIN_LIMIT=4*1024*1024;
    private static final byte[] EMPTY=new byte[0];
    private IntBuffer words;

    X11RequestBuffer(){super(EMPTY);}
    void receive(InputStream input,int length,int maximum) throws IOException {
        if(length<0 || length>maximum)throw new IOException("Invalid X11 request body length: "+length);
        pos=count=mark=0;
        if(buf.length<length) {
            int capacity=length>RETAIN_LIMIT?length:
                    Math.min(RETAIN_LIMIT,Math.max(4096,Integer.highestOneBit(length-1)<<1));
            buf=new byte[capacity];words=null;
        }
        int offset=0;
        while(offset<length) {
            int n=input.read(buf,offset,length-offset);
            if(n<0)throw new EOFException("Truncated X11 request body: "+offset+"/"+length);
            if(n==0) {
                int value=input.read();
                if(value<0)throw new EOFException("Truncated X11 request body");
                buf[offset++]=(byte)value;
            }else offset+=n;
        }
        count=length;
    }
    int finish() {
        int unread=count-pos;
        pos=count=mark=0;
        if(buf.length>RETAIN_LIMIT){buf=EMPTY;words=null;}
        return unread;
    }
    void decodeBgrx(int[] destination,int pixels,boolean bulk) throws IOException {
        long bytes=(long)pixels*4;
        if(pixels<0 || bytes>count-pos)throw new EOFException("X11 pixels exceed request boundary");
        if(bulk && (pos&3)==0) {
            if(words==null)words=ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
            X11PixelCodec.decodeBgrxBulk(words,pos/4,destination,pixels);
        }else X11PixelCodec.decodeBgrxToArgb(buf,pos,destination,pixels);
        pos+=(int)bytes;
    }
    int retainedCapacity(){return buf.length;}
}

package com.drnas.console;
import org.junit.Test;
import java.io.IOException;
import java.nio.*;
import java.util.*;
import static org.junit.Assert.*;

public class IcoImageTest {
    private byte[] wrap(int size,byte[] payload){
        ByteBuffer b=ByteBuffer.allocate(22+payload.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)0).putShort((short)1).putShort((short)1);
        b.put((byte)size).put((byte)size).put((byte)0).put((byte)0);
        b.putShort((short)1).putShort((short)32).putInt(payload.length).putInt(22).put(payload);
        return b.array();
    }
    private byte[] dib(int bpp,byte[] palette,byte[] rows,byte[] mask){
        ByteBuffer b=ByteBuffer.allocate(40+palette.length+rows.length+mask.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(40).putInt(2).putInt(4).putShort((short)1).putShort((short)bpp);
        b.putInt(0).putInt(rows.length).putInt(0).putInt(0).putInt(palette.length/4).putInt(0);
        b.put(palette).put(rows).put(mask);return wrap(2,b.array());
    }
    @Test public void legacy32BitUsesAndMaskAndCorrectBottomUpRows() throws Exception {
        byte[] rows={ (byte)255,0,0,0, (byte)255,(byte)255,(byte)255,0,
                0,0,(byte)255,0, 0,(byte)255,0,0 };
        byte[] mask={0,0,0,0,0x40,0,0,0};
        int[] px=IcoImage.read(dib(32,new byte[0],rows,mask)).get(0).argb;
        assertArrayEquals(new int[]{0xffff0000,0x0000ff00,0xff0000ff,0xffffffff},px);
    }
    @Test public void preservesPartialAlphaInsteadOfMakingEverythingOpaque() throws Exception {
        byte[] rows={ (byte)255,0,0,(byte)255, (byte)255,(byte)255,(byte)255,0,
                0,0,(byte)255,64, 0,(byte)255,0,(byte)128 };
        int[] px=IcoImage.read(dib(32,new byte[0],rows,new byte[8])).get(0).argb;
        assertEquals(0x40ff0000,px[0]);assertEquals(0x8000ff00,px[1]);assertEquals(0x00ffffff,px[3]);
    }
    @Test public void supportsIndexed8And4And1BitFrames() throws Exception {
        byte[] palette={0,0,0,0,0,0,(byte)255,0};
        byte[] rows8={1,0,0,0,0,1,0,0};
        byte[] rows4={0x10,0,0,0,0x01,0,0,0};
        byte[] rows1={(byte)0x80,0,0,0,0x40,0,0,0};
        int[] expected={0xff000000,0xffff0000,0xffff0000,0xff000000};
        assertArrayEquals(expected,IcoImage.read(dib(8,palette,rows8,new byte[8])).get(0).argb);
        assertArrayEquals(expected,IcoImage.read(dib(4,palette,rows4,new byte[8])).get(0).argb);
        assertArrayEquals(expected,IcoImage.read(dib(1,palette,rows1,new byte[8])).get(0).argb);
    }
    @Test public void extractsPngPayloadFromTheIcoContainer() throws Exception {
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jFZkAAAAASUVORK5CYII=");
        IcoImage.Frame f=IcoImage.read(wrap(1,png)).get(0);
        assertEquals(1,f.width);assertArrayEquals(png,f.png);assertNull(f.argb);
    }
    @Test public void rejectsTruncatedOffsetsAndNonIcoInput() {
        assertThrows(IOException.class,()->IcoImage.read(new byte[100]));
        byte[] broken=wrap(1,new byte[40]);broken[18]=(byte)255;
        assertThrows(IOException.class,()->IcoImage.read(broken));
        assertThrows(IOException.class,()->IcoImage.read(new byte[IcoImage.MAX_BYTES+1]));
    }
}

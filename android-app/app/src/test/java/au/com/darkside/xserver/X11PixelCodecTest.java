package au.com.darkside.xserver;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.Assert.*;

public class X11PixelCodecTest {
    @Test public void preservesChannelsAndForcesOpaqueAlpha() {
        byte[] raw = {0x11,0x22,0x33,0, (byte)0xff,0,0,0,
                0, (byte)0xff, 0, (byte)0xff, 0,0,(byte)0xff,0};
        int[] result = new int[4];
        X11PixelCodec.decodeBgrxToArgb(raw,result,4);
        assertArrayEquals(new int[]{0xff332211,0xff0000ff,0xff00ff00,0xffff0000},result);
    }
    @Test public void decodesOnlyRequestedPrefixAndReusesDestination() {
        byte[] raw = {1,2,3,4,5,6,7,8};
        int[] destination = {0,0,0x12345678};
        X11PixelCodec.decodeBgrxToArgb(raw,destination,2);
        assertArrayEquals(new int[]{0xff030201,0xff070605,0x12345678},destination);
    }
    @Test public void bulkPathEqualsScalarEvenWithRandomXAlphaAndPartialSizes(){
        java.util.Random rand=new java.util.Random(82L);
        for(int count:new int[]{1,19,480,707*1280}){
            byte[] raw=new byte[count*4];
            rand.nextBytes(raw);
            int[] baseline=new int[count],bulk=new int[count];
            X11PixelCodec.decodeBgrxToArgb(raw,baseline,count);
            X11PixelCodec.decodeBgrxBulk(ByteBuffer.wrap(raw)
                    .order(ByteOrder.LITTLE_ENDIAN).asIntBuffer(),bulk,count);
            assertArrayEquals(baseline,bulk);
        }
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsTruncatedPayload() {
        X11PixelCodec.decodeBgrxToArgb(new byte[3],new int[1],1);
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsNegativeSize() {
        X11PixelCodec.decodeBgrxToArgb(new byte[0],new int[1],-1);
    }
}

package au.com.darkside.xserver;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.Random;

public final class X11RequestBufferTest {
    @Test public void bulkViewTracksReusedAndGrownBackingStorage() throws Exception {
        X11RequestBuffer request=new X11RequestBuffer();
        for(int pixels:new int[]{8,2048,16,1280*720,3}) {
            byte[] raw=new byte[pixels*4];new Random(pixels).nextBytes(raw);
            request.receive(new ByteArrayInputStream(raw),raw.length,16*1024*1024);
            int[] expected=new int[pixels],actual=new int[pixels];
            X11PixelCodec.decodeBgrxToArgb(raw,expected,pixels);
            request.decodeBgrx(actual,pixels,true);
            assertArrayEquals(expected,actual);assertEquals(0,request.finish());
        }
    }
    @Test public void pixelsDecodeDirectlyAtBodyOffsetAndPreserveFollowingBytes() throws Exception {
        for(boolean bulk:new boolean[]{false,true})for(int offset:new int[]{0,3,20}) {
            byte[] raw=new byte[offset+1280*720*4+4];new Random(77).nextBytes(raw);
            X11RequestBuffer request=new X11RequestBuffer();
            request.receive(new ByteArrayInputStream(raw),raw.length,16*1024*1024);
            assertEquals(offset,request.skip(offset));
            int[] expected=new int[1280*720],actual=new int[expected.length];
            X11PixelCodec.decodeBgrxToArgb(raw,offset,expected,expected.length);
            request.decodeBgrx(actual,actual.length,bulk);
            assertArrayEquals(expected,actual);
            assertEquals(raw[raw.length-4]&255,request.read());
            assertEquals(3,request.finish());
        }
    }
    @Test public void reusedStorageNeverExposesStaleRequestTail() throws Exception {
        X11RequestBuffer request=new X11RequestBuffer();
        byte[] frame=new byte[1280*720*4];
        request.receive(new ByteArrayInputStream(frame),frame.length,16*1024*1024);
        request.finish();int capacity=request.retainedCapacity();
        assertTrue(capacity>=frame.length);assertTrue(capacity<=X11RequestBuffer.RETAIN_LIMIT);
        request.receive(new ByteArrayInputStream(new byte[]{17}),1,16*1024*1024);
        assertEquals(capacity,request.retainedCapacity());
        assertEquals(17,request.read());assertEquals(-1,request.read());
        try {request.decodeBgrx(new int[1],1,false);fail("crossed boundary");}
        catch(EOFException expected){}
        assertEquals(0,request.finish());
    }
    @Test public void oversizedRequestStorageIsReleasedAndLimitsAreEnforced() throws Exception {
        X11RequestBuffer request=new X11RequestBuffer();
        byte[] body=new byte[X11RequestBuffer.RETAIN_LIMIT+1];
        request.receive(new ByteArrayInputStream(body),body.length,16*1024*1024);
        request.finish();assertEquals(0,request.retainedCapacity());
        for(int bad:new int[]{-1,17*1024*1024}) {
            try {request.receive(new ByteArrayInputStream(new byte[0]),bad,16*1024*1024);fail();}
            catch(IOException expected){}
        }
    }
    @Test public void truncatedReceiveCannotPublishPartialOrStalePixels() throws Exception {
        X11RequestBuffer request=new X11RequestBuffer();
        try {request.receive(new ByteArrayInputStream(new byte[]{1}),4,16);fail();}
        catch(EOFException expected){}
        assertEquals(-1,request.read());assertEquals(0,request.finish());
    }
}

package au.com.darkside.xserver;

import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

public class X11RequestBufferTest {
    @Test public void reusesStorageWithoutExposingStaleTail() throws Exception {
        X11RequestBuffer b=new X11RequestBuffer();
        b.receive(new ByteArrayInputStream(new byte[4096]),4096);
        int capacity=b.capacity();
        b.receive(new ByteArrayInputStream(new byte[]{42}),1);
        assertEquals(capacity,b.capacity());assertEquals(42,b.read());assertEquals(-1,b.read());
        assertEquals(0,b.read(new byte[0],0,0));
    }
    @Test public void decodesAtNonzeroOffsetAndPreservesFollowingBytes() throws Exception {
        for(boolean bulk:new boolean[]{false,true}) {
            X11RequestBuffer b=new X11RequestBuffer();
            b.receive(new ByteArrayInputStream(new byte[]{99,1,2,3,4,5,6,7,8,88}),10);
            assertEquals(99,b.read());int[] colors=new int[2];
            b.readBgrxColors(colors,2,bulk);
            assertArrayEquals(new int[]{0xff030201,0xff070605},colors);
            assertEquals(88,b.read());assertEquals(-1,b.read());
        }
    }
    @Test public void rejectsDecodePastFrameWithoutConsumingBody() throws Exception {
        X11RequestBuffer b=new X11RequestBuffer();
        b.receive(new ByteArrayInputStream(new byte[]{1,2,3}),3);
        try{b.readBgrxColors(new int[1],1,false);fail();}catch(EOFException expected){}
        assertEquals(3,b.available());assertEquals(1,b.read());
    }
    @Test public void truncatedReceiveCannotExposePreviousRequest() throws Exception {
        X11RequestBuffer b=new X11RequestBuffer();
        b.receive(new ByteArrayInputStream(new byte[]{1,2,3,4}),4);
        try{b.receive(new ByteArrayInputStream(new byte[]{7}),4);fail();}catch(EOFException expected){}
        assertEquals(0,b.available());assertEquals(-1,b.read());
    }
}

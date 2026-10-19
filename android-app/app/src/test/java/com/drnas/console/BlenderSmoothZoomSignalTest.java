package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlenderSmoothZoomSignalTest {
    @Test public void encodesCumulativeDelta() {
        assertEquals("r60:1:1",BlenderSmoothZoomSignal.encode(1,1));
        assertEquals("r60:12:-450",BlenderSmoothZoomSignal.encode(12,-450));
        assertEquals("r60:2:0",BlenderSmoothZoomSignal.encode(2,0));
    }
    @Test public void limitsUnboundedUpdates() {
        for(int seq:new int[]{0,-1,10000000}) {
            try{BlenderSmoothZoomSignal.encode(seq,0);fail();}catch(IllegalArgumentException expected){}
        }
        for(int delta:new int[]{1000001,-1000001,Integer.MIN_VALUE}) {
            try{BlenderSmoothZoomSignal.encode(1,delta);fail();}catch(IllegalArgumentException expected){}
        }
    }
}

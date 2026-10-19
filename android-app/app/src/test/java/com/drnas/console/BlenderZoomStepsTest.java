package com.drnas.console;

import static org.junit.Assert.*;
import org.junit.Test;

public class BlenderZoomStepsTest {
    @Test public void dragRightSendsUpWheelStepsAndPreservesRemainder(){
        BlenderZoomSteps steps=new BlenderZoomSteps();
        assertEquals(0,steps.update(54));
        assertEquals(1,steps.update(55));
        assertEquals(0,steps.update(59));
        assertEquals(1,steps.update(60));
        assertEquals(8,steps.update(100));
        assertEquals(0,steps.update(100));
    }
    @Test public void dragLeftSendsDownWheelAndRecenterIsSilent(){
        BlenderZoomSteps steps=new BlenderZoomSteps();
        assertEquals(-1,steps.update(45));
        assertEquals(-9,steps.update(0));
        steps.reset(50);
        assertEquals(0,steps.update(50));
        assertEquals(1,steps.update(55));
    }
    @Test public void extremeJumpsRemainBounded(){
        BlenderZoomSteps steps=new BlenderZoomSteps();
        steps.reset(100);
        assertEquals(-10,steps.update(-999));
        assertEquals(-10,steps.update(0));
        steps.reset(-30);
        assertEquals(10,steps.update(300));
    }
}

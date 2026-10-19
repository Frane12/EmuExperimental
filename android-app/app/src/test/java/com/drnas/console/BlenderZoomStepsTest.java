package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlenderZoomStepsTest {
    @Test public void fineDragFollowsEverySliderPoint() {
        BlenderZoomSteps steps=new BlenderZoomSteps();
        assertEquals(4,steps.update(54));
        assertEquals(1,steps.update(55));
        assertEquals(4,steps.update(59));
        assertEquals(1,steps.update(60));
        assertEquals(40,steps.update(100));
        assertEquals(0,steps.update(100));
    }
    @Test public void dragLeftAndSilentRecenter() {
        BlenderZoomSteps steps=new BlenderZoomSteps();
        assertEquals(-5,steps.update(45));
        assertEquals(-45,steps.update(0));
        steps.reset(50);
        assertEquals(0,steps.update(50));
        assertEquals(5,steps.update(55));
    }
    @Test public void positionsClampedWithoutOverflows() {
        BlenderZoomSteps steps=new BlenderZoomSteps();
        steps.reset(100);
        assertEquals(-100,steps.update(-999));
        assertEquals(0,steps.update(0));
        steps.reset(-30);
        assertEquals(100,steps.update(300));
    }
}

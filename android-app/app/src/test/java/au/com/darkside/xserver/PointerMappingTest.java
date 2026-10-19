package au.com.darkside.xserver;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Regression: r58 zoom slider sent wheel 4/5 but Pointer dropped both. */
public class PointerMappingTest {
    @Test public void defaultMappingIncludesWheelUpAndWheelDown() {
        Pointer pointer=new Pointer();
        assertEquals(1,pointer.mapButton(1));
        assertEquals(2,pointer.mapButton(2));
        assertEquals(3,pointer.mapButton(3));
        assertEquals(4,pointer.mapButton(4));
        assertEquals(5,pointer.mapButton(5));
    }
    @Test public void invalidButtonsRemainRejected() {
        Pointer pointer=new Pointer();
        assertEquals(0,pointer.mapButton(0));
        assertEquals(0,pointer.mapButton(6));
        assertEquals(0,pointer.mapButton(-1));
    }
}

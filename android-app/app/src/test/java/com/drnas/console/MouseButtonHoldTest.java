package com.drnas.console;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class MouseButtonHoldTest {
    @Test public void staysPressedUntilActualRelease(){
        List<String> events=new ArrayList<>();
        MouseButtonHold hold=new MouseButtonHold((b,p)->events.add(b+":"+p));
        hold.press(1);hold.press(1);
        assertTrue(hold.isHeld(1));
        assertEquals(List.of("1:true"),events);
        hold.release(1);hold.release(1);
        assertEquals(List.of("1:true","1:false"),events);
    }
    @Test public void cancellationAndPauseReleaseBothButtonsExactlyOnce(){
        List<String> events=new ArrayList<>();
        MouseButtonHold hold=new MouseButtonHold((b,p)->events.add(b+":"+p));
        hold.press(1);hold.press(3);hold.releaseAll();hold.releaseAll();
        assertFalse(hold.isHeld(1));assertFalse(hold.isHeld(3));
        assertEquals(List.of("1:true","3:true","1:false","3:false"),events);
    }
    @Test public void releasingLeftDoesNotDropHeldRight(){
        List<String> events=new ArrayList<>();
        MouseButtonHold hold=new MouseButtonHold((b,p)->events.add(b+":"+p));
        hold.press(1);hold.press(3);hold.release(1);
        assertTrue(hold.isHeld(3));assertFalse(hold.isHeld(1));
        hold.release(3);
        assertEquals(List.of("1:true","3:true","1:false","3:false"),events);
    }
}

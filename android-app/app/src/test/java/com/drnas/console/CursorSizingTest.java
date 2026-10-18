package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public class CursorSizingTest {
    @Test public void highDensityDoesNotInflatePointer() {
        assertEquals(28,32*CursorSizing.compactScale(32,32,4),.001);
        assertEquals(16,32*CursorSizing.compactScale(32,32,1),.001);
    }
    @Test public void hotspotStaysAtPhysicalPointer() {
        float s=CursorSizing.compactScale(32,24,3);
        float left=CursorSizing.edge(350,0,7,s);
        assertEquals(350,left+7*s,.001);
        assertEquals(350,CursorSizing.edge(350,7,7,s),.001);
    }
    @Test public void arrowKeepsItsAspectRatio() {
        float s=CursorSizing.compactScale(24,32,3);
        assertEquals(.75,(24*s)/(32*s),.001);
        assertEquals(1,CursorSizing.compactScale(0,0,3),.001);
    }
}

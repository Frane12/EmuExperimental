package au.com.darkside.xserver;
import org.junit.Test;
import static org.junit.Assert.*;

public class X11FrameTailCleanupTest {
    @Test public void stripsRightAndBottomOnlyForAlmostFullOpaqueFrame() {
        assertTrue(X11FrameTailCleanup.shouldScrub(0,0,1264,708,1280,720,true));
        assertFalse(X11FrameTailCleanup.shouldScrub(0,0,1280,720,1280,720,true));
        assertFalse(X11FrameTailCleanup.shouldScrub(0,0,640,480,1280,720,true));
        assertFalse(X11FrameTailCleanup.shouldScrub(0,0,1264,708,1280,720,false));
        assertFalse(X11FrameTailCleanup.shouldScrub(2,0,1264,708,1280,720,true));
        assertFalse(X11FrameTailCleanup.shouldScrub(0,0,1281,721,1280,720,true));
        // Device log 2026-10-17 16:37: 3619852 total payload - 12 header
        // = 1280*707*4. A game drawable may be 707 high on a 720-high root.
        assertEquals((1280*707*4)+12,3619852);
        assertTrue(X11FrameTailCleanup.shouldScrub(0,0,1280,707,1280,720,true));
        assertEquals(13,720-707);  // Only rows [707,720) may be blackened.
        assertFalse(X11FrameTailCleanup.shouldScrub(0,0,1280,707,1280,707,true));
        // A normal mid-frame dirty rectangle must NEVER clear retained pixels.
        assertFalse(X11FrameTailCleanup.shouldScrub(200,200,640,480,1280,720,true));
        assertFalse(X11FrameTailCleanup.shouldScrub(0,1,1280,707,1280,720,true));
        assertFalse(X11FrameTailCleanup.shouldScrub(0,0,1280,707,1280,720,false));
    }
}

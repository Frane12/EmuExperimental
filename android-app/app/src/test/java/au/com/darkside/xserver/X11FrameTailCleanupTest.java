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
    }
}

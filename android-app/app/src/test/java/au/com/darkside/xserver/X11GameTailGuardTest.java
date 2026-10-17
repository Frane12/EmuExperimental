package au.com.darkside.xserver;

import org.junit.Test;
import static org.junit.Assert.*;

public class X11GameTailGuardTest {
    @Test public void detectsActual707HighGameOn720RootOnly() {
        assertEquals(13,X11GameTailGuard.candidateGap(
                1280,707,1280,707,1280,720,0,0,true));
        assertEquals(0,X11GameTailGuard.candidateGap(
                1280,707,1280,707,1280,707,0,0,true));
        assertEquals(0,X11GameTailGuard.candidateGap(
                1280,707,1280,720,1280,720,0,0,true)); // a partial copy
        assertEquals(0,X11GameTailGuard.candidateGap(
                1280,707,1280,707,1280,720,8,0,true));
        assertEquals(0,X11GameTailGuard.candidateGap(
                1280,707,1280,707,1280,720,0,0,false));
    }
    @Test public void activatesAfterRepeatAndExpiresWhenGameStops(){
        X11GameTailGuard g=new X11GameTailGuard();
        for(int i=0;i<5;i++) {
            g.observe(13,707,1_000_000_000L+i*10_000_000);
            assertEquals(0,g.visibleGap(1_200_000_000L));
        }
        g.observe(13,707,1_100_000_000L);
        assertEquals(13,g.visibleGap(1_200_000_000L));
        assertEquals(0,g.visibleGap(4_500_000_000L));
        g.reset();
        assertEquals(0,g.visibleGap(1_200_000_000L));
    }
}

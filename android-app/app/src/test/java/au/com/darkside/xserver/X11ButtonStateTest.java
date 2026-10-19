package au.com.darkside.xserver;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class X11ButtonStateTest {
    @Test public void pressAndReleaseKeepModifiersAndOtherHeldButtons() {
        for (int button=1;button<=5;button++) {
            int mask=0x80 << button;
            int prior=0x05 | ((0x80 << (button==1?3:1)));
            assertEquals(prior,X11ButtonState.beforeTransition(prior|mask,button));
            assertEquals(prior|mask,X11ButtonState.beforeTransition(prior,button));
        }
    }
    @Test public void tapCarriesUpStateOnPressAndDownStateOnRelease() {
        assertEquals(0,X11ButtonState.beforeTransition(0x100,1));
        assertEquals(0x100,X11ButtonState.beforeTransition(0,1));
    }
    @Test public void capturedQueuedReleaseStateSurvivesLaterButtonChanges() {
        int captured=X11ButtonState.beforeTransition(0x04,1);
        int later=0x04 | 0x400;
        assertEquals(0x104,captured);
        assertEquals(0x504,X11ButtonState.beforeTransition(later,1));
    }
}

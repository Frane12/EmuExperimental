package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;

public class WineBootEvidenceTest {
    @Test public void traceFloodDoesNotHideCompletionOrFinalFailure() {
        WineBootEvidence e=new WineBootEvidence();
        for(int i=0;i<25000;i++)e.observe("002c:trace:module:load_dll kernel32.dll");
        e.observe("002c:trace:wineboot:main Operation done");
        e.observe("002c:err:seh:raise_exception Unhandled exception");
        assertTrue(e.completed()); assertEquals(1,e.errors());
        assertEquals(12,e.tail().size());
        assertTrue(e.tail().get(11).contains("Unhandled exception"));
    }
    @Test public void exitZeroAndMutationCannotProveCompletion() {
        assertEquals("WINEBOOT_EXIT_0_COMPLETION_UNPROVEN",
                WineBootEvidence.classify(true,0,false,false,3,true));
        assertEquals("WINEBOOT_COMPLETED_CMD_UNPROVEN",
                WineBootEvidence.classify(true,0,true,false,3,true));
    }
    @Test public void verifiedGuestDoesNotRequirePersistedMutation() {
        assertEquals("WINEBOOT_COMPLETED_GUEST_VERIFIED",
                WineBootEvidence.classify(true,0,true,true,3,false));
        assertEquals("WINEBOOT_PREFIX_INITIALIZED",
                WineBootEvidence.classify(true,0,true,true,3,true));
        assertEquals("WINEBOOT_TIMEOUT",
                WineBootEvidence.classify(false,0,true,true,3,true));
        assertEquals("WINEBOOT_EXIT_1",
                WineBootEvidence.classify(true,1,true,true,3,true));
    }
    @Test public void commandEchoOrOtherModuleCannotSpoofCompletion() {
        WineBootEvidence e=new WineBootEvidence();
        e.observe("002c:trace:process:main Operation done");
        e.observe("002c:trace:wineboot:main command Operation done");
        assertFalse(e.completed());
    }
}

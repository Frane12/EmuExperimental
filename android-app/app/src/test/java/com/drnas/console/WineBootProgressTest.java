package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public class WineBootProgressTest {
    @Test public void doesNotClaimReadyBeforeWinebootVerification(){
        WineBootProgress p=new WineBootProgress();
        assertEquals(0,p.percent());
        p.begin();
        assertEquals(3,p.percent());
        assertTrue(p.advance("[container] Ensuring embedded Proton 11.0-2"));
        assertTrue(p.advance("[container] [bundle] Installing Proton"));
        assertFalse(p.advance("[container] Ensuring embedded Proton 11.0-2"));
        assertEquals(8,p.percent());
        p.advance("[container] wineserver preflight status=SERVER_VERSION_EXIT_0");
        assertEquals(92,p.percent());
        p.finish(false);
        assertTrue(p.percent()<100);
        assertEquals("Inicijalizacija nije uspjela",p.phase());
    }

    @Test public void marksHundredOnlyAfterVerifiedCompletion(){
        WineBootProgress p=new WineBootProgress();
        p.begin();
        p.advance("[container] Wineboot completion + independent Windows CMD execution confirmed!");
        assertEquals(97,p.percent());
        p.finish(true);
        assertEquals(100,p.percent());
        assertEquals("Wine je spreman",p.phase());
    }
}

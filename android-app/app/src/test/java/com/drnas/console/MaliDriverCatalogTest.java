package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public class MaliDriverCatalogTest {
    @Test public void g52GetsThreeCandidatesOnAndroid12() {
        assertEquals(3,MaliDriverCatalog.compatible("phone","Mali-G52 MC2",31,"").size());
    }
    @Test public void android9G52GetsOnlyV7WithItsDeclaredMinimumApi() {
        assertEquals("PANVK_G52_V7",MaliDriverCatalog.compatible("phone","Mali-G52 MC2",28,"").get(0).id);
        assertTrue(MaliDriverCatalog.compatible("phone","Mali-G52 MC2",27,"").isEmpty());
    }
    @Test public void g52SpecificDriverNeverGoesToG72OrG76() {
        for(String gpu:new String[]{"Mali-G72","Mali-G76"}) {
            assertEquals(2,MaliDriverCatalog.compatible("phone",gpu,31,"").size());
            assertTrue(MaliDriverCatalog.compatible("phone",gpu,30,"").isEmpty());
        }
    }
    @Test public void exactChipMatchDoesNotAcceptAdrenoOrOtherMaliGenerations() {
        for(String gpu:new String[]{"Adreno 840","Mali-G57","Mali-G520","Mali-G720","Mali-G760",""})
            assertTrue(MaliDriverCatalog.compatible("phone",gpu,35,"").isEmpty());
    }
    @Test public void rememberedCandidateIsPrioritizedOnlyIfCompatible() {
        assertEquals("PANVK_G52_V7",MaliDriverCatalog.compatible("phone","Mali-G52",31,"PANVK_G52_V7").get(0).id);
        assertEquals("PANVK_JIM_R4",MaliDriverCatalog.compatible("phone","Mali-G76",31,"PANVK_G52_V7").get(0).id);
    }
}

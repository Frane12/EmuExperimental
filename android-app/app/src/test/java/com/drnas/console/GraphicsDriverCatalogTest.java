package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.HashSet;

public class GraphicsDriverCatalogTest {
    @Test public void choicesNeverCrossChipFamilies() {
        for(String target:new String[]{"A810","A830","A840"}) {
            assertEquals(2,GraphicsDriverCatalog.compatible(target,35).size());
            for(GraphicsDriverCatalog.Entry entry:GraphicsDriverCatalog.compatible(target,35))
                assertEquals(target,entry.target);
        }
        assertTrue(GraphicsDriverCatalog.compatible("Mali G52",35).isEmpty());
        assertTrue(GraphicsDriverCatalog.compatible("A8400",35).isEmpty());
    }
    @Test public void legacyRequiresItsMinimumAndroidVersion() {
        assertTrue(GraphicsDriverCatalog.compatible("A740",27).isEmpty());
        assertEquals(1,GraphicsDriverCatalog.compatible("A740",28).size());
    }
    @Test public void pinsAndIdentifiersAreUniqueAndComplete() {
        HashSet<String> ids=new HashSet<>(),assets=new HashSet<>(),hashes=new HashSet<>();
        for(GraphicsDriverCatalog.Entry e:GraphicsDriverCatalog.ENTRIES) {
            assertTrue(ids.add(e.id));assertTrue(assets.add(e.asset));assertTrue(hashes.add(e.sha));
            assertTrue(e.sha.matches("[0-9a-f]{64}"));assertSame(e,GraphicsDriverCatalog.find(e.id));
        }
        assertNull(GraphicsDriverCatalog.find("unknown"));
    }
}

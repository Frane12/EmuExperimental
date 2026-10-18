package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class BlenderDemoImportTest {
    @Test public void acceptsNormalBlendAndSafeFilename() throws Exception {
        assertEquals("rock.blend",BlenderDemoImport.cleanName("rock.blend"));
        assertEquals("pebble_scene.blend",
                BlenderDemoImport.cleanName("pebble:scene.blend"));
        assertTrue(BlenderDemoImport.isBlendMagic(
                "BLENDER-v300".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test public void refusesUnexpectedFileTypesOrTraversal() throws Exception {
        for(String name:new String[]{"scene.zip","scene.blend1","../evil.zip",".blend",
                "",null}) {
            try {
                BlenderDemoImport.cleanName(name);
                fail("must reject "+name);
            } catch(IOException expected) {}
        }
        assertFalse(BlenderDemoImport.isBlendMagic(
                "NotBlend".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test public void userSuppliedNameNeverBecomesAbsoluteOrParentPath() throws Exception {
        for(String name:new String[]{"../test.blend","../../test.blend",
                "C:\\\\Users\\\\secret.blend","\u00e9.blend"}) {
            String safe=BlenderDemoImport.cleanName(name);
            assertFalse(safe.contains("/"));
            assertFalse(safe.contains("\\\\"));
            assertTrue(safe.endsWith(".blend"));
        }
    }
}

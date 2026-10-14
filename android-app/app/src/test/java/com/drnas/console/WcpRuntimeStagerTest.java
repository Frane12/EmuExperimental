package com.drnas.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import java.io.IOException;

/** Untrusted TAR/ZIP path handling used by isolated component staging. */
public final class WcpRuntimeStagerTest {
    @Test public void normalizesTarRootAndPaths() throws Exception {
        assertEquals("", WcpRuntimeStager.clean("./",true));
        assertEquals("", WcpRuntimeStager.clean(".",true));
        assertEquals("profile.json", WcpRuntimeStager.clean("./profile.json",false));
        assertEquals("system32", WcpRuntimeStager.clean("./system32/",true));
        assertEquals("bin/wine", WcpRuntimeStager.clean("bin/wine",false));
    }
    @Test public void refusesTraversalAndEmptyFiles() {
        for(String path : new String[]{"", ".", "./", "../etc/passwd", "./../../evil",
                "dir/../payload", "dir//payload", "/system/bin/sh", "C:\\evil",
                "dir/./payload", "a\u0000b"}) {
            assertThrows("accepted "+path, IOException.class,
                    ()->WcpRuntimeStager.clean(path,false));
        }
    }
}

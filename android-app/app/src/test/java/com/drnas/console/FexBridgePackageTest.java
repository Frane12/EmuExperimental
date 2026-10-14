package com.drnas.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import org.junit.Test;

import java.io.IOException;

/** Regression for FEX GameNative WCP archives containing a TAR root entry. */
public class FexBridgePackageTest {
    @Test public void gnuTarRootDirectoriesAreAccepted() throws Exception {
        assertEquals("", FexBridgePackage.normalize("."));
        assertEquals("", FexBridgePackage.normalize("./"));
        assertEquals("", FexBridgePackage.normalize("././"));
    }

    @Test public void ordinaryFilesAndDirectoriesAreCanonicalized() throws Exception {
        assertEquals("profile.json", FexBridgePackage.normalize("./profile.json"));
        assertEquals("system32", FexBridgePackage.normalize("./system32/"));
        assertEquals("system32/libwow64fex.dll",
                FexBridgePackage.normalize("./system32/libwow64fex.dll"));
    }

    @Test public void dangerousArchivePathsAreRejected() {
        for (String path : new String[] {
                "", "/tmp/evil", "../escape", "./../../escape",
                "system32//payload.dll", "system32/../payload.dll",
                "system32\\evil.dll", "a\u0000b"
        }) {
            assertThrows("accepted path: " + path, IOException.class,
                    () -> FexBridgePackage.normalize(path));
        }
    }
}

package com.drnas.console;

import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;
import java.util.List;

/** No real wineserver is executed by local JVM unit tests. */
public final class WineServerDiagnosticsTest {
    @Test public void foregroundNativeLinkerCommandHasFixedSafeArguments() {
        List<String> args=WineServerDiagnostics.nativeCommand(
                new File("/system/bin/linker64"),
                new File("/data/user/0/com.drnas.console/files/proton/bin/wineserver"));
        assertEquals(4,args.size());
        assertEquals("/system/bin/linker64",args.get(0));
        assertEquals("/data/user/0/com.drnas.console/files/proton/bin/wineserver",args.get(1));
        assertEquals("--foreground",args.get(2));
        assertEquals("--persistent=60",args.get(3));
        assertFalse(args.contains("--library-path"));
        assertFalse(args.contains("wineboot.exe"));
    }
    @Test public void rejectRelativeWineServer() {
        assertThrows(IllegalArgumentException.class,()->WineServerDiagnostics.nativeCommand(
                new File("/system/bin/linker64"),new File("wineserver")));
    }

    @Test public void missingNlsBlocksPrestartWithoutRunningProcess() throws Exception {
        java.nio.file.Path root=java.nio.file.Files.createTempDirectory("drnas-nls");
        try {
            java.util.ArrayList<String> log=new java.util.ArrayList<>();
            assertNull(WineServerDiagnostics.prestart(
                    new File("/system/bin/linker64"),root.resolve("bin/wineserver").toFile(),
                    root.toFile(),root.toFile(),new java.util.HashMap<>(),log::add));
            assertTrue(log.stream().anyMatch(x->x.contains("l_intl.nls")));
        } finally {
            WineBootProbe.removeWithoutFollowingLinks(root.toFile());
        }
    }
}

package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Synthetic sandbox tests only; never execute a real guest binary in CI. */
public class WineBootProbeTest {
    @Test public void launchesWineNotWineserverWithBoundedInitCommand() {
        List<String> args=WineBootProbe.command(new File("/system/bin/linker64"),
                new File("/data/user/0/com.drnas.console/files/proton/lib/wine/aarch64-unix/wine"));
        assertEquals(4,args.size());
        assertEquals("/system/bin/linker64",args.get(0));
        assertTrue(args.get(1).endsWith("aarch64-unix/wine"));
        assertEquals("wineboot.exe",args.get(2));
        assertEquals("--init",args.get(3));
        assertFalse(args.contains("--library-path"));
    }

    @Test public void deletingTestRootDoesNotFollowExternalSymlink() throws Exception {
        Path sandbox=Files.createTempDirectory("drnas-test-wineboot");
        Path outside=Files.createTempFile("drnas-keep-this-registry","");
        try {
            Files.write(outside,"PRESERVE".getBytes(StandardCharsets.UTF_8));
            Path sub=Files.createDirectory(sandbox.resolve("sandbox-prefix"));
            Files.write(sub.resolve("user.reg"),"test".getBytes(StandardCharsets.UTF_8));
            Files.createSymbolicLink(sub.resolve("linked-outside.reg"),outside);
            WineBootProbe.removeWithoutFollowingLinks(sandbox.toFile());
            assertFalse(Files.exists(sandbox));
            assertTrue(Files.exists(outside));
            assertEquals("PRESERVE",new String(Files.readAllBytes(outside),StandardCharsets.UTF_8));
        } finally {
            if(Files.exists(sandbox))WineBootProbe.removeWithoutFollowingLinks(sandbox.toFile());
            Files.deleteIfExists(outside);
        }
    }
    @Test public void refusesRelativeExecutable() {
        assertThrows(IllegalArgumentException.class,()->WineBootProbe.command(
                new File("/system/bin/linker64"),new File("wine")));
    }
}

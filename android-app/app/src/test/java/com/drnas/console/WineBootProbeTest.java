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

    @Test public void skipsOnlyWinebootReexecViaWineSecondStageFlag() {
        java.util.Map<String,String> childEnv=new java.util.HashMap<>();
        WineBootProbe.configureSecondStage(childEnv);
        assertEquals("1",childEnv.get("WINELOADERNOEXEC"));
        assertEquals(1,childEnv.size());
        assertFalse(WineBootProbe.command(new File("/system/bin/linker64"),
                new File("/data/user/0/com.drnas.console/files/wine"))
                .contains("WINELOADERNOEXEC"));
    }

    @Test public void findsPEModuleInGameNativeWineFolder() throws Exception {
        Path root=Files.createTempDirectory("drnas-wine-pe");
        try {
            Path lib=Files.createDirectories(root.resolve("lib/wine"));
            Path prefix=Files.createDirectories(root.resolve("prefix"));
            Path pe=lib.resolve("aarch64-windows/wineboot.exe");
            Files.createDirectories(pe.getParent());
            byte[] hdr=new byte[80]; hdr[0]='M';hdr[1]='Z';
            Files.write(pe,hdr);
            java.util.ArrayList<String> logs=new java.util.ArrayList<>();
            assertTrue(WineBootProbe.inspectWinebootArtifacts(lib.toFile(),
                    prefix.toFile(),logs::add));
            assertTrue(logs.stream().anyMatch(x->x.contains(
                    "aarch64-windows/wineboot.exe: PE/MZ")));
        } finally {
            WineBootProbe.removeWithoutFollowingLinks(root.toFile());
        }
    }

    @Test public void doesNotTreatZeroLengthPrefixRegistryOrBootModuleAsValidPE()
            throws Exception {
        Path root=Files.createTempDirectory("drnas-wine-empty-pe");
        try {
            Path lib=Files.createDirectories(root.resolve("lib/wine"));
            Path prefix=Files.createDirectories(root.resolve("prefix"));
            Path pe=lib.resolve("aarch64-windows/wineboot.exe");
            Files.createDirectories(pe.getParent());
            Files.createFile(pe);
            assertFalse(WineBootProbe.inspectWinebootArtifacts(lib.toFile(),
                    prefix.toFile(),msg->{}));
        } finally {
            WineBootProbe.removeWithoutFollowingLinks(root.toFile());
        }
    }
}

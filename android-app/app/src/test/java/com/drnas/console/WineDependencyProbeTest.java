package com.drnas.console;

import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Regression for PE header classification without executing untrusted files. */
public final class WineDependencyProbeTest {
    private static void fakePe(Path path,int machine) throws Exception {
        Files.createDirectories(path.getParent());
        try(RandomAccessFile f=new RandomAccessFile(path.toFile(),"rw")){
            f.setLength(512);
            f.seek(0);f.write('M');f.write('Z');
            f.seek(60);f.writeInt(Integer.reverseBytes(128));
            f.seek(128);f.write('P');f.write('E');f.write(0);f.write(0);
            f.write(machine&255);f.write((machine>>8)&255);
        }
    }
    @Test public void detectsArm64AndArm64ecPE() throws Exception {
        Path root=Files.createTempDirectory("drnas-pe-machine");
        try {
            Path arm=root.resolve("aarch64-windows/kernel32.dll");
            Path ec=root.resolve("arm64ec-windows/kernelbase.dll");
            fakePe(arm,0xaa64);fakePe(ec,0xa641);
            assertTrue(WineDependencyProbe.describe(arm.toFile()).contains("PE ARM64 machine=0xaa64"));
            assertTrue(WineDependencyProbe.describe(ec.toFile()).contains("PE ARM64EC machine=0xa641"));
            assertEquals("MISSING",WineDependencyProbe.describe(root.resolve("none.dll").toFile()));
            Path invalid=root.resolve("invalid.dll");Files.write(invalid,new byte[4]);
            assertTrue(WineDependencyProbe.describe(invalid.toFile()).startsWith("INVALID"));
        } finally {
            WineBootProbe.removeWithoutFollowingLinks(root.toFile());
        }
    }
    @Test public void inventoriesBuiltinAndPrefixWithoutClaimingBootSucceeded()
            throws Exception {
        Path root=Files.createTempDirectory("drnas-pe-inventory");
        try {
            Path lib=root.resolve("lib/wine");
            Path prefix=root.resolve("prefix");
            Files.createDirectories(lib);Files.createDirectories(prefix);
            fakePe(lib.resolve("aarch64-windows/kernel32.dll"),0xaa64);
            ArrayList<String> logs=new ArrayList<>();
            WineDependencyProbe.inventory(lib.toFile(),prefix.toFile(),logs::add);
            assertTrue(logs.stream().anyMatch(x->x.contains(
                    "aarch64-windows/kernel32.dll = PE ARM64")));
            assertTrue(logs.stream().anyMatch(x->x.contains(
                    "aarch64-windows/kernelbase.dll = MISSING")));
            assertTrue(logs.stream().anyMatch(x->x.contains(
                    "do not prove that Wine loaded kernel32.dll")));
        } finally {
            WineBootProbe.removeWithoutFollowingLinks(root.toFile());
        }
    }
}

package com.drnas.console;

import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

/** Synthetic files only: no third-party Wine binary or guest execution. */
public class WineLaunchProbeTest {
    private File temp(byte[] data) throws Exception {
        File f=Files.createTempFile("drnas-wine-probe-", ".bin").toFile();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(f)){out.write(data);}
        f.deleteOnExit();return f;
    }
    @Test public void rejectsPEAndArbitraryData() throws Exception {
        byte[] mz=new byte[72];mz[0]='M';mz[1]='Z';
        WineLaunchProbe.Binary result=WineLaunchProbe.inspect(temp(mz));
        assertFalse(result.runnable);
        assertTrue(result.description.contains("PE/MZ"));
        assertFalse(WineLaunchProbe.inspect(temp("unknown".getBytes(StandardCharsets.UTF_8))).runnable);
    }
    private File nativeElf(String interp) throws Exception {
        byte[] name=(interp+"\u0000").getBytes(StandardCharsets.US_ASCII);
        byte[] b=new byte[128+name.length];
        b[0]=0x7f;b[1]='E';b[2]='L';b[3]='F';
        b[4]=2;b[5]=1;b[6]=1; // ELF64 little-endian
        b[16]=3; // ET_DYN
        b[18]=(byte)183;b[19]=0; // EM_AARCH64
        b[32]=64; // e_phoff 64
        b[54]=56; // program header size
        b[56]=1; // one phdr
        b[64]=3; // PT_INTERP
        b[64+8]=(byte)128; // p_offset
        b[64+32]=(byte)name.length; // p_filesz
        System.arraycopy(name,0,b,128,name.length);
        return temp(b);
    }
    @Test public void recognizesBionicELFEntrypoint() throws Exception {
        WineLaunchProbe.Binary r=WineLaunchProbe.inspect(nativeElf("/system/bin/linker64"));
        assertTrue(r.description,r.runnable);
        assertEquals("/system/bin/linker64",r.interp);
    }
    @Test public void recognizesButDoesNotLaunchGlibcBinary() throws Exception {
        WineLaunchProbe.Binary r=WineLaunchProbe.inspect(nativeElf("/lib/ld-linux-aarch64.so.1"));
        assertFalse(r.runnable);
        assertTrue(r.description.contains("unconfirmed target"));
    }

    @Test public void selectsGameNativeRealLoaderWhenBinWineIsEmpty() throws Exception {
        java.nio.file.Path root=Files.createTempDirectory("drnas-proton-layout");
        try {
            java.nio.file.Path bin=root.resolve("bin");
            java.nio.file.Path loader=root.resolve("lib/wine/aarch64-unix");
            Files.createDirectories(bin);Files.createDirectories(loader);
            Files.write(bin.resolve("wine"),new byte[0]);
            Files.write(bin.resolve("wineserver"),new byte[0]);
            Files.copy(nativeElf("/system/bin/linker64").toPath(),loader.resolve("wine"));
            java.util.ArrayList<String> messages=new java.util.ArrayList<>();
            File chosen=WineLaunchProbe.selectWine(root.toFile(),messages::add);
            assertNotNull(chosen);
            assertEquals(loader.resolve("wine").toFile().getCanonicalPath(),
                    chosen.getCanonicalPath());
            assertTrue(messages.stream().anyMatch(x->x.contains("Empty Wine placeholder")));
            assertTrue(messages.stream().anyMatch(x->x.contains(
                    "Selected actual Bionic Wine entry: lib/wine/aarch64-unix/wine")));
        } finally {
            try(java.util.stream.Stream<java.nio.file.Path> files=Files.walk(root)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
                    try {Files.deleteIfExists(p);} catch(Exception ignored){}
                });
            }
        }
    }
    @Test public void neverTreatsWineserverAsWineEntrypoint() throws Exception {
        java.nio.file.Path root=Files.createTempDirectory("drnas-proton-no-wine");
        try {
            java.nio.file.Path bin=root.resolve("bin");
            Files.createDirectories(bin);
            Files.copy(nativeElf("/system/bin/linker64").toPath(),bin.resolve("wineserver"));
            assertNull(WineLaunchProbe.selectWine(root.toFile(),msg->{}));
        } finally {
            try(java.util.stream.Stream<java.nio.file.Path> files=Files.walk(root)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
                    try {Files.deleteIfExists(p);} catch(Exception ignored){}
                });
            }
        }
    }

    @Test public void linkerDirectInvocationDoesNotUseGnuFlags() {
        java.util.List<String> args=WineLaunchProbe.linkerCommand(
                new File("/system/bin/linker64"),
                new File("/data/user/0/com.drnas.console/files/wine"));
        assertEquals(3,args.size());
        assertEquals("/system/bin/linker64",args.get(0));
        assertEquals("/data/user/0/com.drnas.console/files/wine",args.get(1));
        assertEquals("--version",args.get(2));
        assertFalse(args.contains("--library-path"));
    }

    @Test public void linkerRejectsRelativeWineExecutable() {
        assertThrows(IllegalArgumentException.class,()->WineLaunchProbe.linkerCommand(
                new File("/system/bin/linker64"),new File("wine")));
    }
}

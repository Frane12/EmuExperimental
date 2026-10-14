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
}

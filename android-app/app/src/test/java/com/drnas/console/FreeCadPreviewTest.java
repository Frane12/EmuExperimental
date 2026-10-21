package com.drnas.console;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.RandomAccessFile;
import static org.junit.Assert.*;

public final class FreeCadPreviewTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private File fakePe(File dir, int machine) throws Exception {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("mkdir failed");
        File exe = new File(dir, "FreeCAD.exe");
        try(RandomAccessFile f = new RandomAccessFile(exe,"rw")) {
            f.setLength(256);
            f.write('M'); f.write('Z');
            f.seek(0x3c); f.write(new byte[]{(byte)0x80,0,0,0});
            f.seek(0x80); f.write(new byte[]{'P','E',0,0,(byte)machine,(byte)(machine>>>8)});
        }
        return exe;
    }

    @Test public void discoversWindowsInstalledAndReadsX64() throws Exception {
        File root = temp.newFolder("drive_c");
        File exe = fakePe(new File(root,"Program Files/FreeCAD 1.1/bin"),0x8664);
        assertEquals(exe.getCanonicalFile(),FreeCadPreview.findExecutable(root));
        assertEquals("x64",FreeCadPreview.architecture(exe));
    }

    @Test public void discoversPortableAndReadsArm64() throws Exception {
        File root = temp.newFolder("drive_c");
        File exe = fakePe(new File(root,"FreeCAD/bin"),0xaa64);
        assertEquals(exe.getCanonicalFile(),FreeCadPreview.findExecutable(root));
        assertEquals("arm64",FreeCadPreview.architecture(exe));
    }

    @Test public void missesUnknownFilesAndRefusesSymlinkEscape() throws Exception {
        File root = temp.newFolder("drive_c");
        assertNull(FreeCadPreview.findExecutable(root));
        File outside = fakePe(temp.newFolder("elsewhere"),0x8664);
        File bin = new File(root,"FreeCAD/bin");
        assertTrue(bin.mkdirs());
        java.nio.file.Files.createSymbolicLink(new File(bin,"FreeCAD.exe").toPath(),outside.toPath());
        assertNull(FreeCadPreview.findExecutable(root));
    }

    @Test public void invalidPeNeverPassesAsX64() throws Exception {
        File f = temp.newFile("bad.exe");
        try(RandomAccessFile out = new RandomAccessFile(f,"rw")) {out.write('M');out.write('Z');}
        assertEquals("invalid",FreeCadPreview.architecture(f));
    }
}

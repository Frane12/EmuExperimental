package com.drnas.console;

import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.zip.*;
import static org.junit.Assert.*;

public class BundledBlenderArchiveTest {
    private byte[] archive(String extra) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)) {
            byte[] pe=new byte[128];pe[0]='M';pe[1]='Z';pe[60]=64;
            pe[64]='P';pe[65]='E';pe[68]=0x64;pe[69]=(byte)0xaa;
            zip.putNextEntry(new ZipEntry("blender-4.5.14-windows-arm64/blender.exe"));zip.write(pe);zip.closeEntry();
            zip.putNextEntry(new ZipEntry("blender-4.5.14-windows-arm64/"+extra));zip.write(42);zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    private String hash(byte[] data) throws Exception {
        StringBuilder result=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(data))result.append(String.format("%02x",b&255));
        return result.toString();
    }
    @Test public void verifiesEntireArchiveAndCommitsPortableConfiguration() throws Exception {
        byte[] zip=archive("4.5/scripts/example.py");Path root=Files.createTempDirectory("blender-test");Path target=root.resolve("Blender");
        BundledBlenderArchive.extract(new ByteArrayInputStream(zip),target,hash(zip),s->{});
        assertTrue(Files.isRegularFile(target.resolve("blender.exe")));
        assertTrue(Files.isDirectory(target.resolve("4.5/config")));
        assertEquals(hash(zip),new String(Files.readAllBytes(target.resolve(".drnas-bundle-sha256")),java.nio.charset.StandardCharsets.US_ASCII));
        String startup=new String(Files.readAllBytes(target.resolve("drnas-startup.py")),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(startup.contains("userpref.blend"));
        assertFalse(startup.contains("drnas-window-layout.py"));
        assertFalse(startup.contains("runpy.run_path"));
        assertTrue(startup.contains("DRNAS_BLENDER_LAYOUT_DISABLED"));
        assertTrue(startup.contains("DRNAS_BLENDER_SAFE_FULLSCREEN_MENU"));
        assertTrue(startup.contains("drnas.safe_fullscreen"));
        assertTrue(startup.contains("drnas-host-fullscreen.signal"));
        assertTrue(startup.contains("DRNAS_BLENDER_SAFE_FULLSCREEN_REQUEST"));
        assertFalse(startup.contains("layout.operator(\"wm.window_fullscreen_toggle\""));
        try{BundledBlenderArchive.extract(new ByteArrayInputStream(zip),target,hash(zip),s->{});fail();}catch(IOException expected){}
    }
    @Test public void badHashRollsBackAllFiles() throws Exception {
        byte[] zip=archive("license.txt");Path root=Files.createTempDirectory("blender-test");Path target=root.resolve("Blender");
        try{BundledBlenderArchive.extract(new ByteArrayInputStream(zip),target,"0".repeat(64),s->{});fail();}catch(IOException expected){}
        assertFalse(Files.exists(target));try(java.util.stream.Stream<Path> children=Files.list(root)){assertEquals(0,children.count());}
    }
    @Test public void pathTraversalCannotEscapeStaging() throws Exception {
        byte[] zip=archive("../../escape.txt");Path root=Files.createTempDirectory("blender-test");Path target=root.resolve("Blender");
        try{BundledBlenderArchive.extract(new ByteArrayInputStream(zip),target,hash(zip),s->{});fail();}catch(IOException expected){}
        assertFalse(Files.exists(root.resolve("escape.txt")));assertFalse(Files.exists(target));
    }
}


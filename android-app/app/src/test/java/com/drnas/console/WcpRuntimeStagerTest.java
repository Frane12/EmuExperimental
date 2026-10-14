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

    @Test public void defersExternalDriveMappingsButPreservesInternalLinks()
            throws Exception {
        assertEquals(false,WcpRuntimeStager.acceptPrefixSymlink(".wine/dosdevices/d:",
                "/storage/emulated/0"));
        assertEquals(false,WcpRuntimeStager.acceptPrefixSymlink(".wine/dosdevices/z:","/"));
        assertEquals(false,WcpRuntimeStager.acceptPrefixSymlink(".wine/dosdevices/e:",
                "../../../../storage"));
        assertEquals(true,WcpRuntimeStager.acceptPrefixSymlink(".wine/dosdevices/c:",
                "../drive_c"));
        assertThrows(IOException.class,()->WcpRuntimeStager.acceptPrefixSymlink(
                ".wine/customlink","/storage/emulated/0"));
        assertThrows(IOException.class,()->WcpRuntimeStager.acceptPrefixSymlink(
                ".wine/customlink","../../../etc/passwd"));
    }

    private static void dir(org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tar,
                            String name) throws Exception {
        org.apache.commons.compress.archivers.tar.TarArchiveEntry e=
                new org.apache.commons.compress.archivers.tar.TarArchiveEntry(name);
        e.setMode(0755);tar.putArchiveEntry(e);tar.closeArchiveEntry();
    }

    private static void file(org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tar,
                             String name,String content) throws Exception {
        byte[] bytes=content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.apache.commons.compress.archivers.tar.TarArchiveEntry e=
                new org.apache.commons.compress.archivers.tar.TarArchiveEntry(name);
        e.setSize(bytes.length);tar.putArchiveEntry(e);tar.write(bytes);tar.closeArchiveEntry();
    }

    private static void link(org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tar,
                             String name,String destination) throws Exception {
        org.apache.commons.compress.archivers.tar.TarArchiveEntry e=
                new org.apache.commons.compress.archivers.tar.TarArchiveEntry(
                        name,org.apache.commons.compress.archivers.tar.TarConstants.LF_SYMLINK);
        e.setLinkName(destination);
        tar.putArchiveEntry(e);tar.closeArchiveEntry();
    }

    @Test public void xzPrefixWithWinlatorDriveLinksExtractsSafely() throws Exception {
        java.nio.file.Path work=java.nio.file.Files.createTempDirectory("drnas-prefix-test");
        try {
            java.io.File packed=work.resolve("prefixPack.txz").toFile();
            try(org.tukaani.xz.XZOutputStream xz=new org.tukaani.xz.XZOutputStream(
                    new java.io.FileOutputStream(packed),new org.tukaani.xz.LZMA2Options());
                org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tar=
                    new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(xz)) {
                dir(tar,".wine/");
                dir(tar,".wine/dosdevices/");
                link(tar,".wine/dosdevices/d:","/storage/emulated/0");
                link(tar,".wine/dosdevices/z:","/");
                link(tar,".wine/dosdevices/c:","../drive_c");
                dir(tar,".wine/drive_c/");
                dir(tar,".wine/drive_c/windows/");
                file(tar,".wine/drive_c/windows/test.txt","ready");
            }
            java.io.File prefix=work.resolve("isolated-prefix").toFile();
            java.util.ArrayList<String> log=new java.util.ArrayList<>();
            WcpRuntimeStager.unpackPrefixPack(packed,prefix,log::add);
            assertEquals(true,new java.io.File(prefix,
                    ".wine/drive_c/windows/test.txt").isFile());
            assertEquals(true,java.nio.file.Files.isSymbolicLink(
                    prefix.toPath().resolve(".wine/dosdevices/c:")));
            assertEquals(false,java.nio.file.Files.exists(
                    prefix.toPath().resolve(".wine/dosdevices/d:")));
            assertEquals(false,java.nio.file.Files.exists(
                    prefix.toPath().resolve(".wine/dosdevices/z:")));
            assertEquals(true,new java.io.File(prefix,"drnas-prefix.ready").isFile());
            assertEquals(true,log.stream().anyMatch(msg->msg.contains("deferred")));
        } finally {
            try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(work)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
                    try {java.nio.file.Files.deleteIfExists(p);} catch(Exception ignored){}
                });
            }
        }
    }

    @Test public void nonDosAbsoluteSymlinkRefusesEntirePrefix() throws Exception {
        java.nio.file.Path work=java.nio.file.Files.createTempDirectory("drnas-prefix-bad");
        try {
            java.io.File packed=work.resolve("bad.txz").toFile();
            try(org.tukaani.xz.XZOutputStream xz=new org.tukaani.xz.XZOutputStream(
                    new java.io.FileOutputStream(packed),new org.tukaani.xz.LZMA2Options());
                org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tar=
                    new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(xz)) {
                dir(tar,".wine/");
                link(tar,".wine/evil","/etc/passwd");
            }
            java.io.File prefix=work.resolve("isolated-prefix").toFile();
            assertThrows(IOException.class,()->WcpRuntimeStager.unpackPrefixPack(
                    packed,prefix,msg->{}));
            assertEquals(false,prefix.exists());
        } finally {
            try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(work)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
                    try {java.nio.file.Files.deleteIfExists(p);} catch(Exception ignored){}
                });
            }
        }
    }

}

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


    private static java.io.File minimalPrefixPack(java.nio.file.Path work) throws Exception {
        java.io.File packed=work.resolve("prefixPack.txz").toFile();
        try(org.tukaani.xz.XZOutputStream xz=new org.tukaani.xz.XZOutputStream(
                new java.io.FileOutputStream(packed),new org.tukaani.xz.LZMA2Options());
            org.apache.commons.compress.archivers.tar.TarArchiveOutputStream tar=
                    new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(xz)) {
            dir(tar,".wine/");
            dir(tar,".wine/drive_c/");
            dir(tar,".wine/drive_c/windows/");
            file(tar,".wine/drive_c/windows/test.txt","ready");
        }
        return packed;
    }

    // Android SDK Java stubs do not expose Files.readString/writeString.
    private static void writeText(java.nio.file.Path path,String content) throws Exception {
        java.nio.file.Files.write(path,content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static String readText(java.nio.file.Path path) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(path),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void cleanWork(java.nio.file.Path work) throws Exception {
        try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(work)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
                try {java.nio.file.Files.deleteIfExists(p);} catch(Exception ignored){}
            });
        }
    }

    @Test public void recoversIncompletePrefixWithoutDestroyingOldData() throws Exception {
        java.nio.file.Path work=java.nio.file.Files.createTempDirectory("drnas-prefix-recovery");
        try {
            java.io.File packed=minimalPrefixPack(work);
            java.io.File prefix=work.resolve("prefix").toFile();
            java.nio.file.Path userFile=prefix.toPath().resolve(
                    "drive_c/users/xuser/Documents/important.blend");
            java.nio.file.Files.createDirectories(userFile.getParent());
            writeText(userFile,"user project - retain");
            java.util.List<String> log=new java.util.ArrayList<>();
            WcpRuntimeStager.unpackPrefixPack(packed,prefix,log::add);
            assertEquals(true,new java.io.File(prefix,"drnas-prefix.ready").isFile());
            assertEquals(true,new java.io.File(prefix,
                    ".wine/drive_c/windows/test.txt").isFile());
            assertEquals(false,java.nio.file.Files.exists(userFile));
            java.io.File[] backups=work.toFile().listFiles(
                    (folder,name)->name.startsWith(".prefix-incomplete-backup-"));
            assertEquals(1,backups.length);
            assertEquals("user project - retain",readText(
                    backups[0].toPath().resolve("drive_c/users/xuser/Documents/important.blend")));
            assertEquals(true,log.stream().anyMatch(m->m.startsWith("PREFIX_RECOVERY_BACKUP:")));
            // A second run is idempotent: it must not produce another backup.
            WcpRuntimeStager.unpackPrefixPack(packed,prefix,log::add);
            assertEquals(1,work.toFile().listFiles(
                    (folder,name)->name.startsWith(".prefix-incomplete-backup-")).length);
        } finally {cleanWork(work);}
    }

    @Test public void concurrentBootsCommitOnlyOnePrefix() throws Exception {
        java.nio.file.Path work=java.nio.file.Files.createTempDirectory("drnas-prefix-race");
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.io.File packed=minimalPrefixPack(work);
            java.io.File prefix=work.resolve("prefix").toFile();
            java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Void> call=()->{
                start.await();
                WcpRuntimeStager.unpackPrefixPack(packed,prefix,msg->{});
                return null;
            };
            java.util.concurrent.Future<Void> first=pool.submit(call),second=pool.submit(call);
            start.countDown();
            first.get(30,java.util.concurrent.TimeUnit.SECONDS);
            second.get(30,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(true,new java.io.File(prefix,"drnas-prefix.ready").isFile());
            assertEquals(0,work.toFile().listFiles(
                    (folder,name)->name.startsWith(".prefix-incomplete-backup-")).length);
        } finally {pool.shutdownNow();cleanWork(work);}
    }

    @Test public void failedExtractionDoesNotMoveIncompletePrefix() throws Exception {
        java.nio.file.Path work=java.nio.file.Files.createTempDirectory("drnas-prefix-failure");
        try {
            java.io.File broken=work.resolve("broken.txz").toFile();
            writeText(broken.toPath(),"not a tar.xz");
            java.io.File prefix=work.resolve("prefix").toFile();
            java.nio.file.Files.createDirectory(prefix.toPath());
            writeText(prefix.toPath().resolve("sentinel.txt"),"preserve");
            assertThrows(IOException.class,()->WcpRuntimeStager.unpackPrefixPack(
                    broken,prefix,msg->{}));
            assertEquals("preserve",readText(
                    prefix.toPath().resolve("sentinel.txt")));
            assertEquals(0,work.toFile().listFiles(
                    (folder,name)->name.startsWith(".prefix-incomplete-backup-")).length);
        } finally {cleanWork(work);}
    }

    @Test public void neverFollowsExistingPrefixSymlink() throws Exception {
        java.nio.file.Path work=java.nio.file.Files.createTempDirectory("drnas-prefix-symlink");
        try {
            java.io.File packed=minimalPrefixPack(work);
            java.nio.file.Path outside=work.resolve("outside");
            java.nio.file.Files.createDirectory(outside);
            writeText(outside.resolve("sentinel.txt"),"untouched");
            java.io.File prefix=work.resolve("prefix").toFile();
            java.nio.file.Files.createSymbolicLink(prefix.toPath(),outside);
            assertThrows(IOException.class,()->WcpRuntimeStager.unpackPrefixPack(
                    packed,prefix,msg->{}));
            assertEquals("untouched",readText(
                    outside.resolve("sentinel.txt")));
        } finally {cleanWork(work);}
    }

}

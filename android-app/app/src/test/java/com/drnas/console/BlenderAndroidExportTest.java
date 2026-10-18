package com.drnas.console;

import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.Assert.*;

public final class BlenderAndroidExportTest {
    @Test public void discoversOnlySavedBlendProjectsInWineUserFolders() throws Exception {
        Path prefix=Files.createTempDirectory("blender-save-prefix");
        try {
            Path docs=Files.createDirectories(prefix.resolve(
                    "drive_c/users/xuser/Documents/Blender"));
            Path desktop=Files.createDirectories(prefix.resolve("drive_c/users/xuser/Desktop"));
            Files.write(docs.resolve("Test.blend"),"BLeNDER-v280".getBytes(StandardCharsets.US_ASCII));
            Files.write(docs.resolve("Test.blend1"),"backup".getBytes(StandardCharsets.US_ASCII));
            Files.write(desktop.resolve("Another.blend"),"BLeNDER-v280".getBytes(StandardCharsets.US_ASCII));
            List<BlenderAndroidExport.Project> projects=BlenderAndroidExport.projects(prefix.toFile());
            assertEquals(2,projects.size());
            assertTrue(projects.stream().anyMatch(p->p.file.getName().equals("Test.blend")
                    && p.guestRelativePath.contains("Documents")));
            assertTrue(projects.stream().anyMatch(p->p.file.getName().equals("Another.blend")));
        } finally {delete(prefix);}
    }

    @Test public void neverEscapesWineDriveThroughSymlink() throws Exception {
        Path prefix=Files.createTempDirectory("blender-save-prefix");
        Path outside=Files.createTempDirectory("blender-save-foreign");
        try {
            Path docs=Files.createDirectories(prefix.resolve("drive_c/users/xuser/Documents"));
            Files.write(outside.resolve("private.blend"),"private".getBytes(StandardCharsets.US_ASCII));
            Files.createSymbolicLink(docs.resolve("external"),outside);
            assertTrue(BlenderAndroidExport.projects(prefix.toFile()).isEmpty());
        } finally {delete(prefix);delete(outside);}
    }

    private static void delete(Path target) throws Exception {
        Files.walkFileTree(target,new java.nio.file.SimpleFileVisitor<Path>() {
            @Override public java.nio.file.FileVisitResult visitFile(Path file,
                    java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                Files.delete(file);return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override public java.nio.file.FileVisitResult postVisitDirectory(Path dir,
                    java.io.IOException error) throws java.io.IOException {
                if(error!=null)throw error;
                Files.delete(dir);return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
}

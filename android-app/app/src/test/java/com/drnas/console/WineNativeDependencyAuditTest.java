package com.drnas.console;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class WineNativeDependencyAuditTest {
    @Test public void exactLibraryNameOnly() throws Exception {
        File dir=Files.createTempDirectory("drnas-audit").toFile();
        try {
            File library=new File(dir,"libvulkan.so.1");
            assertTrue(library.createNewFile());
            assertTrue(WineNativeDependencyAudit.existsOnPath(dir.getAbsolutePath(),"libvulkan.so.1"));
            assertFalse(WineNativeDependencyAudit.existsOnPath(dir.getAbsolutePath(),"libGL.so.1"));
            assertFalse(WineNativeDependencyAudit.existsOnPath(dir.getAbsolutePath(),"../libvulkan.so.1"));
            assertFalse(WineNativeDependencyAudit.existsOnPath("", "libvulkan.so.1"));
        } finally {
            for(File f:dir.listFiles())f.delete();
            dir.delete();
        }
    }
}

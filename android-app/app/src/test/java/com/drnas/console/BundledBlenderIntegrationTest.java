package com.drnas.console;

import org.junit.Test;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import static org.junit.Assert.*;

public class BundledBlenderIntegrationTest {
    @Test public void integrationRefreshIsIdempotentAndDoesNotModifyOtherFiles() throws Exception {
        Path dir=Files.createTempDirectory("drnas-blender-integration");
        Path script=dir.resolve("drnas-startup.py");
        Path userPreferences=dir.resolve("userpref.blend");
        Files.write(userPreferences,new byte[]{3,7,12});
        byte[] first="print('r50')\n".getBytes(StandardCharsets.UTF_8);
        byte[] next="print('r51')\n".getBytes(StandardCharsets.UTF_8);
        assertTrue(BundledBlender.writeIfChanged(script,first));
        Files.setLastModifiedTime(script,FileTime.fromMillis(123456789000L));
        assertFalse(BundledBlender.writeIfChanged(script,first));
        assertEquals(FileTime.fromMillis(123456789000L),Files.getLastModifiedTime(script));
        assertTrue(BundledBlender.writeIfChanged(script,next));
        assertArrayEquals(next,Files.readAllBytes(script));
        assertArrayEquals(new byte[]{3,7,12},Files.readAllBytes(userPreferences));
    }

    @Test public void refusesSymlinkInPlaceOfAppOwnedScript() throws Exception {
        Path dir=Files.createTempDirectory("drnas-blender-symlink");
        Path unrelated=dir.resolve("user.blend");
        Files.write(unrelated,new byte[]{9,8,7});
        Path link=dir.resolve("drnas-window-layout.py");
        try {
            Files.createSymbolicLink(link,unrelated.getFileName());
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
            return;
        }
        try {
            BundledBlender.writeIfChanged(link,new byte[]{1,2,3});
            fail("Symlink write must be blocked");
        } catch (IOException expected) {
            assertArrayEquals(new byte[]{9,8,7},Files.readAllBytes(unrelated));
        }
    }
}

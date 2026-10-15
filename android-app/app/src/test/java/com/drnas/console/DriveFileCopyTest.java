package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;

public class DriveFileCopyTest {
    @Test public void completeImportPreservesExistingWineFile() throws Exception {
        Path dir=Files.createTempDirectory("drive-copy");File target=dir.resolve("game.pak").toFile();
        try {
            assertEquals(3,DriveFileCopy.copy(new ByteArrayInputStream(new byte[]{1,2,3}),target,3,()->Long.MAX_VALUE));
            assertThrows(IOException.class,()->DriveFileCopy.copy(new ByteArrayInputStream(new byte[]{4}),target,1,()->Long.MAX_VALUE));
            assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(target.toPath()));
        } finally {Files.deleteIfExists(target.toPath());Files.delete(dir);}
    }
    @Test public void truncatedProviderAndLowStorageLeaveNoPartialGameFile() throws Exception {
        Path dir=Files.createTempDirectory("drive-failure");File target=dir.resolve("game.pak").toFile();
        try {
            assertThrows(IOException.class,()->DriveFileCopy.copy(new ByteArrayInputStream(new byte[3]),target,8,()->Long.MAX_VALUE));
            assertFalse(target.exists());assertEquals(0,dir.toFile().list().length);
            assertThrows(IOException.class,()->DriveFileCopy.copy(new ByteArrayInputStream(new byte[3]),target,3,()->0L));
            assertFalse(target.exists());assertEquals(0,dir.toFile().list().length);
        } finally {Files.delete(dir);}
    }
    @Test public void interruptionCleansTemporaryFileAndCanResume() throws Exception {
        Path dir=Files.createTempDirectory("drive-interrupted");File target=dir.resolve("game.pak").toFile();
        try {
            Thread.currentThread().interrupt();
            assertThrows(InterruptedIOException.class,()->DriveFileCopy.copy(new ByteArrayInputStream(new byte[3]),target,3,()->Long.MAX_VALUE));
            Thread.interrupted();
            assertFalse(target.exists());assertEquals(0,dir.toFile().list().length);
            assertEquals(3,DriveFileCopy.copy(new ByteArrayInputStream(new byte[3]),target,-1,()->Long.MAX_VALUE));
        } finally {Thread.interrupted();Files.deleteIfExists(target.toPath());Files.delete(dir);}
    }
}

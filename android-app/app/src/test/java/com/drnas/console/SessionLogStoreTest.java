package com.drnas.console;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SessionLogStoreTest {
    @Test public void exportsCompleteUtf8LogBeyondUiHistoryLimit() throws Exception {
        Path dir=Files.createTempDirectory("drnas-complete-session");
        try {
            try(SessionLogStore store=new SessionLogStore(dir.resolve("logs").toFile())) {
                StringBuilder expected=new StringBuilder();
                for(int i=0;i<1800;i++) {
                    String line="["+i+"] Wine PE č, ć, ž, ARM64EC ; extra test\n";
                    expected.append(line);
                    store.appendLine(line);
                }
                assertTrue("Must exceed 30k UI text truncation",store.size()>30000);
                ByteArrayOutputStream result=new ByteArrayOutputStream();
                assertEquals(store.size(),store.copyTo(result));
                assertEquals(expected.toString(),new String(result.toByteArray(),StandardCharsets.UTF_8));
                assertTrue(store.filename().endsWith(".txt"));
                assertEquals(1,store.file().getParentFile().listFiles().length);
            }
        } finally {
            WineBootProbe.removeWithoutFollowingLinks(dir.toFile());
        }
    }
    @Test public void emptySessionCanBeExportedAndClosesSafely() throws Exception {
        Path dir=Files.createTempDirectory("drnas-empty-session");
        try {
            SessionLogStore store=new SessionLogStore(dir.toFile());
            ByteArrayOutputStream result=new ByteArrayOutputStream();
            assertEquals(0,store.copyTo(result));
            store.close();
            store.close();
            assertThrows(java.io.IOException.class,()->store.appendLine("after closed\n"));
        } finally {
            WineBootProbe.removeWithoutFollowingLinks(dir.toFile());
        }
    }
}

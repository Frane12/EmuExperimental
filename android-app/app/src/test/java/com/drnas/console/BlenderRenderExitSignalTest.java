package com.drnas.console;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public final class BlenderRenderExitSignalTest {
    @Test public void boundedAtomicRenderExitCommand() throws Exception {
        Path folder=Files.createTempDirectory("drnas-render-command");
        try {
            Path target=folder.resolve("drnas-render-exit.signal");
            BlenderRenderExitSignal.write(target,1);
            assertEquals("r62:1",new String(Files.readAllBytes(target),StandardCharsets.US_ASCII));
            BlenderRenderExitSignal.write(target,2);
            assertEquals("r62:2",new String(Files.readAllBytes(target),StandardCharsets.US_ASCII));
            assertFalse(Files.exists(folder.resolve("drnas-render-exit.signal.tmp")));
            assertThrows(IllegalArgumentException.class,()->BlenderRenderExitSignal.encode(0));
        } finally {
            Files.deleteIfExists(folder.resolve("drnas-render-exit.signal"));
            Files.deleteIfExists(folder);
        }
    }
}

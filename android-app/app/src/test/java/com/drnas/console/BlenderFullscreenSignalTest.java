package com.drnas.console;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlenderFullscreenSignalTest {
    @Test public void readsMonotonicSafeCommands() throws Exception {
        Path dir=Files.createTempDirectory("drnas-fs");
        Path signal=dir.resolve("drnas-host-fullscreen.signal");
        assertEquals(0,BlenderFullscreenSignal.readSequence(signal));
        Files.write(signal,"r57:1\\n".getBytes(StandardCharsets.US_ASCII));
        assertEquals(1,BlenderFullscreenSignal.readSequence(signal));
        Files.write(signal,"r57:3\\n".getBytes(StandardCharsets.US_ASCII));
        assertEquals(3,BlenderFullscreenSignal.readSequence(signal));
        BlenderFullscreenSignal.clearStale(signal);
        assertEquals(0,BlenderFullscreenSignal.readSequence(signal));
    }
    @Test public void refusesMalformedAndOversizedSignals() throws Exception {
        Path dir=Files.createTempDirectory("drnas-fs-bad");
        Path signal=dir.resolve("drnas-host-fullscreen.signal");
        for(String bad:new String[]{"toggle","r56:1","r57:0","r57:-2","r57:10000000","r57:1\\ncommand"}) {
            Files.write(signal,bad.getBytes(StandardCharsets.US_ASCII));
            try{BlenderFullscreenSignal.readSequence(signal);fail(bad);}catch(java.io.IOException expected){}
        }
        // Symlinks are not read, including links to real files.
        Path target=dir.resolve("target");
        Files.write(target,"r57:1".getBytes(StandardCharsets.US_ASCII));
        Path symlink=dir.resolve("linked");
        Files.createSymbolicLink(symlink,target);
        assertEquals(0,BlenderFullscreenSignal.readSequence(symlink));
        BlenderFullscreenSignal.clearStale(symlink);
        assertTrue(Files.exists(target));
    }
}

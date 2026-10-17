package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared file protocol ABI: native XInput PE DLL reads exactly these offsets. */
public final class XInputSharedStateTest {
    @Test public void virtualPadPublishesAtomicXboxState() throws Exception {
        Path root=Files.createTempDirectory("drnas-xinput");
        File stateFile=root.resolve("input.bin").toFile();
        try(XInputSharedState state=new XInputSharedState(stateFile)) {
            ByteBuffer b=state.snapshotForTest();
            assertEquals(64,b.remaining());
            assertEquals(XInputSharedState.MAGIC,b.getInt(0));
            assertEquals(1,b.getInt(4));
            assertEquals(0,b.getInt(24)); // disconnected by default
            state.setVirtualEnabled(true);
            b=state.snapshotForTest();
            assertEquals(1,b.getInt(24)); // a game now sees player 0
            int before=b.getInt(28);
            state.setTouchLeft(-1f,1f);
            state.setTouchRight(.5f,-.5f);
            state.setTouchButton(XInputSharedState.A,true);
            state.setTouchTrigger(true,255);
            b=state.snapshotForTest();
            assertEquals(-32768,b.getShort(16));
            assertEquals(-32768,b.getShort(18));
            assertTrue(b.getShort(20)>0);
            assertTrue(b.getShort(22)>0);
            assertEquals(XInputSharedState.A,b.getShort(12)&65535);
            assertEquals(255,b.get(14)&255);
            assertTrue(b.getInt(28)>before);
            assertEquals(0,b.getInt(8)&1); // reader never gets odd committed seq
            before=b.getInt(28);
            state.setTouchButton(XInputSharedState.A,true);
            assertEquals(before,state.snapshotForTest().getInt(28));
            state.releaseTouch();
            assertEquals(0,state.snapshotForTest().getShort(12));
            state.setVirtualEnabled(false);
            assertEquals(0,state.snapshotForTest().getInt(24));
        }finally{Files.deleteIfExists(stateFile.toPath());Files.deleteIfExists(root);}
    }
    @Test public void physicalControllerWorksWithoutTouchOverlay() throws Exception {
        Path root=Files.createTempDirectory("drnas-physical-xinput");
        Path file=root.resolve("input.bin");
        try(XInputSharedState state=new XInputSharedState(file.toFile())) {
            state.setPhysicalButton(XInputSharedState.B,true);
            state.setPhysicalAxis(1f,0f,0f,-1f,.25f,1f);
            ByteBuffer b=state.snapshotForTest();
            assertEquals(1,b.getInt(24));
            assertEquals(XInputSharedState.B,b.getShort(12)&65535);
            assertEquals(32767,b.getShort(16));
            assertEquals(32767,b.getShort(22));
            assertEquals(255,b.get(15)&255);
            state.disconnectPhysical();
            assertEquals(0,state.snapshotForTest().getInt(24));
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(root);}
    }
}

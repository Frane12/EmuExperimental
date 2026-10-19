package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public class BlenderDisplayProfileTest {
    @Test public void a810AutomaticallyUsesTheLowerFramebuffer() {
        BlenderDisplayProfile.Resolution r=BlenderDisplayProfile.choose("Qualcomm Adreno (TM) 810","auto");
        assertEquals(960,r.width);assertEquals(544,r.height);
    }
    @Test public void strongerAndUnknownDevicesKeepTheExistingDefault() {
        for(String gpu:new String[]{"Adreno 830","Adreno 840","Adreno 740","Adreno 8100","Mali-G52",""})
            assertSame(BlenderDisplayProfile.HD,BlenderDisplayProfile.choose(gpu,"auto"));
    }
    @Test public void menuChoiceOverridesAutomaticDetection() {
        assertSame(BlenderDisplayProfile.HD,BlenderDisplayProfile.choose("Adreno 810","1280x720"));
        assertSame(BlenderDisplayProfile.LOW,BlenderDisplayProfile.choose("Adreno 840","960x544"));
        assertSame(BlenderDisplayProfile.LOW,BlenderDisplayProfile.choose("Adreno 810","invalid"));
    }
}

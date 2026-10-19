package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public class BlenderMaliG57LabTest {
    @Test public void exactXiaomiModelAndMaliG57SelectOpenGlOnlyInAuto() {
        assertTrue(BlenderMaliG57Lab.useOpenGl("24117RN76E", "Mali-G57 MC2", "auto", ""));
        assertTrue(BlenderMaliG57Lab.useOpenGl(" 24117rn76e ", "ARM Mali G57 MC2", "auto", "--factory-startup"));
        assertTrue(BlenderMaliG57Lab.useOpenGl("24117RN76E", "", "auto", ""));
    }
    @Test public void excludesAllOtherDevicesAndWrongGpus() {
        assertFalse(BlenderMaliG57Lab.useOpenGl("24117RN76E", "Adreno 840", "auto", ""));
        assertFalse(BlenderMaliG57Lab.useOpenGl("24117RN76E", "Mali-G610", "auto", ""));
        assertFalse(BlenderMaliG57Lab.useOpenGl("24117RN76E", "Mali-G570", "auto", ""));
        assertFalse(BlenderMaliG57Lab.useOpenGl("Nothing A142", "Mali-G57 MC2", "auto", ""));
        assertFalse(BlenderMaliG57Lab.useOpenGl("24117RN76E", "Mali-G57", "vulkan", ""));
        assertFalse(BlenderMaliG57Lab.useOpenGl("24117RN76E", "Mali-G57", "opengl", ""));
    }
    @Test public void respectsExplicitUserBackendAndMalformedArgs() {
        for (String args : new String[]{
                "--gpu-backend vulkan", "--gpu-backend=opengl",
                "--gpu-backend \"vulkan\"", "\"broken"}) {
            assertFalse(BlenderMaliG57Lab.useOpenGl("24117RN76E", "Mali-G57", "auto", args));
        }
    }
}

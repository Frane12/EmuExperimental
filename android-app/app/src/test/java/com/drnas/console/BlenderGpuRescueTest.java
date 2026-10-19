package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public class BlenderGpuRescueTest {
    @Test public void a142MaliG610UsesIsolatedOpenGlOnlyInAuto() {
        assertTrue(BlenderGpuRescue.useOpenGl("Nothing A142", "Mali-G610 MC4", "auto", ""));
        assertTrue(BlenderGpuRescue.useOpenGl("A142", "ARM Mali-G610 MC4", "auto", ""));
        assertTrue(BlenderGpuRescue.useOpenGl("Nothing A142", "", "auto", ""));
    }
    @Test public void otherModelsAndGraphicsRemainUntouched() {
        assertFalse(BlenderGpuRescue.useOpenGl("Nothing A142", "Adreno 840", "auto", ""));
        assertFalse(BlenderGpuRescue.useOpenGl("Nothing A142", "Mali-G52 MC2", "auto", ""));
        assertFalse(BlenderGpuRescue.useOpenGl("Poco F8 Ultra", "Mali-G610", "auto", ""));
        assertFalse(BlenderGpuRescue.useOpenGl("Nothing A142", "Mali-G610", "vulkan", ""));
        assertFalse(BlenderGpuRescue.useOpenGl("Nothing A142", "Mali-G610", "opengl", ""));
        assertFalse(BlenderGpuRescue.useOpenGl("Nothing A142", "Mali-G610", "dxvk", ""));
    }
    @Test public void usersExplicitBackendWins() {
        assertFalse(BlenderGpuRescue.useOpenGl("Nothing A142", "Mali-G610", "auto",
                "--gpu-backend vulkan"));
        assertFalse(BlenderGpuRescue.useOpenGl("Nothing A142", "Mali-G610", "auto",
                "--gpu-backend=vulkan"));
        assertFalse(BlenderGpuRescue.useOpenGl("Nothing A142", "Mali-G610", "auto",
                "--gpu-backend \"vulkan\""));
        assertTrue(BlenderGpuRescue.useOpenGl("Nothing A142", "Mali-G610", "auto",
                "--factory-startup"));
        assertFalse(BlenderGpuRescue.useOpenGl("Nothing A142", "Mali-G610", "auto",
                "\"broken"));
    }
}

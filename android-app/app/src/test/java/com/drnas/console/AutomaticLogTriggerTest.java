package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutomaticLogTriggerTest {
    @Test public void capturesObservedFailureAndVulkanTimeout() {
        assertTrue(AutomaticLogTrigger.matches("[GUI Wine] XIO:  fatal IO error 61 (No data available)"));
        assertTrue(AutomaticLogTrigger.matches("Native Vulkan presentation FAILED (exit=-1); inspect [Wine Vulkan]."));
        assertTrue(AutomaticLogTrigger.matches("X11 client IO ended after opcode=18 bytes=24: java.net.SocketException: Connection reset"));
        assertTrue(AutomaticLogTrigger.matches("X11 request FAILED opcode=98 bytes=12: java.lang.IllegalStateException"));
    }
    @Test public void ignoresNormalProbesAndCleanShutdown() {
        assertFalse(AutomaticLogTrigger.matches("X11 client IO ended after opcode=43 bytes=0: java.io.IOException"));
        assertFalse(AutomaticLogTrigger.matches("GUI stopped by user/lifecycle."));
        assertFalse(AutomaticLogTrigger.matches("X11 Vulkan clear/present=0"));
    }
}

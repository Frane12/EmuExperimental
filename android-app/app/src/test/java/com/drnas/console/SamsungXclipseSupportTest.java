package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public class SamsungXclipseSupportTest {
    @Test public void recognizesSamsungXclipse940() {
        assertTrue(SamsungXclipseSupport.isXclipse940("Samsung Xclipse 940"));
        assertTrue(SamsungXclipseSupport.isXclipse940("Xclipse-940 (AMD RDNA 3)"));
        assertTrue(SamsungXclipseSupport.isXclipse940("xclipse_940"));
    }
    @Test public void neverMisidentifiesAdrenoOrMali() {
        assertFalse(SamsungXclipseSupport.isXclipse940(null));
        assertFalse(SamsungXclipseSupport.isXclipse940("Adreno 840"));
        assertFalse(SamsungXclipseSupport.isXclipse940("Mali-G52"));
        assertFalse(SamsungXclipseSupport.isXclipse940("Xclipse 920"));
        assertFalse(SamsungXclipseSupport.isXclipse940("Xclipse 9400"));
    }
    @Test public void systemVulkanOnlyAndNoTurnip() {
        assertEquals("SAMSUNG_XCLIPSE_940_SYSTEM_VULKAN",
                SamsungXclipseSupport.backend("Xclipse 940"));
        assertEquals("",TurnipDevicePolicy.target("Samsung Xclipse 940"));
        assertTrue(GraphicsDriverCatalog.compatible(
                TurnipDevicePolicy.target("Samsung Xclipse 940"),35).isEmpty());
    }
}

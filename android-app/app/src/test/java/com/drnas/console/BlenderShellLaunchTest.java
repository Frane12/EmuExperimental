package com.drnas.console;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class BlenderShellLaunchTest {
    @Test public void startsVulkanWithoutForcingAnyBlenderWindowMode() throws Exception {
        List<String> a=BlenderShellLaunch.arguments("","auto");
        assertEquals("vulkan",a.get(a.indexOf("--gpu-backend")+1));
        assertFalse(a.contains("--window-fullscreen"));
        assertFalse(a.contains("--window-border"));
        assertFalse(a.contains("--window-maximized"));
        assertFalse(a.contains("-W"));
        assertFalse(a.contains("-w"));
        assertFalse(a.contains("-M"));
        assertEquals(BundledBlenderArchive.WINDOWS_SETUP,a.get(a.indexOf("--python")+1));
    }
    @Test public void retainsChosenBackendAndAvoidsDuplicateFlags() throws Exception {
        List<String> a=BlenderShellLaunch.arguments("--gpu-backend opengl -W --python C:\\custom.py","auto");
        assertEquals(1,java.util.Collections.frequency(a,"--gpu-backend"));
        assertEquals("opengl",a.get(a.indexOf("--gpu-backend")+1));
        assertFalse(a.contains("--window-fullscreen"));
        assertEquals(1,java.util.Collections.frequency(a,"--python"));
    }
    @Test public void explicitFullscreenAndBorderAreRespectedButNeverAutoInjected() throws Exception {
        List<String> fullscreen=BlenderShellLaunch.arguments("--window-fullscreen","auto");
        assertEquals(1,java.util.Collections.frequency(fullscreen,"--window-fullscreen"));
        assertFalse(fullscreen.contains("--window-border"));
        List<String> bordered=BlenderShellLaunch.arguments("--window-border","auto");
        assertEquals(1,java.util.Collections.frequency(bordered,"--window-border"));
        assertFalse(bordered.contains("--window-fullscreen"));
    }
    @Test public void explicitOpenGlProfileSurvivesShell() throws Exception {
        List<String> a=BlenderShellLaunch.arguments("","opengl");
        assertEquals("opengl",a.get(a.indexOf("--gpu-backend")+1));
    }
    @Test public void fileWithSpacesRemainsOneArgument() throws Exception {
        List<String> a=BlenderShellLaunch.arguments("\"C:\\My Projects\\demo.blend\"","auto");
        assertTrue(a.contains("C:\\My Projects\\demo.blend"));
    }
}


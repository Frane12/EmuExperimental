package com.drnas.console;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class BlenderShellLaunchTest {
    @Test public void startsTestedBackendFullscreenAndPortableSetup() throws Exception {
        List<String> a=BlenderShellLaunch.arguments("","auto");
        assertEquals("vulkan",a.get(a.indexOf("--gpu-backend")+1));
        assertTrue(a.contains("--window-fullscreen"));
        assertEquals(BundledBlenderArchive.WINDOWS_SETUP,a.get(a.indexOf("--python")+1));
    }
    @Test public void retainsChosenBackendAndAvoidsDuplicateFlags() throws Exception {
        List<String> a=BlenderShellLaunch.arguments("--gpu-backend opengl -W --python C:\\custom.py","auto");
        assertEquals(1,java.util.Collections.frequency(a,"--gpu-backend"));
        assertEquals("opengl",a.get(a.indexOf("--gpu-backend")+1));
        assertFalse(a.contains("--window-fullscreen"));
        assertEquals(1,java.util.Collections.frequency(a,"--python"));
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

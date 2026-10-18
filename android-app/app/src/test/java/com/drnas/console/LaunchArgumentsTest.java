package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class LaunchArgumentsTest {
    @Test public void blenderVulkanIsAutomaticallySelectedOnlyForBlender() throws Exception {
        assertEquals(Arrays.asList("--gpu-backend","vulkan"),
                LaunchArguments.forApp("/wine/R Folder/Blender.exe","",true));
        assertEquals(Collections.emptyList(),
                LaunchArguments.forApp("/wine/R Folder/Blender.exe","",false));
        assertEquals(Collections.emptyList(),
                LaunchArguments.forApp("/wine/Other.exe","",true));
    }
    @Test public void explicitBackendAndOtherArgumentsArePreserved() throws Exception {
        assertEquals(Arrays.asList("--gpu-backend","opengl","--factory-startup"),
                LaunchArguments.forApp("/some/blender.exe","--gpu-backend opengl --factory-startup",true));
        assertEquals(Arrays.asList("--config","A B.txt"),
                LaunchArguments.parse("--config \"A B.txt\""));
        assertEquals(Arrays.asList("--path","C:\\Users\\user\\test.blend"),
                LaunchArguments.parse("--path C:\\Users\\user\\test.blend"));
    }
    @Test public void malformedInputsFailInsteadOfEnteringWindowsShell() throws Exception {
        assertThrows(IOException.class,()->LaunchArguments.parse("--path \"unclosed"));
        assertThrows(IOException.class,()->LaunchArguments.parse("a\nb"));
        assertThrows(IOException.class,()->LaunchArguments.parse("x".repeat(401)));
    }

    @Test public void nativeAndCustomParametersAreForwardedForRealLinks() throws Exception {
        String link="C:\\users\\xuser\\Desktop\\Blender 4.5.lnk";
        assertEquals(Collections.emptyList(),LaunchArguments.forLink(link,"",false));
        assertEquals(Arrays.asList(link,"vulkan"),
                LaunchArguments.forLink(link,"",true));
        assertEquals(Arrays.asList(link,"vulkan","--gpu-backend","opengl","--factory-startup"),
                LaunchArguments.forLink(link,"--gpu-backend opengl --factory-startup",true));
        assertEquals(Arrays.asList(link,"auto","--factory-startup","--python","A B.py"),
                LaunchArguments.forLink(link,"--factory-startup --python \"A B.py\"",false));
        assertThrows(IOException.class,()->LaunchArguments.forLink(
                "/data/user/0/hijack.lnk","--gpu-backend vulkan",true));
        assertThrows(IOException.class,()->LaunchArguments.forLink(
                link,"--gpu-backend \"unfinished",true));
    }
    @Test public void manuallySelectedVulkanBackendConfiguresNativeBridge() throws Exception {
        assertTrue(LaunchArguments.explicitlyRequestsVulkan("--gpu-backend vulkan"));
        assertTrue(LaunchArguments.explicitlyRequestsVulkan("--gpu-backend=vulkan"));
        assertFalse(LaunchArguments.explicitlyRequestsVulkan("--gpu-backend opengl"));
        assertFalse(LaunchArguments.explicitlyRequestsVulkan("--factory-startup"));
    }
}

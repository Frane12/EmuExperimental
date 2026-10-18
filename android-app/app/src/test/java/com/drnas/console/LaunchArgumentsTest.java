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
}

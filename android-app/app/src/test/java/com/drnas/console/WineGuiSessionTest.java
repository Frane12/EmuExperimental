package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.util.*;

public class WineGuiSessionTest {
    @Test public void guiLaunchIsBuiltinWinecfgInVirtualDesktop() {
        List<String> args=WineGuiSession.command(new File("/system/bin/linker64"),new File("/data/app/wine"));
        assertEquals(Arrays.asList("/system/bin/linker64","/data/app/wine",
                "explorer.exe","/desktop=DRNAS,1024x768","winecfg.exe"),args);
    }
    @Test public void softwareDisplayDoesNotEnableCustomVulkan() {
        Map<String,String> env=new HashMap<>();WineGuiSession.displayEnvironment(env);
        assertEquals("127.0.0.1:12.0",env.get("DISPLAY"));
        assertTrue(env.get("WINEDLLOVERRIDES").contains("winex11.drv=b"));
        assertFalse(env.containsKey("VK_ICD_FILENAMES"));
    }
}

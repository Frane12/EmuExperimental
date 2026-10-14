package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.util.*;

public class WineGuiSessionTest {
    private static final File LINKER=new File("/system/bin/linker64");
    private static final File WINE=new File("/data/app/wine");

    @Test public void startsExplorerDesktopAt1280x720ByDefault() {
        assertEquals(1280,WineGuiSession.DESKTOP_WIDTH);
        assertEquals(720,WineGuiSession.DESKTOP_HEIGHT);
        assertEquals(Arrays.asList("/system/bin/linker64","/data/app/wine",
                "explorer.exe","/desktop=shell,1280x720","winefile.exe"),
                WineGuiSession.command(LINKER,WINE));
    }
    @Test public void oldWinecfgAndWinefileRemainExplicitOnly() {
        assertEquals(Arrays.asList("/system/bin/linker64","/data/app/wine",
                "explorer.exe","/desktop=shell,1280x720","winecfg.exe"),
                WineGuiSession.command(LINKER,WINE,"config"));
        assertEquals(Arrays.asList("/system/bin/linker64","/data/app/wine",
                "explorer.exe","/desktop=shell,1280x720","winefile.exe"),
                WineGuiSession.command(LINKER,WINE,"files"));
    }
    @Test public void rejectsUnknownGuestEntrypoint() {
        assertThrows(IllegalArgumentException.class,
                ()->WineGuiSession.command(LINKER,WINE,"arbitrary.exe"));
    }
    @Test public void softwareDisplayDoesNotEnableCustomVulkan() {
        Map<String,String> env=new HashMap<>();
        WineGuiSession.displayEnvironment(env);
        assertEquals("127.0.0.1:12.0",env.get("DISPLAY"));
        assertTrue(env.get("WINEDLLOVERRIDES").contains("winex11.drv=b"));
        assertFalse(env.containsKey("VK_ICD_FILENAMES"));
        assertEquals("-all",env.get("WINEDEBUG"));
    }
}


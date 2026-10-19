package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.util.*;

public class WineGuiSessionTest {
    private static final File LINKER=new File("/system/bin/linker64");
    private static final File WINE=new File("/data/app/wine");
    @Test public void directBlenderLaunchStartsTheWindowMappingWatchdog() {
        assertTrue(WineGuiSession.isLaunchMarker("Launching Blender directly without Explorer desktop/taskbar"));
        assertTrue(WineGuiSession.isLaunchMarker("Launching Wine explorer.exe virtual desktop"));
        assertFalse(WineGuiSession.isLaunchMarker("Blender archive staged"));
        assertFalse(WineGuiSession.isLaunchMarker(null));
    }
    @Test public void blenderVulkanBridgeIsRequiredOnAdrenoEvenWithDefaultProfile() {
        assertTrue(WineGuiSession.needsNativeWineVulkan(true,false,false));
        assertTrue(WineGuiSession.needsNativeWineVulkan(true,true,false));
        assertFalse(WineGuiSession.needsNativeWineVulkan(true,false,true));
        assertFalse(WineGuiSession.needsNativeWineVulkan(false,false,false));
        assertTrue(WineGuiSession.needsNativeWineVulkan(false,true,false));
    }
    @Test public void blenderStartsDirectlyWithoutExplorerDesktop() {
        List<String> args=WineGuiSession.blenderCommand(LINKER,WINE,
                List.of("--gpu-backend","vulkan","--window-fullscreen"));
        assertEquals(List.of("/system/bin/linker64","/data/app/wine",
                BundledBlenderArchive.WINDOWS_EXE,"--gpu-backend","vulkan","--window-fullscreen"),args);
        assertFalse(args.contains("explorer.exe"));
        assertFalse(args.stream().anyMatch(a->a.startsWith("/desktop=")));
    }

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
    @Test public void taskManagerJoinsSameShellDesktop() {
        assertEquals(Arrays.asList("/system/bin/linker64","/data/app/wine",
                "explorer.exe","/desktop=shell,1280x720","taskmgr.exe"),
                WineGuiSession.command(LINKER,WINE,"tasks"));
    }
    @Test public void cmdUsesDosSystemPathNotHostWorkingDirectory() {
        assertEquals("C:\\windows\\system32\\cmd.exe",WineShortcuts.builtinExecutable("cmd"));
        assertEquals(Arrays.asList("/system/bin/linker64","/data/app/wine",
                "explorer.exe","/desktop=shell,1280x720",
                "C:\\windows\\system32\\cmd.exe"),
                WineGuiSession.command(LINKER,WINE,"builtin:cmd"));
    }
    @Test public void launchesProgramsThroughGuestDrivePathsNotUnixAbsolutePaths() throws Exception {
        java.nio.file.Path root=java.nio.file.Files.createTempDirectory("drnas-wine-drive-");
        try {
            java.nio.file.Path folder=java.nio.file.Files.createDirectories(root.resolve("Blender 4.1"));
            File blender=folder.resolve("blender.exe").toFile();
            assertTrue(blender.createNewFile());
            String guest=WineGuiSession.windowsPath(root.toFile(),blender,'R');
            assertEquals("R:\\Blender 4.1\\blender.exe",guest);
            assertEquals(Arrays.asList("/system/bin/linker64","/data/app/wine",
                    "explorer.exe","/desktop=shell,1280x720",guest),
                    WineGuiSession.command(LINKER,WINE,"execwin:"+guest));
            File fake=File.createTempFile("outside-", ".exe");
            try {
                assertThrows(java.io.IOException.class,
                        ()->WineGuiSession.windowsPath(root.toFile(),fake,'R'));
            } finally {assertTrue(fake.delete());}
        } finally {
            java.nio.file.Files.walk(root).sorted(Comparator.reverseOrder())
                    .forEach(p->{try {java.nio.file.Files.deleteIfExists(p);}catch(Exception ignored){}});
        }
    }
    @Test public void nativeVulkanLaunchUsesGuestDosPathAndSeparateArgs() throws Exception {
        String path="R:\\Blender 4.5\\blender.exe";
        assertEquals(Arrays.asList("/system/bin/linker64","/data/app/wine",
                "explorer.exe","/desktop=shell,1280x720",path,
                "--gpu-backend","vulkan"),
                WineGuiSession.command(LINKER,WINE,"execwin:"+path,
                LaunchArguments.forApp(path,"",true)));
    }
    @Test public void nativeDxvkPreferenceDoesNotFakeVulkan() {
        Map<String,String> env=new HashMap<>();
        WineGuiSession.displayEnvironment(env);
        assertEquals("127.0.0.1:12.0",env.get("DISPLAY"));
        assertTrue(env.get("WINEDLLOVERRIDES").contains("winex11.drv=b"));
        assertFalse(env.containsKey("VK_ICD_FILENAMES"));
        assertEquals("-all,err+all",env.get("WINEDEBUG"));
        assertTrue(env.get("WINEDLLOVERRIDES").contains("d3d9=n,b"));
        assertTrue(env.get("WINEDLLOVERRIDES").contains("dxgi=n,b"));
        assertTrue(env.get("WINEDLLOVERRIDES").contains("d3d11=n,b"));
    }
    @Test public void wineShellLinkFlagsLaunchRealHelperWithGuestWindowsPath() throws Exception {
        String link="C:\\users\\xuser\\Desktop\\Blender.lnk";
        java.util.List<String> flags=LaunchArguments.forLink(link,
                "--gpu-backend vulkan --factory-startup",true);
        assertEquals(Arrays.asList("/system/bin/linker64","/data/app/wine",
                "explorer.exe","/desktop=shell,1280x720",
                "C:\\DRNAS\\Tools\\DRNASLinkLaunch.exe",
                link,"vulkan","--gpu-backend","vulkan","--factory-startup"),
                WineGuiSession.command(LINKER,WINE,
                        "execwin:C:\\DRNAS\\Tools\\DRNASLinkLaunch.exe",flags));
        assertEquals(Arrays.asList("/system/bin/linker64","/data/app/wine",
                "explorer.exe","/desktop=shell,1280x720",link),
                WineGuiSession.command(LINKER,WINE,"linkwin:"+link,
                        LaunchArguments.forLink(link,"",false)));
    }
}



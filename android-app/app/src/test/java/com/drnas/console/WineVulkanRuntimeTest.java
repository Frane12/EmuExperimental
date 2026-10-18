package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

public class WineVulkanRuntimeTest {
    @Test public void VulkanOverridesAreScopedToNativeProfile() {
        Map<String,String> env=new HashMap<>();
        env.put("WINEDLLOVERRIDES","winex11.drv=b;d3d9=n,b");
        WineVulkanRuntime.configure(env,false);
        assertEquals("winex11.drv=b;d3d9=n,b",env.get("WINEDLLOVERRIDES"));
        WineVulkanRuntime.configure(env,true);
        assertEquals("winex11.drv=b;d3d9=n,b;vulkan-1=b;winevulkan=b",env.get("WINEDLLOVERRIDES"));
        assertEquals("NATIVE_WINEVULKAN",env.get("DRNAS_GRAPHICS_API"));
    }
    @Test public void PreflightDistinguishesBinariesFromWorkingGpu() throws Exception {
        File root=Files.createTempDirectory("drnas-winevulkan").toFile();
        File wineLib=new File(root,"wine-libs"),guiLib=new File(root,"gui-libs");
        File bridge=new File(wineLib,"wine/arm64ec-windows/winevulkan.dll");
        File loader=new File(guiLib,"libvulkan.so.1");
        try {
            assertTrue(bridge.getParentFile().mkdirs());assertTrue(guiLib.mkdirs());
            byte[] pe=new byte[600];pe[0]='M';pe[1]='Z';
            Files.write(bridge.toPath(),pe);
            Files.write(loader.toPath(),new byte[2048]);
            ArrayList<String> messages=new ArrayList<>();
            assertEquals(bridge.getCanonicalFile(),WineVulkanRuntime.requireBridge(wineLib,guiLib,messages::add));
            assertTrue(messages.stream().anyMatch(s->s.contains("file presence only")));
            Files.delete(bridge.toPath());
            assertThrows(java.io.IOException.class,()->WineVulkanRuntime.requireBridge(wineLib,guiLib,messages::add));
        } finally {
            try(java.util.stream.Stream<java.nio.file.Path> walk=Files.walk(root.toPath())) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
            }
        }
    }
}

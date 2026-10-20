package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class WineOpenGlRuntimeTest {
    @Test public void vulkanBlenderGetsLibGlWithoutEglOverrides() throws Exception {
        File lib=Files.createTempDirectory("drnas-blender-gl").toFile();
        try {
            for(String name:new String[]{"libGL.so.1","libEGL.so.1",
                    "libgallium-26.3.0-devel.so"})
                Files.write(new File(lib,name).toPath(),new byte[]{0x7f,'E','L','F'});
            Map<String,String> env=new HashMap<>();
            env.put("LD_LIBRARY_PATH","/native:/wine");
            env.put("VK_DRIVER_FILES","/chosen/turnip_icd.json");
            ArrayList<String> log=new ArrayList<>();
            WineOpenGlRuntime.exposeBlenderLibGl(lib,env,log::add);
            assertEquals(lib.getCanonicalPath()+":/native:/wine",env.get("LD_LIBRARY_PATH"));
            assertEquals("/chosen/turnip_icd.json",env.get("VK_DRIVER_FILES"));
            assertFalse(env.containsKey("WINE_USE_EGL"));
            assertFalse(env.containsKey("GALLIUM_DRIVER"));
            assertTrue(log.stream().anyMatch(s->s.contains("LIBGL_LINKER_READY")));
            WineOpenGlRuntime.exposeBlenderLibGl(lib,env,log::add);
            assertEquals(lib.getCanonicalPath()+":/native:/wine",env.get("LD_LIBRARY_PATH"));
        } finally { for(File file:lib.listFiles())file.delete(); lib.delete(); }
    }

    @Test public void refusesIncompleteMesaForVulkanBlender() throws Exception {
        File lib=Files.createTempDirectory("drnas-blender-gl-missing").toFile();
        try {
            Map<String,String> env=new HashMap<>();
            assertThrows(IOException.class,
                    ()->WineOpenGlRuntime.exposeBlenderLibGl(lib,env,s->{}));
            assertFalse(env.containsKey("LD_LIBRARY_PATH"));
        } finally {lib.delete();}
    }
}

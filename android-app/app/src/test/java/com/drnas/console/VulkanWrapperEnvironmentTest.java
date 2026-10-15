package com.drnas.console;

import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public final class VulkanWrapperEnvironmentTest {
    @Test public void wrapperIsTheIcdAndBackendSelectionIsSeparate() throws Exception {
        File root=Files.createTempDirectory("drnas wrapper ").toFile();
        File lib=new File(root,"lib");assertTrue(lib.mkdir());
        try {
            Files.write(new File(lib,"libvulkan_wrapper.so").toPath(),new byte[]{1});
            Files.write(new File(lib,"libmain_hook.so").toPath(),new byte[]{1});
            Map<String,String> env=new HashMap<>();
            env.put("VK_DRIVER_FILES","old-raw-turnip.json");
            env.put("ADRENOTOOLS_DRIVER_PATH","old-driver");
            env.put("ADRENOTOOLS_DRIVER_NAME","old.so");
            TurnipRuntime.configureWrapper(lib,env);
            JSONObject icd=new JSONObject(Files.readString(new File(env.get("VK_DRIVER_FILES")).toPath()));
            assertEquals(new File(lib,"libvulkan_wrapper.so").getAbsolutePath(),
                    icd.getJSONObject("ICD").getString("library_path"));
            assertEquals(env.get("VK_DRIVER_FILES"),env.get("VK_ICD_FILENAMES"));
            assertEquals(lib.getAbsolutePath()+"/",env.get("ADRENOTOOLS_HOOKS_PATH"));
            assertEquals("sw,noshm",env.get("MESA_VK_WSI_DEBUG"));
            assertFalse(env.containsKey("ADRENOTOOLS_DRIVER_PATH"));
            assertFalse(env.containsKey("ADRENOTOOLS_DRIVER_NAME"));
            assertEquals("0",env.get("WRAPPER_EMULATE_BCN"));
        } finally {
            try(java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(root.toPath())){
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception ignored){}});
            }
        }
    }
    @Test public void missingWrapperFailsBeforeSelectingAnyIcd() throws Exception {
        Map<String,String> env=new HashMap<>();
        File root=Files.createTempDirectory("drnas-missing-wrapper").toFile();
        try {
            try { TurnipRuntime.configureWrapper(root,env);fail("Missing wrapper accepted"); }
            catch(java.io.IOException expected) { assertFalse(env.containsKey("VK_DRIVER_FILES")); }
        } finally {Files.delete(root.toPath());}
    }
}

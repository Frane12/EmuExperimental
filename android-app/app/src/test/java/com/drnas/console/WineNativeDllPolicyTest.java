package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

public class WineNativeDllPolicyTest {
    private static Map<String,String> baseline() {
        Map<String,String> env=new HashMap<>();
        WineGuiSession.displayEnvironment(env);
        return env;
    }
    @Test public void normalX86DirectXKeepsDxvkEnabled() {
        Map<String,String> env=baseline();
        String original=env.get("WINEDLLOVERRIDES");
        WineNativeDllPolicy.configure(env,WineNativeDllPolicy.IMAGE_FILE_MACHINE_AMD64,
                false,true,s->{});
        assertEquals(original,env.get("WINEDLLOVERRIDES"));
        assertNull(env.get("DRNAS_NATIVE_DXGI_GUARD"));
    }
    @Test public void nativeArm64BlenderUsesBuiltInDxgiWithoutRemovingOtherOverrides() {
        for(int machine:new int[]{WineNativeDllPolicy.IMAGE_FILE_MACHINE_ARM64,
                WineNativeDllPolicy.IMAGE_FILE_MACHINE_ARM64EC}) {
            Map<String,String> env=baseline();
            WineNativeDllPolicy.configure(env,machine,false,true,s->{});
            String result=env.get("WINEDLLOVERRIDES");
            assertTrue(result.contains("dxgi=b"));
            assertTrue(result.contains("d3d9=b"));
            assertTrue(result.contains("d3d11=b"));
            assertTrue(result.contains("d3d10core=b"));
            assertTrue(result.contains("winex11.drv=b"));
            assertFalse(result.contains("dxgi=n,b"));
            assertEquals("1",env.get("DRNAS_NATIVE_DXGI_GUARD"));
        }
    }
    @Test public void nativeVulkanLinksUseBuiltinWithoutKnowingTargetMachine() {
        Map<String,String> env=baseline();
        WineNativeDllPolicy.configure(env,0,true,false,s->{});
        WineVulkanRuntime.configure(env,true);
        assertTrue(env.get("WINEDLLOVERRIDES").contains("dxgi=b"));
        assertTrue(env.get("WINEDLLOVERRIDES").contains("vulkan-1=b"));
    }
    @Test public void peMachineValidationRejectsMalformedAndDetectsArm64() throws Exception {
        java.nio.file.Path path=Files.createTempFile("drnas-pe-machine",".exe");
        try {
            byte[] pe=new byte[192];
            pe[0]='M';pe[1]='Z';pe[0x3c]=(byte)0x80;
            pe[0x80]='P';pe[0x81]='E';
            pe[0x84]=0x64;pe[0x85]=(byte)0xaa;
            Files.write(path,pe);
            assertEquals(WineNativeDllPolicy.IMAGE_FILE_MACHINE_ARM64,
                    WineNativeDllPolicy.guestMachine(path.toFile()));
            pe[0x81]='X';Files.write(path,pe);
            assertEquals(0,WineNativeDllPolicy.guestMachine(path.toFile()));
        } finally {Files.deleteIfExists(path);}
    }
}

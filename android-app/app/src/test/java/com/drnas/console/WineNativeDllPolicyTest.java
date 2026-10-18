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
    @Test public void dxgiInventoryIsReadOnlyAndReportsWrongMachine() throws Exception {
        java.nio.file.Path root=Files.createTempDirectory("drnas-dxgi-audit");
        try {
            java.nio.file.Path wine=Files.createDirectories(
                    root.resolve("wine/wine/aarch64-windows"));
            java.nio.file.Path prefix=Files.createDirectories(
                    root.resolve("prefix/drive_c/windows/system32"));
            byte[] pe=new byte[192];
            pe[0]='M'; pe[1]='Z'; pe[0x3c]=(byte)0x80;
            pe[0x80]='P'; pe[0x81]='E';
            pe[0x84]=0x64; pe[0x85]=(byte)0xaa; // ARM64 Wine builtin
            Files.write(wine.resolve("dxgi.dll"),pe);
            pe[0x84]=0x64; pe[0x85]=(byte)0x86; // AMD64 prefix DXVK
            Files.write(prefix.resolve("dxgi.dll"),pe);
            Map<String,String> env=baseline();
            WineVulkanRuntime.configure(env,true);
            String original=env.get("WINEDLLOVERRIDES");
            List<String> messages=new ArrayList<>();
            WineNativeDllPolicy.auditModules(root.resolve("wine").toFile(),
                    root.resolve("prefix").toFile(),null,
                    WineNativeDllPolicy.IMAGE_FILE_MACHINE_ARM64,env,messages::add);
            assertEquals(original,env.get("WINEDLLOVERRIDES"));
            assertTrue(messages.stream().anyMatch(s->s.contains("WINE:aarch64-windows/dxgi.dll") &&
                    s.contains("SAME_MACHINE")));
            assertTrue(messages.stream().anyMatch(s->s.contains("PREFIX:system32/dxgi.dll") &&
                    s.contains("DIFFERENT_MACHINE")));
        } finally {
            try(java.util.stream.Stream<java.nio.file.Path> children=Files.walk(root)) {
                children.sorted(java.util.Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}catch(Exception error){throw new RuntimeException(error);}
                });
            }
        }
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

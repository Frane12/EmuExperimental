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
    private static byte[] testPe(int machine) {
        byte[] pe=new byte[2048];
        pe[0]='M';pe[1]='Z';pe[0x3c]=(byte)0x80;
        pe[0x80]='P';pe[0x81]='E';
        pe[0x84]=(byte)(machine&255);pe[0x85]=(byte)((machine>>>8)&255);
        return pe;
    }
    private static void cleanTempTree(java.nio.file.Path root) throws Exception {
        try(java.util.stream.Stream<java.nio.file.Path> all=Files.walk(root)) {
            all.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
                try{Files.deleteIfExists(p);}catch(Exception e){throw new RuntimeException(e);}
            });
        }
    }
    @Test public void nativeArm64ReplacesOnlySystem32DxgiAndIsIdempotent() throws Exception {
        java.nio.file.Path root=Files.createTempDirectory("drnas-arm64-dxgi");
        try {
            java.nio.file.Path wine=Files.createDirectories(
                    root.resolve("wine/wine/aarch64-windows"));
            java.nio.file.Path system32=Files.createDirectories(
                    root.resolve("prefix/drive_c/windows/system32"));
            java.nio.file.Path syswow64=Files.createDirectories(
                    root.resolve("prefix/drive_c/windows/syswow64"));
            byte[] builtin=testPe(WineNativeDllPolicy.IMAGE_FILE_MACHINE_ARM64);
            byte[] dxvk=testPe(WineNativeDllPolicy.IMAGE_FILE_MACHINE_AMD64);
            byte[] wow=testPe(WineNativeDllPolicy.IMAGE_FILE_MACHINE_I386);
            Files.write(wine.resolve("dxgi.dll"),builtin);
            Files.write(system32.resolve("dxgi.dll"),dxvk);
            Files.write(syswow64.resolve("dxgi.dll"),wow);
            List<String> logs=new ArrayList<>();
            assertTrue(WineNativeDllPolicy.stageArm64Dxgi(root.resolve("wine").toFile(),
                    root.resolve("prefix").toFile(),
                    WineNativeDllPolicy.IMAGE_FILE_MACHINE_ARM64,logs::add));
            assertArrayEquals(builtin,Files.readAllBytes(system32.resolve("dxgi.dll")));
            assertArrayEquals(wow,Files.readAllBytes(syswow64.resolve("dxgi.dll")));
            assertTrue(WineNativeDllPolicy.stageArm64Dxgi(root.resolve("wine").toFile(),
                    root.resolve("prefix").toFile(),
                    WineNativeDllPolicy.IMAGE_FILE_MACHINE_ARM64,logs::add));
            assertTrue(logs.stream().anyMatch(s->s.contains("DRNAS_ARM64_DXGI_STAGE_OK")));
            assertTrue(logs.stream().anyMatch(s->s.contains("DRNAS_ARM64_DXGI_STAGE_VERIFIED")));
        }finally{cleanTempTree(root);}
    }
    @Test public void nonArm64GuestDoesNotAlterDxvkFile() throws Exception {
        java.nio.file.Path root=Files.createTempDirectory("drnas-dxgi-x64");
        try {
            java.nio.file.Path wine=Files.createDirectories(
                    root.resolve("wine/wine/aarch64-windows"));
            java.nio.file.Path folder=Files.createDirectories(
                    root.resolve("prefix/drive_c/windows/system32"));
            byte[] dxvk=testPe(WineNativeDllPolicy.IMAGE_FILE_MACHINE_AMD64);
            Files.write(wine.resolve("dxgi.dll"),
                    testPe(WineNativeDllPolicy.IMAGE_FILE_MACHINE_ARM64));
            Files.write(folder.resolve("dxgi.dll"),dxvk);
            assertFalse(WineNativeDllPolicy.stageArm64Dxgi(root.resolve("wine").toFile(),
                    root.resolve("prefix").toFile(),
                    WineNativeDllPolicy.IMAGE_FILE_MACHINE_AMD64,s->{}));
            assertArrayEquals(dxvk,Files.readAllBytes(folder.resolve("dxgi.dll")));
        }finally{cleanTempTree(root);}
    }
    @Test public void invalidWineArm64DllRefusesToOverwriteExistingDxvk() throws Exception {
        java.nio.file.Path root=Files.createTempDirectory("drnas-dxgi-invalid");
        try {
            java.nio.file.Path wine=Files.createDirectories(
                    root.resolve("wine/wine/aarch64-windows"));
            java.nio.file.Path folder=Files.createDirectories(
                    root.resolve("prefix/drive_c/windows/system32"));
            byte[] dxvk=testPe(WineNativeDllPolicy.IMAGE_FILE_MACHINE_AMD64);
            Files.write(wine.resolve("dxgi.dll"),testPe(WineNativeDllPolicy.IMAGE_FILE_MACHINE_I386));
            Files.write(folder.resolve("dxgi.dll"),dxvk);
            try {
                WineNativeDllPolicy.stageArm64Dxgi(root.resolve("wine").toFile(),
                        root.resolve("prefix").toFile(),
                        WineNativeDllPolicy.IMAGE_FILE_MACHINE_ARM64,s->{});
                fail("Must reject wrong Wine builtin architecture");
            } catch(java.io.IOException expected) {
                assertTrue(expected.getMessage().contains("BLOCKED"));
            }
            assertArrayEquals(dxvk,Files.readAllBytes(folder.resolve("dxgi.dll")));
        }finally{cleanTempTree(root);}
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
    @Test public void explorerLaunchedArm64BlenderGetsOnlyAppLocalDxgi() throws Exception {
        java.nio.file.Path root=Files.createTempDirectory("drnas-blender-arm64-sidecar");
        try {
            java.nio.file.Path wine=Files.createDirectories(
                    root.resolve("wine/wine/aarch64-windows"));
            java.nio.file.Path armBlender=Files.createDirectories(
                    root.resolve("prefix/drive_c/DRNAS/Apps/Blender-4.5.14"));
            java.nio.file.Path x64Blender=Files.createDirectories(
                    root.resolve("prefix/drive_c/DRNAS/Apps/Blender-x64"));
            java.nio.file.Path system32=Files.createDirectories(
                    root.resolve("prefix/drive_c/windows/system32"));
            byte[] wineArm64=testPe(WineNativeDllPolicy.IMAGE_FILE_MACHINE_ARM64);
            byte[] dxvkX64=testPe(WineNativeDllPolicy.IMAGE_FILE_MACHINE_AMD64);
            Files.write(wine.resolve("dxgi.dll"),wineArm64);
            Files.write(armBlender.resolve("blender.exe"),wineArm64);
            Files.write(x64Blender.resolve("blender.exe"),dxvkX64);
            Files.write(system32.resolve("dxgi.dll"),dxvkX64);
            List<String> log=new ArrayList<>();
            assertEquals(1,WineNativeDllPolicy.stageKnownArm64BlenderAppDxgi(
                    root.resolve("wine").toFile(),root.resolve("prefix").toFile(),log::add));
            assertArrayEquals(wineArm64,Files.readAllBytes(armBlender.resolve("dxgi.dll")));
            assertArrayEquals(dxvkX64,Files.readAllBytes(system32.resolve("dxgi.dll")));
            assertFalse(Files.exists(x64Blender.resolve("dxgi.dll")));
            assertEquals(0,WineNativeDllPolicy.stageKnownArm64BlenderAppDxgi(
                    root.resolve("wine").toFile(),root.resolve("prefix").toFile(),log::add));
            assertTrue(log.stream().anyMatch(s->s.contains("APP_LOCAL_ARM64_READY")));
        } finally {cleanTempTree(root);}
    }
    @Test public void explorerNativeStageNeverOverwritesExistingAppDll() throws Exception {
        java.nio.file.Path root=Files.createTempDirectory("drnas-blender-app-owned-dll");
        try {
            java.nio.file.Path wine=Files.createDirectories(
                    root.resolve("wine/wine/aarch64-windows"));
            java.nio.file.Path blender=Files.createDirectories(
                    root.resolve("prefix/drive_c/DRNAS/Apps/Blender-4.5.14"));
            byte[] arm=testPe(WineNativeDllPolicy.IMAGE_FILE_MACHINE_ARM64);
            byte[] existing=testPe(WineNativeDllPolicy.IMAGE_FILE_MACHINE_AMD64);
            Files.write(wine.resolve("dxgi.dll"),arm);
            Files.write(blender.resolve("blender.exe"),arm);
            Files.write(blender.resolve("dxgi.dll"),existing);
            assertEquals(0,WineNativeDllPolicy.stageKnownArm64BlenderAppDxgi(
                    root.resolve("wine").toFile(),root.resolve("prefix").toFile(),s->{}));
            assertArrayEquals(existing,Files.readAllBytes(blender.resolve("dxgi.dll")));
        } finally {cleanTempTree(root);}
    }
}

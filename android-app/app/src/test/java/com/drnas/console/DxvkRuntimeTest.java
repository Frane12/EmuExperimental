package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;

public final class DxvkRuntimeTest {
    private static byte[] pe(int machine) {
        byte[] data=new byte[256];data[0]='M';data[1]='Z';data[60]=64;
        data[64]='P';data[65]='E';data[68]=(byte)machine;data[69]=(byte)(machine>>8);
        return data;
    }
    @Test public void installsIntoActivePrefixAndReplacesBuiltinLinkWithoutModifyingItsTarget() throws Exception {
        Path root=Files.createTempDirectory("drnas-dxvk");
        try {
            File prepared=root.resolve("prepared").toFile(),active=root.resolve("active").toFile();
            for(String arch:new String[]{"system32","syswow64"}) {
                File folder=new File(prepared,".wine/drive_c/windows/"+arch);assertTrue(folder.mkdirs());
                for(String dll:new String[]{"d3d9.dll","dxgi.dll","d3d11.dll","d3d10core.dll","d3d10.dll","d3d10_1.dll"})
                    Files.write(new File(folder,dll).toPath(),pe(arch.equals("syswow64")?0x014c:0x8664));
            }
            Path target=active.toPath().resolve("drive_c/windows/system32/d3d9.dll");
            Files.createDirectories(target.getParent());
            Path builtin=root.resolve("wine-builtin.dll");Files.write(builtin,new byte[]{42});
            Files.createSymbolicLink(target,builtin);
            DxvkRuntime.install(prepared,active,s->{});
            assertFalse(Files.isSymbolicLink(target));
            assertArrayEquals(new byte[]{42},Files.readAllBytes(builtin));
            assertArrayEquals(pe(0x8664),Files.readAllBytes(target));
            assertArrayEquals(pe(0x014c),Files.readAllBytes(active.toPath().resolve("drive_c/windows/syswow64/d3d9.dll")));
            long time=Files.getLastModifiedTime(target).toMillis();
            DxvkRuntime.install(prepared,active,s->{});
            assertEquals(time,Files.getLastModifiedTime(target).toMillis());
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(root)){
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception ignored){}});
            }
        }
    }
    @Test public void rejectsWrongBitnessAndBrokenPeHeader() throws Exception {
        try{DxvkRuntime.validatePe(pe(0x8664),true);fail();}catch(IOException expected){}
        try{DxvkRuntime.validatePe(new byte[256],false);fail();}catch(IOException expected){}
        DxvkRuntime.validatePe(pe(0xa641),false);
        DxvkRuntime.validatePe(pe(0x014c),true);
    }
}

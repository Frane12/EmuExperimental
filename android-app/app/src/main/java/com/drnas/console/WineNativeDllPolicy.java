package com.drnas.console;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DRNAS DXVK's 32/64-bit x86 PE DLLs cannot be loaded by an ARM64 Windows
 * executable. Use Wine's architecture-matched built-in DirectX stubs for a
 * native ARM64/ARM64EC guest or when a user explicitly requests WineVulkan.
 * This does not alter installed DXVK DLLs and does not disable DirectX for
 * normal x86/x64 games launched in a separate Wine session.
 */
final class WineNativeDllPolicy {
    static final int IMAGE_FILE_MACHINE_ARM64=0xaa64;
    static final int IMAGE_FILE_MACHINE_ARM64EC=0xa641;
    static final int IMAGE_FILE_MACHINE_AMD64=0x8664;
    static final int IMAGE_FILE_MACHINE_I386=0x014c;

    /** Returns zero if no valid accessible PE header exists. No memory mapping. */
    static int guestMachine(File exe) throws IOException {
        if(exe==null || !exe.isFile() || exe.length()<128) return 0;
        try(RandomAccessFile in=new RandomAccessFile(exe,"r")) {
            if(in.readUnsignedByte()!=0x4d || in.readUnsignedByte()!=0x5a)return 0;
            in.seek(0x3c);
            long off=Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
            if(off<64 || off>exe.length()-6)return 0;
            in.seek(off);
            if(in.readUnsignedByte()!=0x50||in.readUnsignedByte()!=0x45 ||
                    in.readUnsignedByte()!=0||in.readUnsignedByte()!=0)return 0;
            int machine=in.readUnsignedByte()|(in.readUnsignedByte()<<8);
            return machine;
        }
    }
    static boolean isNativeArm64(int machine) {
        return machine==IMAGE_FILE_MACHINE_ARM64 ||
                machine==IMAGE_FILE_MACHINE_ARM64EC;
    }
    static boolean shouldUseWineBuiltinDxgi(int machine, boolean nativeVulkan) {
        return nativeVulkan || isNativeArm64(machine);
    }
    static void configure(Map<String,String> env,int machine,boolean nativeVulkan,
                          boolean hasWineBuiltIn,Consumer<String> log) {
        if(!shouldUseWineBuiltinDxgi(machine,nativeVulkan))return;
        String previous=env.getOrDefault("WINEDLLOVERRIDES","");
        // Replace the normal DXVK overrides instead of adding duplicates;
        // duplicate WINEDLLOVERRIDES entries can be parsed inconsistently.
        String updated=previous.replace("d3d9=n,b","d3d9=b")
                .replace("dxgi=n,b","dxgi=b")
                .replace("d3d11=n,b","d3d11=b")
                .replace("d3d10core=n,b","d3d10core=b");
        env.put("WINEDLLOVERRIDES",updated);
        env.put("DRNAS_NATIVE_DXGI_GUARD","1");
        log.accept("DRNAS_NATIVE_DXGI_GUARD: "+
                "guest PE machine=0x"+Integer.toHexString(machine)+
                ", nativeVulkan="+nativeVulkan+
                ", Wine builtin dxgi module="+(hasWineBuiltIn?"PRESENT":"UNCONFIRMED")+
                "; forcing dxgi/d3d9/d3d11/d3d10core=b to avoid " +
                "x86/x64 DXVK PE loader error c000007b in ARM64 guests. "+
                "No change to DXVK files or other application profiles.");
    }
    static boolean hasBuiltInDxgi(File wineLib) {
        for(String arch:new String[]{"aarch64-windows","arm64ec-windows"}) {
            File module=new File(wineLib,"wine/"+arch+"/dxgi.dll");
            if(module.isFile() && module.length()>512)return true;
        }
        return false;
    }
    private WineNativeDllPolicy(){}
}

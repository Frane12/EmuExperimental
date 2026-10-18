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
    /**
     * Read-only forensic inventory for an ARM64 program that still reports
     * c000007b after WINEDLLOVERRIDES=dxgi=b. Do not infer load order from
     * file presence: verbose Wine +module/+loaddll is needed to identify
     * which DLL Wine actually attempted to map.
     */
    static void auditModules(File wineLib,File prefix,File guestExe,
                             int guestMachine,Map<String,String> env,
                             Consumer<String> log) {
        if(!shouldUseWineBuiltinDxgi(guestMachine,
                "NATIVE_WINEVULKAN".equals(env.get("DRNAS_GRAPHICS_API"))))return;
        log.accept("DRNAS_PE_IMPORT_AUDIT BEGIN (read-only): guestMachine=0x"+
                Integer.toHexString(guestMachine)+
                "; WINEDLLOVERRIDES="+env.getOrDefault("WINEDLLOVERRIDES","(unset)")+
                "; WINEDLLPATH="+env.getOrDefault("WINEDLLPATH","(unset)"));
        for(String architecture:new String[]{"aarch64-windows","arm64ec-windows","x86_64-windows"}) {
            inspect("WINE:"+architecture+"/dxgi.dll",
                    new File(wineLib,"wine/"+architecture+"/dxgi.dll"),guestMachine,log);
        }
        for(String arch:new String[]{"system32","syswow64"}) {
            for(String dll:new String[]{"dxgi.dll","d3d11.dll"}) {
                inspect("PREFIX:"+arch+"/"+dll,
                        new File(prefix,"drive_c/windows/"+arch+"/"+dll),guestMachine,log);
            }
        }
        if(guestExe!=null && guestExe.isFile()) {
            inspect("EXE:"+guestExe.getName(),guestExe,guestMachine,log);
            // Windows' DLL search can discover an application-local dxgi.dll
            // before system32. Check only that known file, not the directory.
            inspect("EXE_DIR:dxgi.dll",new File(guestExe.getParentFile(),"dxgi.dll"),
                    guestMachine,log);
        }else log.accept("DRNAS_PE_IMPORT_AUDIT GUEST_EXE_UNRESOLVED (possible .lnk)");
        log.accept("DRNAS_PE_IMPORT_AUDIT END: PE machine is NOT proof of which DLL loaded.");
    }
    private static void inspect(String label,File binary,int guestMachine,
                                Consumer<String> log) {
        try {
            if(!binary.isFile()){
                log.accept("DRNAS_PE_IMPORT_AUDIT "+label+" ABSENT");
                return;
            }
            int machine=guestMachine(binary);
            String match=guestMachine==0?"GUEST_MACHINE_UNKNOWN":
                    machine==guestMachine?"SAME_MACHINE":"DIFFERENT_MACHINE";
            log.accept("DRNAS_PE_IMPORT_AUDIT "+label+" fileBytes="+binary.length()+
                    " PE_MACHINE=0x"+Integer.toHexString(machine)+" "+match);
        }catch(IOException|SecurityException error){
            log.accept("DRNAS_PE_IMPORT_AUDIT "+label+" READ_ERROR "+
                    error.getClass().getSimpleName());
        }
    }
    private WineNativeDllPolicy(){}
}

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
     * Per-session ARM64 DXGI recovery. The r19 loader trace proves that
     * dxgi=b by itself does not bypass an x86_64 dxgi.dll already residing in
     * prefix/system32: Wine sees machine 0x8664 and fails with c000007b.
     *
     * Stage only Wine's existing architecture-matched PE DXGI in the
     * application-owned active prefix, before any Wine process starts.
     * On the next session DxvkVariants.install() restores the pinned DXVK
     * x64 copy from the prepared prefix. Do not alter the Wine package,
     * user applications, syswow64 or the x86/x64 game installation.
     *
     * Never apply to unknown, x86 or x64 executable machines. .lnk launchers
     * with an unknown target must be resolved before using this recovery.
     */
    static boolean stageArm64Dxgi(File wineLib,File activePrefix,int guestMachine,
                                 Consumer<String> log) throws IOException {
        if(!isNativeArm64(guestMachine)) {
            log.accept("DRNAS_ARM64_DXGI_STAGE_SKIPPED: guest PE not confirmed ARM64/ARM64EC");
            return false;
        }
        File source=new File(wineLib,"wine/aarch64-windows/dxgi.dll").getCanonicalFile();
        File allowedRoot=wineLib.getCanonicalFile();
        if(!source.toPath().startsWith(allowedRoot.toPath()) ||
                !java.nio.file.Files.isRegularFile(source.toPath(),
                        java.nio.file.LinkOption.NOFOLLOW_LINKS) ||
                source.length()<512 || source.length()>16L*1024*1024 ||
                guestMachine(source)!=IMAGE_FILE_MACHINE_ARM64)
            throw new IOException("DRNAS_ARM64_DXGI_STAGE_BLOCKED: verified ARM64 Wine dxgi.dll unavailable");

        File prefix=activePrefix.getCanonicalFile();
        File folder=new File(prefix,"drive_c/windows/system32").getCanonicalFile();
        if(!folder.isDirectory() || !folder.toPath().startsWith(prefix.toPath()))
            throw new IOException("DRNAS_ARM64_DXGI_STAGE_BLOCKED: invalid prefix/system32");
        java.nio.file.Path target=new File(folder,"dxgi.dll").toPath();
        // DxvkVariants.install() must have staged the regular x64 DXVK first.
        // Refuse to overwrite an unknown third-party or unrecognized file.
        if(!java.nio.file.Files.isRegularFile(target,
                java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new IOException("DRNAS_ARM64_DXGI_STAGE_BLOCKED: baseline DXVK dxgi.dll missing");
        int currentMachine=guestMachine(target.toFile());
        if(currentMachine!=IMAGE_FILE_MACHINE_AMD64 &&
                currentMachine!=IMAGE_FILE_MACHINE_ARM64)
            throw new IOException("DRNAS_ARM64_DXGI_STAGE_BLOCKED: unexpected system32 dxgi PE 0x"+
                    Integer.toHexString(currentMachine));
        byte[] pe=java.nio.file.Files.readAllBytes(source.toPath());
        if(guestMachine(target.toFile())==IMAGE_FILE_MACHINE_ARM64 &&
                java.util.Arrays.equals(pe,java.nio.file.Files.readAllBytes(target))) {
            log.accept("DRNAS_ARM64_DXGI_STAGE_VERIFIED: Wine ARM64 dxgi already staged");
            return true;
        }
        java.nio.file.Path temp=java.nio.file.Files.createTempFile(folder.toPath(),
                ".drnas-arm64-dxgi-",".tmp");
        try {
            java.nio.file.Files.write(temp,pe);
            try {
                java.nio.file.Files.move(temp,target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch(java.nio.file.AtomicMoveNotSupportedException notAtomic) {
                java.nio.file.Files.move(temp,target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }finally{java.nio.file.Files.deleteIfExists(temp);}
        if(guestMachine(target.toFile())!=IMAGE_FILE_MACHINE_ARM64)
            throw new IOException("DRNAS_ARM64_DXGI_STAGE_BLOCKED: staged dxgi header mismatch");
        log.accept("DRNAS_ARM64_DXGI_STAGE_OK: system32/dxgi.dll now Wine ARM64 PE "+
                "(previous=0x"+Integer.toHexString(currentMachine)+
                ", bytes="+pe.length+"). DXVK x64 will be reinstated from pinned WCP "+
                "at next Wine session; syswow64 and app folders untouched. "+
                "Wine loader/Blender startup still require device test.");
        return true;
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

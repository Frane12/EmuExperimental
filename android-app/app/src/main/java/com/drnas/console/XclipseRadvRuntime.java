package com.drnas.console;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/**
 * Experimental RADV backend restricted to Exynos 2400 / Xclipse 940.
 * Uses the existing Wine Khronos -> X11 wrapper -> AdrenoTools loader.
 * Samsung's stock Vulkan remains the fallback if the bundle cannot be staged.
 * No driver is installed into Android system partitions.
 */
final class XclipseRadvRuntime {
    static final String ARCHIVE="xclipse/radv-xclipse-27.0.0-devel-0340c63.zip";
    static final String SHA256="0155e9114f8f8b975107c49495e4730161b45805d52fe955cd1e4aed888b4763";
    static final String DRIVER="vulkan.radeon.so";

    static boolean compatible(String model,String name,int api){
        if(api<34) return false;
        String m=model==null?"":model.toUpperCase(Locale.ROOT);
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        // Device model can be reported without a GPU name on Samsung firmware.
        return n.matches(".*\\bxclipse[ _-]*940\\b.*") ||
                (m.startsWith("SM-S731") || m.startsWith("SM-S921") ||
                 m.startsWith("SM-S926") || m.startsWith("SM-S928"));
    }
    static boolean matchesDevice(){
        return compatible(Build.MODEL,NativeHost.vulkanDeviceName(),Build.VERSION.SDK_INT);
    }
    private static String sha256(Path file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(file)){
            byte[] bytes=new byte[65536];int n;
            while((n=in.read(bytes))!=-1)if(n>0)digest.update(bytes,0,n);
        }
        StringBuilder out=new StringBuilder(64);
        for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private static boolean arm64Elf(Path file) throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)<4096)return false;
        try(InputStream in=Files.newInputStream(file)){
            byte[] b=new byte[20];
            if(in.read(b)!=20)return false;
            return (b[0]&255)==127 && b[1]=='E' && b[2]=='L' && b[3]=='F' &&
                    b[4]==2 && (b[18]&255)==183 && b[19]==0;
        }
    }
    private static File stage(Context ctx) throws Exception {
        Path base=new File(ctx.getFilesDir(),"radv-xclipse/0340c63").toPath();
        if(Files.isSymbolicLink(base))throw new IOException("Symlink in RADV destination");
        Files.createDirectories(base);
        Path target=base.resolve(DRIVER),marker=base.resolve(".sha256");
        if(Files.isSymbolicLink(target)||Files.isSymbolicLink(marker))
            throw new IOException("Unsafe RADV file");
        if(arm64Elf(target) && Files.isRegularFile(marker) &&
                SHA256.equals(new String(Files.readAllBytes(marker),StandardCharsets.US_ASCII)))
            return target.toFile();
        Path zip=Files.createTempFile(base,"radv-", ".zip");
        Path extracted=Files.createTempFile(base,".driver-", ".so");
        try{
            try(InputStream in=ctx.getAssets().open(ARCHIVE)){
                Files.copy(in,zip,StandardCopyOption.REPLACE_EXISTING);
            }
            if(!SHA256.equals(sha256(zip)))throw new IOException("RADV archive SHA-256 mismatch");
            boolean found=false;
            try(ZipInputStream entries=new ZipInputStream(Files.newInputStream(zip))){
                ZipEntry e;
                while((e=entries.getNextEntry())!=null){
                    if(e.isDirectory() || !DRIVER.equals(e.getName()))continue;
                    if(found)throw new IOException("Duplicate RADV driver in archive");
                    found=true;
                    try(OutputStream out=Files.newOutputStream(extracted,StandardOpenOption.TRUNCATE_EXISTING)){
                        long total=0;byte[] buf=new byte[65536];int n;
                        while((n=entries.read(buf))!=-1){
                            total+=n;
                            if(total>48L*1024*1024)throw new IOException("RADV driver too large");
                            out.write(buf,0,n);
                        }
                    }
                }
            }
            if(!found || !arm64Elf(extracted))throw new IOException("RADV ARM64 ELF missing or invalid");
            Files.move(extracted,target,StandardCopyOption.REPLACE_EXISTING);
            Files.write(marker,SHA256.getBytes(StandardCharsets.US_ASCII));
            return target.toFile();
        } finally {
            Files.deleteIfExists(zip);Files.deleteIfExists(extracted);
        }
    }
    static boolean select(Context ctx,Map<String,String> env,Consumer<String> log){
        if(!matchesDevice())return false;
        try{
            File driver=stage(ctx);
            env.put("DRNAS_XCLIPSE_LD_BASE",env.getOrDefault("LD_LIBRARY_PATH",""));
            env.put("ADRENOTOOLS_DRIVER_PATH",driver.getParent()+"/");
            env.put("ADRENOTOOLS_DRIVER_NAME",driver.getName());
            env.put("DRNAS_TURNIP_PREFERRED","1");
            env.put("DRNAS_XCLIPSE_RADV","EXPERIMENTAL_0340c63");
            env.remove("TU_AUTOTUNE_ALGO");
            env.put("LD_LIBRARY_PATH",driver.getParent()+":"+env.get("DRNAS_XCLIPSE_LD_BASE"));
            log.accept("DRNAS_XCLIPSE_RADV_STAGED v1.7.0 sha256="+SHA256+
                  " (driver selected for probe; GPU device/Present not verified)");
            return true;
        }catch(Exception ex){
            log.accept("DRNAS_XCLIPSE_RADV_FAILED: "+ex.getMessage()+
                    " ; falling back to firmware system Vulkan");
            return false;
        }
    }
    private XclipseRadvRuntime(){}
}

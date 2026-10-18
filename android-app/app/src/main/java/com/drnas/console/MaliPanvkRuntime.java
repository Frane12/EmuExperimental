package com.drnas.console;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Opt-in-to-hardware, APK-pinned PanVK Kbase experiment for Mali-G52 MC2.
 * Only the isolated Wine process is configured; no global Android GPU driver
 * is replaced. Do NOT advertise Vulkan success until the native probe succeeds.
 */
final class MaliPanvkRuntime {
    static final String ARCHIVE = "mali/mali-panvk-26.3.0-devel-r4.zip";
    static final String SHA256 = "1bf5fdbc6fa7134100f747692436eeee5e112e640a8b5064345facbfac918787";
    static final String DRIVER = "libvulkan_panfrost.so";

    static boolean isTargetGpu(String model, String vkName) {
        final String gpu=vkName == null ? "" : vkName.toLowerCase(Locale.ROOT);
        final String device=model == null ? "" : model.toUpperCase(Locale.ROOT);
        // Explicit device from the failure report even when stock Vulkan
        // enumeration returns no physical device.
        return gpu.contains("mali-g52") || device.equals("24040RN64Y");
    }

    static boolean matchesDevice() {
        return Build.VERSION.SDK_INT >= 31 &&
                isTargetGpu(Build.MODEL,NativeHost.vulkanDeviceName());
    }

    private static String sha(File source) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(source))) {
            byte[] buf=new byte[64*1024]; int n;
            while((n=in.read(buf))!=-1)if(n>0)digest.update(buf,0,n);
        }
        StringBuilder hex=new StringBuilder(64);
        for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }

    private static File install(Context ctx,Consumer<String> log) throws Exception {
        File root=new File(ctx.getFilesDir(),"mali-panvk/r4");
        File driver=new File(root,DRIVER);
        // Reverify the ELF every startup: files live in app-private storage.
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot create PanVK storage");
        File archive=new File(root,".panvk-package.zip");
        try(InputStream in=ctx.getAssets().open(ARCHIVE);
                OutputStream out=new BufferedOutputStream(new FileOutputStream(archive))) {
            byte[] buf=new byte[65536];int n;
            while((n=in.read(buf))!=-1)if(n>0)out.write(buf,0,n);
        }
        if(!SHA256.equals(sha(archive)))throw new IOException("PanVK r4 archive checksum mismatch");
        File tmp=new File(root,DRIVER+".partial");
        long expanded=0;
        boolean found=false;
        try(ZipInputStream zip=new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(entry.isDirectory())continue;
                String name=entry.getName();
                if(!name.equals(DRIVER))continue;
                if(found)throw new IOException("Duplicate PanVK driver entry");
                found=true;
                try(OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp))) {
                    byte[] buffer=new byte[65536];int n;
                    while((n=zip.read(buffer))!=-1) {
                        if(n==0)continue;
                        expanded+=n;
                        if(expanded>64L*1024*1024)throw new IOException("PanVK ELF exceeds size limit");
                        out.write(buffer,0,n);
                    }
                }
            }
        } finally {archive.delete();}
        if(!found||expanded<200000)throw new IOException("Pinned PanVK ELF missing");
        try(InputStream in=new FileInputStream(tmp)) {
            byte[] h=new byte[20];
            if(in.read(h)!=20||h[0]!=127||h[1]!='E'||h[2]!='L'||h[3]!='F'||
                    h[4]!=2||(h[18]&255)!=183||h[19]!=0)
                throw new IOException("PanVK package does not contain ARM64 ELF");
        }
        Files.move(tmp.toPath(),driver.toPath(),StandardCopyOption.REPLACE_EXISTING);
        log.accept("DRNAS_MALI_PANVK_STAGED: "+DRIVER+" "+expanded+
                " bytes; archive SHA256 verified; GPU execution not yet established");
        return driver;
    }

    static boolean select(Context ctx, Map<String,String> env, Consumer<String> log) {
        String deviceName=NativeHost.vulkanDeviceName();
        if(!matchesDevice()) {
            log.accept("DRNAS_MALI_PANVK_SKIP: model="+Build.MODEL+" gpu="+deviceName);
            return false;
        }
        try {
            File driver=install(ctx,log);
            // Keep the same X11 WSI wrapper used by DRNAS. Its backend driver
            // override is experimental on Mali and MUST be validated by the
            // vulkan_device_probe and by Wine/Blender's real frame.
            File parent=driver.getParentFile().getCanonicalFile();
            env.put("ADRENOTOOLS_DRIVER_PATH",parent.getAbsolutePath()+"/");
            env.put("ADRENOTOOLS_DRIVER_NAME",DRIVER);
            env.put("DRNAS_TURNIP_PREFERRED","1");
            env.put("DRNAS_MALI_PANVK_PREFERRED","1");
            env.put("PANVK_DEBUG","startup");
            env.remove("TU_AUTOTUNE_ALGO");
            env.put("LD_LIBRARY_PATH",parent.getAbsolutePath()+":"+
                    env.getOrDefault("LD_LIBRARY_PATH",""));
            log.accept("DRNAS_MALI_PANVK_BACKEND_REQUESTED: PanVK Kbase r4 for "+Build.MODEL+
                    " / "+deviceName+"; X11 wrapper retained; adapter and presentation NOT verified");
            return true;
        } catch(Exception e) {
            log.accept("DRNAS_MALI_PANVK_FAILED: "+e.getClass().getSimpleName()+
                    ": "+e.getMessage()+"; system Vulkan left unchanged");
            return false;
        }
    }
    private MaliPanvkRuntime(){}
}

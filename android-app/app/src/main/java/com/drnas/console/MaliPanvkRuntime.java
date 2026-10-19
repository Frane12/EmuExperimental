package com.drnas.console;

import android.content.Context;
import android.os.Build;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Hardware-gated PanVK candidates, isolated to the Wine child environment. */
final class MaliPanvkRuntime {
    static final String ARCHIVE="mali/"+MaliDriverCatalog.ENTRIES[0].archive;
    static final String SHA256=MaliDriverCatalog.ENTRIES[0].sha;
    static final String DRIVER="libvulkan_panfrost.so";
    private static final String BASE_LD="DRNAS_MALI_BASE_LD_LIBRARY_PATH";
    static boolean isTargetGpu(String model,String name) {
        return !MaliDriverCatalog.gpu(model,name).isEmpty();
    }
    static boolean matchesDevice() {
        return !MaliDriverCatalog.compatible(Build.MODEL,NativeHost.vulkanDeviceName(),
                Build.VERSION.SDK_INT,"").isEmpty();
    }
    private static boolean elf(File file) throws IOException {
        if(Files.isSymbolicLink(file.toPath())||!file.isFile()||file.length()<20)return false;
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))) {
            byte[] h=new byte[20];in.readFully(h);
            return h[0]==127&&h[1]=='E'&&h[2]=='L'&&h[3]=='F'&&h[4]==2&&
                    (h[18]&255)==183&&h[19]==0;
        }
    }
    private static String sha(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf=new byte[65536];int n;
            while((n=in.read(buf))!=-1)if(n>0)digest.update(buf,0,n);
        }
        StringBuilder result=new StringBuilder(64);
        for(byte b:digest.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));
        return result.toString();
    }
    private static File install(Context ctx,MaliDriverCatalog.Entry entry) throws Exception {
        Path base=new File(ctx.getFilesDir(),"mali-panvk").toPath();
        Path root=base.resolve(entry.id);
        if(Files.isSymbolicLink(base)||Files.isSymbolicLink(root))throw new IOException("Unsafe PanVK directory");
        Files.createDirectories(root);
        Path marker=root.resolve(".archive-sha256");
        if(Files.isSymbolicLink(marker))throw new IOException("Unsafe PanVK marker");
        String[] libraries=entry.g52Only?new String[]{DRIVER,"libc++_shared.so","libdrm.so"}:new String[]{DRIVER};
        boolean ready=Files.isRegularFile(marker)&&entry.sha.equals(new String(Files.readAllBytes(marker),StandardCharsets.US_ASCII));
        for(String name:libraries)ready&=elf(root.resolve(name).toFile());
        if(ready)return root.resolve(DRIVER).toFile();
        Path archive=Files.createTempFile(base,".panvk-",".zip");
        Path stage=Files.createTempDirectory(base,".panvk-extract-");
        Set<String> extracted=new HashSet<>();
        try {
            try(InputStream in=ctx.getAssets().open("mali/"+entry.archive)) {
                Files.copy(in,archive,StandardCopyOption.REPLACE_EXISTING);
            }
            if(!entry.sha.equals(sha(archive.toFile())))throw new IOException("PanVK checksum mismatch: "+entry.id);
            long total=0;
            try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(archive))) {
                ZipEntry item;
                while((item=zip.getNextEntry())!=null) {
                    String name=item.getName();
                    if(item.isDirectory()||!Arrays.asList(libraries).contains(name))continue;
                    if(!extracted.add(name))throw new IOException("Duplicate PanVK library");
                    long size=0,limit=name.equals(DRIVER)?160L*1024*1024:8L*1024*1024;
                    try(OutputStream out=Files.newOutputStream(stage.resolve(name),StandardOpenOption.CREATE_NEW)) {
                        byte[] buffer=new byte[65536];int n;
                        while((n=zip.read(buffer))!=-1) {
                            size+=n;total+=n;
                            if(size>limit||total>176L*1024*1024)throw new IOException("PanVK archive size limit");
                            out.write(buffer,0,n);
                        }
                    }
                    if(!elf(stage.resolve(name).toFile()))throw new IOException("PanVK library is not ARM64 ELF: "+name);
                }
            }
            for(String name:libraries) {
                if(!extracted.contains(name))throw new IOException("Missing PanVK dependency: "+name);
                if(Files.isSymbolicLink(root.resolve(name)))throw new IOException("Unsafe PanVK library");
            }
            for(String name:libraries)Files.move(stage.resolve(name),root.resolve(name),StandardCopyOption.REPLACE_EXISTING);
            Files.write(marker,entry.sha.getBytes(StandardCharsets.US_ASCII));
            return root.resolve(DRIVER).toFile();
        } finally {
            Files.deleteIfExists(archive);
            for(String name:libraries)Files.deleteIfExists(stage.resolve(name));
            Files.deleteIfExists(stage);
        }
    }
    static boolean select(Context ctx,Map<String,String> env,Consumer<String> log) {
        return selectNext(ctx,env,log,new HashSet<>());
    }
    static boolean selectNext(Context ctx,Map<String,String> env,Consumer<String> log,Set<String> attempted) {
        for(MaliDriverCatalog.Entry entry:MaliDriverCatalog.compatible(Build.MODEL,
                NativeHost.vulkanDeviceName(),Build.VERSION.SDK_INT,TurnipRuntime.rememberedDriver(ctx))) {
            if(!attempted.add(entry.id))continue;
            try {
                File driver=install(ctx,entry);
                env.putIfAbsent(BASE_LD,env.getOrDefault("LD_LIBRARY_PATH",""));
                env.put("LD_LIBRARY_PATH",driver.getParent()+":"+env.get(BASE_LD));
                env.put("ADRENOTOOLS_DRIVER_PATH",driver.getParent()+"/");
                env.put("ADRENOTOOLS_DRIVER_NAME",DRIVER);
                env.put("DRNAS_TURNIP_PREFERRED","1");
                env.put("DRNAS_MALI_PANVK_PREFERRED","1");
                env.put("DRNAS_MALI_PANVK_ID",entry.id);
                env.put("PANVK_DEBUG","startup");env.remove("TU_AUTOTUNE_ALGO");
                log.accept("DRNAS_DRIVER_AUTO: Mali candidate="+entry.id+" staged; native probe pending");
                return true;
            } catch(Exception error) {
                log.accept("DRNAS_DRIVER_AUTO: Mali candidate="+entry.id+" unavailable: "+error.getMessage());
            }
        }
        return false;
    }
    static void clear(Map<String,String> env) {
        String base=env.remove(BASE_LD);
        if(base!=null)env.put("LD_LIBRARY_PATH",base);
        for(String key:new String[]{"ADRENOTOOLS_DRIVER_PATH","ADRENOTOOLS_DRIVER_NAME",
                "DRNAS_TURNIP_PREFERRED","DRNAS_MALI_PANVK_PREFERRED","DRNAS_MALI_PANVK_ID","PANVK_DEBUG"})env.remove(key);
    }
    private MaliPanvkRuntime(){}
}

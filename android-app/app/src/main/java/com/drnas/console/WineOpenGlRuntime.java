package com.drnas.console;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import org.json.JSONObject;

/**
 * Opt-in EGL+Zink host libs for Wine 11 on Bionic.
 * This is a genuine Mesa bundle, not a GLES alias masquerading as libGL.
 * Nothing touches the Wine prefix or other applications' environment.
 */
final class WineOpenGlRuntime {
    static final String ASSET = "opengl-runtime-v1.zip";
    private static final String VERSION = "v1";

    static File install(Context context, Consumer<String> log) throws Exception {
        File root = new File(context.getFilesDir(), "opengl-runtime/" + VERSION);
        File lib = new File(root, "lib");
        if (!lib.isDirectory() && !lib.mkdirs())
            throw new IOException("Cannot create separate OpenGL runtime");
        Map<String,byte[]> entries = new LinkedHashMap<>();
        long unpacked = 0;
        try (ZipInputStream zip = new ZipInputStream(context.getAssets().open(ASSET))) {
            ZipEntry e;
            byte[] buf = new byte[64*1024];
            while ((e=zip.getNextEntry())!=null) {
                String name=e.getName();
                if(e.isDirectory() ||
                   (!"manifest.json".equals(name) &&
                   !name.matches("lib/lib[A-Za-z0-9_.+-]+\\.so(?:\\.[0-9]+)?")) ||
                   entries.size()>=12)
                    throw new IOException("Unsafe Mesa bundle entry: "+name);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int n;
                while ((n=zip.read(buf))!=-1) {
                    unpacked+=n;
                    if(unpacked>48L*1024*1024)throw new IOException("OpenGL bundle too large");
                    output.write(buf,0,n);
                }
                if(entries.put(name,output.toByteArray())!=null)
                    throw new IOException("Duplicate Mesa entry");
                zip.closeEntry();
            }
        }
        byte[] meta = entries.remove("manifest.json");
        if(meta==null)throw new IOException("Mesa manifest missing");
        JSONObject manifest=new JSONObject(new String(meta,StandardCharsets.UTF_8));
        JSONObject indexed=manifest.getJSONObject("files");
        if(indexed.length()!=entries.size() ||
           !entries.containsKey("lib/libEGL.so.1") ||
           !entries.containsKey("lib/libgallium-26.3.0-devel.so"))
            throw new IOException("Incomplete EGL/Zink bundle");
        int repaired=0;
        for(Map.Entry<String,byte[]> entry:entries.entrySet()) {
            String name=entry.getKey();
            byte[] data=entry.getValue();
            String soname=name.substring(4);
            if(!indexed.getJSONObject(soname).getString("sha256").equals(digest(data)))
                throw new IOException("Mesa checksum failed: "+soname);
            if(data.length<64 || data[0]!=0x7f || data[1]!='E' || data[2]!='L' ||
               data[3]!='F' || data[4]!=2 || (data[18]&0xff)!=183 ||
               (data[19]&0xff)!=0)
                throw new IOException("Mesa binary is not ELF64 AArch64: "+soname);
            File dest=new File(lib,soname);
            if(dest.isFile() && !Files.isSymbolicLink(dest.toPath()) &&
               dest.length()==data.length && digest(Files.readAllBytes(dest.toPath())).equals(digest(data)))
                continue;
            File tmp=new File(lib,soname+".tmp");
            Files.write(tmp.toPath(),data);
            Files.move(tmp.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING);
            repaired++;
        }
        for(String name:new String[]{"libEGL.so.1","libGL.so.1","libGLESv2.so.2"}) {
            File f=new File(lib,name);
            if(!f.isFile())throw new IOException("Missing actual desktop GL library: "+name);
            String alias=name.substring(0,name.indexOf(".so")+3);
            Path target=new File(lib,alias).toPath();
            Path relative=Paths.get(name);
            if(Files.isSymbolicLink(target) && Files.readSymbolicLink(target).equals(relative))
                continue;
            Files.deleteIfExists(target);Files.createSymbolicLink(target,relative);
        }
        log.accept("DRNAS_OPENGL_MESA_BUNDLE_VERIFIED version="+VERSION+
                " sources="+manifest.optString("commit","?")+
                " aarch64_libraries="+entries.size()+" repaired="+repaired+
                " (EGL context creation/PRESENT NOT yet verified)");
        return lib;
    }

    static void configure(File lib, Map<String,String> env, Consumer<String> log) throws IOException {
        if(!new File(lib,"libEGL.so.1").isFile() ||
           !new File(lib,"libgallium-26.3.0-devel.so").isFile())
            throw new IOException("Mesa EGL/Zink runtime incomplete");
        // Wine 11 ARM64 winex11 uses its EGL path instead of the unsupported
        // GLX path in our Java X server. Use the selected Turnip Vulkan ICD.
        env.put("LD_LIBRARY_PATH",lib.getAbsolutePath()+":"+
                env.getOrDefault("LD_LIBRARY_PATH",""));
        env.put("WINE_USE_EGL","1");
        env.put("EGL_PLATFORM","x11");
        env.put("MESA_LOADER_DRIVER_OVERRIDE","zink");
        env.put("GALLIUM_DRIVER","zink");
        env.put("LIBGL_KOPPER_DRI2","1");
        // Avoid Wine TLS assumptions in Mesa background context threads.
        env.put("GALLIUM_THREAD","0");
        log.accept("DRNAS_OPENGL_EGL_ROUTE_REQUESTED Wine11+Mesa Zink+selected native Vulkan ICD; "+
                "WINE_USE_EGL=1 EGL_PLATFORM=x11 GALLIUM_DRIVER=zink; "+
                "requires successful eglInitialize / GL>=4.3 / first Present. "+
                "No GLX or DRI3 server implementation is claimed.");
    }

    private static String digest(byte[] data) throws Exception {
        byte[] h=MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder text=new StringBuilder(64);
        for(byte v:h)text.append(String.format(Locale.ROOT,"%02x",v&255));
        return text.toString();
    }
    private WineOpenGlRuntime(){}
}

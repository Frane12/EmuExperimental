package com.drnas.console;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.*;
import org.json.JSONObject;

/** APK-owned Bionic X11/font clients; imported Proton and regular prefixes stay intact. */
final class WineGuiRuntime {
    static final String ASSET="gui-runtime-v1.zip";
    static synchronized File install(Context context,Consumer<String> log) throws Exception {
        File root=new File(context.getFilesDir(),"gui-runtime/v1");
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot create GUI runtime");
        Map<String,byte[]> entries=new LinkedHashMap<>();long total=0;
        try(ZipInputStream zip=new ZipInputStream(context.getAssets().open(ASSET))) {
            ZipEntry e;int count=0;byte[] buf=new byte[65536];
            while((e=zip.getNextEntry())!=null) {
                String name=e.getName();
                if(e.isDirectory()||(!name.equals("manifest.json")&&!name.matches("lib/lib[A-Za-z0-9_.+-]+")) || ++count>64)
                    throw new IOException("Invalid bundled GUI entry");
                ByteArrayOutputStream out=new ByteArrayOutputStream();int n;
                while((n=zip.read(buf))!=-1){total+=n;if(total>64L*1024*1024)throw new IOException("GUI bundle too large");out.write(buf,0,n);}
                if(entries.put(name,out.toByteArray())!=null)throw new IOException("Duplicate GUI entry");
            }
        }
        byte[] manifest=entries.get("manifest.json");if(manifest==null)throw new IOException("GUI manifest missing");
        JSONObject hashes=new JSONObject(new String(manifest,StandardCharsets.UTF_8)).getJSONObject("files");
        if(hashes.length()!=entries.size()-1)throw new IOException("GUI manifest mismatch");
        int repaired=0;
        for(String name:entries.keySet()) {
            byte[] data=entries.get(name);
            if(name.startsWith("lib/")&&!sha(data).equals(hashes.getString(name.substring(4))))
                throw new IOException("GUI checksum failed: "+name);
            File dest=new File(root,name);
            if(dest.isFile()&&sha(Files.readAllBytes(dest.toPath())).equals(sha(data)))continue;
            dest.getParentFile().mkdirs();File part=new File(dest.getParentFile(),dest.getName()+".tmp");
            Files.write(part.toPath(),data);
            Files.move(part.toPath(),dest.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            repaired++;
        }
        List<String> names=new ArrayList<>();
        for(Iterator<String> it=hashes.keys();it.hasNext();)names.add(it.next());
        int aliases=installAliases(new File(root,"lib"),names);
        log.accept("GUI runtime verified: "+hashes.length()+" Bionic libraries; installed/repaired="+repaired+"; linker aliases="+aliases);
        return root;
    }
    static String aliasFor(String name) throws IOException {
        if(!name.matches("lib[A-Za-z0-9_+-]+\\.so(?:\\.[A-Za-z0-9_.+-]+)?"))
            throw new IOException("Invalid GUI library name");
        // Keep Android's own unversioned Vulkan loader as the platform default.
        // Wine's explicitly versioned libvulkan.so.1 remains available.
        if(name.startsWith("libvulkan.so") || name.endsWith(".so"))return null;
        return name.substring(0,name.indexOf(".so")+3);
    }
    static int installAliases(File lib,Collection<String> names) throws IOException {
        Map<String,String> aliases=new LinkedHashMap<>();
        for(String name:names) {
            String alias=aliasFor(name);if(alias==null)continue;
            String previous=aliases.put(alias,name);
            if(previous!=null&&!previous.equals(name))throw new IOException("Ambiguous GUI alias: "+alias);
        }
        for(Map.Entry<String,String> entry:aliases.entrySet()) {
            File target=new File(lib,entry.getValue());
            if(!target.isFile() || Files.isSymbolicLink(target.toPath()))throw new IOException("GUI alias target missing: "+entry.getValue());
            java.nio.file.Path alias=new File(lib,entry.getKey()).toPath();
            java.nio.file.Path expected=java.nio.file.Paths.get(entry.getValue());
            if(Files.isSymbolicLink(alias)&&Files.readSymbolicLink(alias).equals(expected))continue;
            // Only APK-owned aliases in gui-runtime/v1/lib are changed.
            Files.deleteIfExists(alias);Files.createSymbolicLink(alias,expected);
        }
        return aliases.size();
    }
    private static String sha(byte[] data) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder s=new StringBuilder();
        for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    static void configure(File root,File prefix,Map<String,String> env) throws IOException {
        String prior=env.getOrDefault("LD_LIBRARY_PATH","");
        env.put("LD_LIBRARY_PATH",new File(root,"lib").getAbsolutePath()+":"+prior+":/system/lib64");
        File cache=new File(root,"font-cache");cache.mkdirs();
        File config=new File(root,"fonts.conf");
        String xml="<?xml version=\"1.0\"?><!DOCTYPE fontconfig SYSTEM \"urn:fontconfig:fonts.dtd\"><fontconfig>"+
                "<dir>/system/fonts</dir><dir>"+escape(new File(prefix,"drive_c/windows/Fonts").getAbsolutePath())+"</dir>"+
                "<cachedir>"+escape(cache.getAbsolutePath())+"</cachedir>"+
                "<alias><family>sans-serif</family><prefer><family>Roboto</family><family>Noto Sans</family></prefer></alias>"+
                "<alias><family>monospace</family><prefer><family>Droid Sans Mono</family></prefer></alias></fontconfig>";
        Files.write(config.toPath(),xml.getBytes(StandardCharsets.UTF_8));
        env.put("FONTCONFIG_FILE",config.getAbsolutePath());env.put("FONTCONFIG_PATH",root.getAbsolutePath());
        env.put("XDG_CACHE_HOME",cache.getAbsolutePath());
        // The Canvas X server does not advertise MIT-SHM. Supply a non-null
        // private path so the upstream SysV client fails cleanly if used.
        env.put("ANDROID_SYSVSHM_SERVER",new File(root,"sysvshm-unavailable.sock").getAbsolutePath());
    }
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;");}
    static void probe(File nativeDir,File winex11,Map<String,String> env,Consumer<String> log,RawWineTrace raw) throws Exception {
        File helper=new File(nativeDir,"libdrnas_gui_probe.so");
        if(!helper.isFile())throw new IOException("GUI dependency probe absent from APK");
        ProcessBuilder pb=new ProcessBuilder("/system/bin/linker64",helper.getAbsolutePath(),winex11.getAbsolutePath()).redirectErrorStream(true);
        pb.environment().clear();pb.environment().putAll(env);pb.environment().remove("LD_PRELOAD");
        Process p=pb.start();Thread output=WineServerDiagnostics.pipe(p,"[GUI deps] ",log,40,raw);
        try {
            if(!p.waitFor(8,TimeUnit.SECONDS)){p.destroyForcibly();log.accept("GUI dependency probe timeout");}
            else log.accept("GUI dependency probe exit="+p.exitValue());
            output.join(1000);
        } finally {if(p.isAlive())p.destroyForcibly();p.getInputStream().close();}
    }
    private WineGuiRuntime(){}
}

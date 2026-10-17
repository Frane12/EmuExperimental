package com.drnas.console;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Explicit DXVK 1.10.3 / 1.10.3-async choice made BEFORE Wine starts.
 * Uses SHA256-pinned, verified WCPs already embedded in the APK. No network
 * access at run time. Never changes any DLL while a Wine process is running.
 *
 * DXVK_ASYNC=1 is meaningful only together with the async-patched DLLs.
 * A failed async installation must restore the default DXVK DLLs.
 */
final class DxvkVariants {
    static final String STANDARD="standard";
    static final String ASYNC="async";
    static final String ASYNC_NAME="Dxvk-1.10.3-async.wcp";
    static final String ASYNC_SHA256="19406702e641950d05b154c07b4dcead62aee94cc008bc8ed425cb72a2874914";
    private static final String PREFS="emudrnas.dxvk";
    private static final String KEY="variant";
    private static final String[] LIBRARIES={"d3d9.dll","dxgi.dll","d3d11.dll","d3d10core.dll"};
    static String selected(Context c) {
        return ASYNC.equals(c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getString(KEY,STANDARD))?ASYNC:STANDARD;
    }
    static void select(Context c,String mode){
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit().putString(KEY,ASYNC.equals(mode)?ASYNC:STANDARD).apply();
    }
    static String toggle(Context c) {
        String value=ASYNC.equals(selected(c))?STANDARD:ASYNC;
        select(c,value);
        return value;
    }

    private static JSONObject item(Context context) throws Exception {
        byte[] bytes;
        try(InputStream in=context.getAssets().open("runtime-components-v1.json");
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];
            for(int n;(n=in.read(buffer))!=-1;){
                if(out.size()+n>16384)throw new IOException("Manifest too large");
                out.write(buffer,0,n);
            }
            bytes=out.toByteArray();
        }
        JSONArray entries=new JSONObject(new String(bytes,"UTF-8")).getJSONArray("items");
        for(int i=0;i<entries.length();i++){
            JSONObject r=entries.getJSONObject(i);
            if("DXVK".equals(r.optString("kind")) &&
                    ASYNC.equals(r.optString("variant")) &&
                    ASYNC_NAME.equals(r.optString("name")) &&
                    ASYNC_SHA256.equals(r.optString("sha256")) &&
                    r.optString("source").endsWith("/"+ASYNC_NAME))return r;
        }
        throw new IOException("Pinned DXVK 1.10.3-async is absent from APK manifest");
    }
    private static final class Mapping {
        final Path from,to;
        final boolean x86;
        Mapping(Path from,Path to,boolean x86){this.from=from;this.to=to;this.x86=x86;}
    }
    static boolean install(Context context,File preparedPrefix,File activePrefix,
                           Consumer<String> log) throws Exception {
        return install(context,preparedPrefix,activePrefix,log,selected(context));
    }
    static boolean install(Context context,File preparedPrefix,File activePrefix,
                           Consumer<String> log,String variant) throws Exception {
        // Always restore known-good standard DLLs first. This also handles
        // changing from async back to standard after Reset Wine.
        DxvkRuntime.install(preparedPrefix,activePrefix,log);
        if(!ASYNC.equals(variant)){
            log.accept("DRNAS_DXVK_VARIANT=STANDARD 1.10.3; async OFF");
            return false;
        }
        try{
            BundledComponents.install(context,log); // no-op if cached
            JSONObject manifest=item(context);
            File vault=new File(context.getFilesDir(),"wcp-vault").getCanonicalFile();
            String sha=manifest.getString("sha256");
            File archive=new File(vault,sha+".wcp").getCanonicalFile();
            if(!archive.toPath().startsWith(vault.toPath()) ||
                    !Files.isRegularFile(archive.toPath(),LinkOption.NOFOLLOW_LINKS) ||
                    !ASYNC_SHA256.equals(TurnipRuntime.sha256(archive)))
                throw new IOException("Pinned async WCP SHA-256 mismatch");
            JSONObject metadata=WcpRuntimeStager.readJson(new File(vault,sha+".json"));
            if(!sha.equals(metadata.optString("sha256")) ||
                    !"DXVK".equals(metadata.optString("componentType")) ||
                    !ASYNC.equals(metadata.optString("variant")) ||
                    !metadata.optBoolean("bundledFromApk"))
                throw new IOException("DXVK async bundle metadata is invalid");
            WcpRuntimeStager.PackageInfo pkg=WcpRuntimeStager.stageWcp(
                    archive,vault,sha,log);
            if(!"DXVK".equals(pkg.kind))throw new IOException("Not a DXVK WCP");
            Path staged=pkg.folder.getCanonicalFile().toPath();
            Path windows=new File(activePrefix,"drive_c/windows").getCanonicalFile().toPath();
            if(!windows.startsWith(activePrefix.getCanonicalFile().toPath()))
                throw new IOException("Invalid Wine prefix destination");
            JSONArray files=pkg.profile.getJSONArray("files");
            Map<String,Mapping> found=new LinkedHashMap<>();
            for(int i=0;i<files.length();i++){
                JSONObject entry=files.getJSONObject(i);
                String name=entry.getString("target");
                String arch;
                if(name.startsWith("${system32}/"))arch="system32";
                else if(name.startsWith("${syswow64}/"))arch="syswow64";
                else continue;
                String dll=name.substring(name.lastIndexOf('/')+1);
                if(!Arrays.asList(LIBRARIES).contains(dll))continue;
                String source=WcpRuntimeStager.clean(entry.getString("source"),false);
                Path from=new File(pkg.folder,source).getCanonicalFile().toPath();
                Path to=windows.resolve(arch).resolve(dll);
                if(!from.startsWith(staged) || !Files.isRegularFile(from,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("DXVK WCP source escaped staging");
                String key=arch+"/"+dll;
                if(found.put(key,new Mapping(from,to,"syswow64".equals(arch)))!=null)
                    throw new IOException("Duplicate async DLL: "+key);
            }
            if(found.size()!=8)throw new IOException("Incomplete async DLL set: "+found.keySet());
            // Validate all PE machines and payloads before modifying ANY DLL.
            for(Mapping m:found.values())
                DxvkRuntime.validatePe(Files.readAllBytes(m.from),m.x86);
            for(Mapping m:found.values()){
                Files.createDirectories(m.to.getParent());
                Path tmp=Files.createTempFile(m.to.getParent(),".drnas-async-",".tmp");
                try{
                    Files.copy(m.from,tmp,StandardCopyOption.REPLACE_EXISTING);
                    Files.move(tmp,m.to,StandardCopyOption.REPLACE_EXISTING);
                }finally{Files.deleteIfExists(tmp);}
            }
            log.accept("DRNAS_DXVK_VARIANT=ASYNC 1.10.3; 8 DLLs installed before Wine start, SHA256 verified");
            return true;
        } catch(Exception error){
            log.accept("DRNAS_DXVK_ASYNC_FAILED: "+error.getMessage()+"; attempting standard fallback");
            DxvkRuntime.install(preparedPrefix,activePrefix,log);
            log.accept("DRNAS_DXVK_VARIANT=STANDARD (async setup failed)");
            return false;
        }
    }

    private DxvkVariants(){}
}

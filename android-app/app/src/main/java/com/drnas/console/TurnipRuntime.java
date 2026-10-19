package com.drnas.console;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Per-chip Drnas Turnip driver preference for Bionic Vulkan loaders.
 * Loading is NOT established by merely copying an AdrenoTools ZIP.
 * Actual activation requires a Vulkan loader that honors an ICD override or
 * a working AdrenoTools injection path. We log this distinction explicitly.
 */
final class TurnipRuntime {
    private static String automaticDeviceKey(Context context) {
        long version=0;
        try {version=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode;}
        catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        return android.os.Build.FINGERPRINT+":"+gpuTarget(NativeHost.vulkanDeviceName())+":"+version;
    }
    private static String rememberedDriver(Context context) {
        android.content.SharedPreferences prefs=context.getSharedPreferences("emudrnas-graphics",Context.MODE_PRIVATE);
        return automaticDeviceKey(context).equals(prefs.getString("automatic-driver-device",""))?
                prefs.getString("automatic-driver-id",""):"";
    }
    private static void rememberDriver(Context context,String id) {
        context.getSharedPreferences("emudrnas-graphics",Context.MODE_PRIVATE).edit()
                .putString("automatic-driver-device",automaticDeviceKey(context))
                .putString("automatic-driver-id",id).apply();
    }
    static boolean automaticSystemBackend(Context context) {
        return "system".equals(rememberedDriver(context));
    }
    private static void clearSelection(JSONObject plan) {
        for(String key:new String[]{"turnipIcd","turnipAutoSelected","turnipArchive","turnipPreferenceRequested"})
            plan.remove(key);
    }
    /** Exact published A840 NeuroFlow H2.9 archive already embedded in APK. */
    static final String A840_H29_SHA256=
            "6b67ba723b2a4881214591055c637e2e513363c44874559d16e2a13b1019bb3d";
    static final String A840_H29_ARCHIVE="Drnas-Turnip-A840.zip";
    static final String A840_H29_ORIGINAL="Drnas-Turnip-NeuroFlow-A840-H2.9-TESTING-ONLY.zip";

    static boolean autoH29Enabled(Context ctx) {
        return ctx.getSharedPreferences("emudrnas-graphics",Context.MODE_PRIVATE)
                .getBoolean("auto-a840-h29",true);
    }

    static void setAutoH29Enabled(Context ctx,boolean enabled) {
        ctx.getSharedPreferences("emudrnas-graphics",Context.MODE_PRIVATE)
                .edit().putBoolean("auto-a840-h29",enabled).apply();
    }

    static String sha256(File file) throws Exception {
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        try(InputStream in=new java.io.BufferedInputStream(new FileInputStream(file))) {
            byte[] buf=new byte[65536];
            for(int n;(n=in.read(buf))!=-1;)if(n>0)digest.update(buf,0,n);
        }
        StringBuilder hex=new StringBuilder(64);
        for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }

    /** Hardware detection and pinned candidates only; no end-user driver choices. */
    static boolean selectBundledA840H29(Context context,JSONObject plan,Consumer<String> log) {
        clearSelection(plan);
        if("system".equals(rememberedDriver(context))) {
            log.accept("DRNAS_DRIVER_AUTO: using last verified system backend for this device/build");
            return false;
        }
        return selectAutomatic(context,plan,log,new java.util.HashSet<>());
    }
    private static boolean selectAutomatic(Context context,JSONObject plan,Consumer<String> log,
                                            java.util.Set<String> attempted) {
        String target=gpuTarget(NativeHost.vulkanDeviceName());
        for(GraphicsDriverCatalog.Entry entry:GraphicsDriverCatalog.ordered(target,
                android.os.Build.VERSION.SDK_INT,rememberedDriver(context))) {
            if(!attempted.add(entry.id))continue;
            if("A840_H2.9".equals(entry.id)&&!autoH29Enabled(context))continue;
            if(selectPinned(context,plan,log,entry.target,entry.asset,entry.source,entry.sha,entry.id))return true;
        }
        clearSelection(plan);
        return false;
    }

    /** Native enumeration failure retries a compatible driver in a NEW child
     * before Blender starts. This does not claim to detect later render crashes. */
    static void verifyAutomaticBackend(Context context,File gui,JSONObject plan,Map<String,String> env,
                                        Consumer<String> log,RawWineTrace raw) throws Exception {
        java.util.Set<String> attempted=new java.util.HashSet<>();
        String current=plan.optString("turnipAutoSelected","");
        if(!current.isEmpty())attempted.add(current);
        boolean panvk="1".equals(env.get("DRNAS_MALI_PANVK_PREFERRED"));
        boolean custom=env.containsKey("ADRENOTOOLS_DRIVER_NAME");
        if(probeWine(context,gui,env,log,raw,false,"fifo")) {
            rememberDriver(context,current.isEmpty()?(panvk?"panvk":"system"):current);
            log.accept("DRNAS_DRIVER_AUTO_VERIFIED backend="+(current.isEmpty()?(panvk?"PanVK":"system"):current));
            return;
        }
        if(!panvk)while(selectAutomatic(context,plan,log,attempted)) {
            configureWine(context,plan,env,log);
            current=plan.optString("turnipAutoSelected","");
            log.accept("DRNAS_DRIVER_AUTO_RETRY backend="+current);
            if(probeWine(context,gui,env,log,raw,false,"fifo")) {
                rememberDriver(context,current);return;
            }
        }
        if(custom || !attempted.isEmpty()) {
            clearSelection(plan);configureWine(context,plan,env,log);
            log.accept("DRNAS_DRIVER_AUTO_RETRY backend=system");
            if(probeWine(context,gui,env,log,raw,false,"fifo")) {
                rememberDriver(context,"system");return;
            }
        }
        throw new IOException("No compatible Vulkan backend passed the automatic startup check. Please share the log for this device.");
    }

    private static boolean selectPinned(Context context,JSONObject plan,Consumer<String> log,
            String target,String assetName,String sourceName,String sha,String selection){
        try {
            // First-run and upgrade path; cached archives are reused. This does
            // not install any global Android driver or touch other packages.
            BundledComponents.install(context,log);
            String manifestJson;
            try(InputStream in=context.getAssets().open("runtime-components-v1.json")) {
                java.io.ByteArrayOutputStream tmp=new java.io.ByteArrayOutputStream();
                byte[] buf=new byte[4096];
                for(int n;(n=in.read(buf))!=-1;)tmp.write(buf,0,n);
                manifestJson=tmp.toString("UTF-8");
            }
            org.json.JSONArray assets=new JSONObject(manifestJson).getJSONArray("items");
            boolean present=false;
            for(int i=0;i<assets.length();i++) {
                JSONObject item=assets.getJSONObject(i);
                if("Turnip".equals(item.optString("kind")) &&
                        target.equals(item.optString("gpuTarget")) &&
                        assetName.equals(item.optString("name")) &&
                        sha.equals(item.optString("sha256")) &&
                        item.optString("source").endsWith("/"+sourceName)) {
                    present=true;break;
                }
            }
            if(!present)throw new IOException("APK manifest lacks pinned Turnip");
            File vault=new File(context.getFilesDir(),"wcp-vault").getCanonicalFile();
            File archive=new File(vault,sha+".wcp").getCanonicalFile();
            if(!archive.toPath().startsWith(vault.toPath()) ||
                    !archive.isFile() || java.nio.file.Files.isSymbolicLink(archive.toPath()))
                throw new IOException("pinned Turnip archive not staged");
            JSONObject meta=WcpRuntimeStager.readJson(new File(vault,sha+".json"));
            if(!sha.equals(meta.optString("sha256")) ||
                    !target.equals(meta.optString("gpuTarget")) ||
                    !"Turnip".equals(meta.optString("componentType")) ||
                    !meta.optBoolean("bundledFromApk"))
                throw new IOException("pinned Turnip metadata mismatch");
            if(!sha.equals(sha256(archive)))
                throw new IOException("pinned Turnip ZIP SHA-256 mismatch");
            WcpRuntimeStager.PackageInfo staged=WcpRuntimeStager.stageTurnipZip(
                    archive,vault,sha,log);
            File icd=writeIcd(staged,vault,log);
            plan.put("turnipIcd",icd.getCanonicalPath());
            plan.put("turnipArchive",sourceName);
            plan.put("turnipPreferenceRequested",true);
            plan.put("turnipAutoSelected",selection);
            log.accept("DRNAS_TURNIP_AUTOPICK_SUCCESS target="+target+": pinned ZIP "+sha+
                    " staged for this Explorer session; Vulkan load remains unverified");
            return true;
        } catch(Exception ex) {
            log.accept("DRNAS_TURNIP_AUTOPICK_FAILED target="+target+": "+ex.getMessage()+
                    "; preserving previous driver selection");
            return false;
        }
    }

    static String gpuTarget(String gpuName) {
        return TurnipDevicePolicy.target(gpuName);
    }

    static File writeIcd(WcpRuntimeStager.PackageInfo info,File vault,Consumer<String> log)
            throws Exception {
        File base=info.folder.getCanonicalFile();
        File stagedRoot=new File(vault,"runtime-packages").getCanonicalFile();
        if(!base.toPath().startsWith(stagedRoot.toPath()))
            throw new IOException("Turnip path escaped vault");
        File driver=findDriver(base);
        if(driver==null)throw new IOException("Turnip ZIP contains no freedreno ELF");
        if(Files.isSymbolicLink(driver.toPath()) || driver.length()<200000 ||
                driver.length()>128L*1024*1024)
            throw new IOException("Invalid staged Turnip driver library");
        try(InputStream in=new FileInputStream(driver)) {
            byte[] h=new byte[20];
            if(in.read(h)!=20 || h[0]!=127 || h[1]!='E' || h[2]!='L' ||
                    h[3]!='F' || h[4]!=2 || (h[18]&255)!=183 || h[19]!=0)
                throw new IOException("Turnip driver is not ARM64 ELF");
        }
        JSONObject inner=new JSONObject();
        inner.put("library_path",driver.getAbsolutePath());
        inner.put("api_version","1.3.0");
        JSONObject root=new JSONObject();
        root.put("file_format_version","1.0.0");
        root.put("ICD",inner);
        File icd=new File(base,"drnas-turnip-icd.json");
        File temp=new File(base,".drnas-turnip-icd.partial");
        try(FileOutputStream out=new FileOutputStream(temp)) {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if(!temp.renameTo(icd))throw new IOException("Cannot write Turnip ICD descriptor");
        log.accept("Drnas Turnip driver selected: "+driver.getName()+
                " | default ICD preference prepared (GPU activation not yet verified)");
        return icd;
    }

    private static File findDriver(File root) throws IOException {
        try(java.util.stream.Stream<Path> walk=Files.walk(root.toPath(),5)) {
            return walk.filter(p->p.getFileName().toString().equals("libvulkan_freedreno.so")||
                            p.getFileName().toString().equals("vulkan.ad07xx.so"))
                    .filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))
                    .map(Path::toFile).findFirst().orElse(null);
        }
    }

    static void configureWine(Context ctx,JSONObject plan,Map<String,String> env,
                              Consumer<String> log) throws Exception {
        File gui=new File(ctx.getFilesDir(),"gui-runtime/v1/lib");
        configureWrapper(gui,env);
        log.accept("Wine Vulkan bridge: Khronos loader -> Mesa X11 wrapper -> AdrenoTools/driver. X11 presentation uses GPU readback + PutImage while DRI3 is unavailable.");
        if(!plan.has("turnipIcd")||plan.isNull("turnipIcd")) {
            log.accept("No matching bundled Drnas Turnip; Wine uses system Vulkan.");
            return;
        }
        File icd=new File(plan.getString("turnipIcd")).getCanonicalFile();
        File vault=new File(ctx.getFilesDir(),"wcp-vault/runtime-packages").getCanonicalFile();
        if(!icd.toPath().startsWith(vault.toPath()) || !icd.isFile() ||
                Files.isSymbolicLink(icd.toPath()))
            throw new IOException("Untrusted Turnip ICD path");
        JSONObject description=WcpRuntimeStager.readJson(icd);
        File driver=new File(description.getJSONObject("ICD").getString("library_path"))
                .getCanonicalFile();
        if(!driver.toPath().startsWith(vault.toPath()) || !driver.isFile() ||
                Files.isSymbolicLink(driver.toPath()))
            throw new IOException("Turnip ICD references unavailable library");
        // The desktop ICD must remain the X11 wrapper. The Android-only
        // Turnip ICD is the backend selected through AdrenoTools instead.
        env.put("ADRENOTOOLS_DRIVER_PATH",driver.getParentFile().getAbsolutePath()+"/");
        env.put("ADRENOTOOLS_DRIVER_NAME",driver.getName());
        env.put("DRNAS_TURNIP_PREFERRED","1");
        env.put("DRNAS_TURNIP_EXPECTED_SO",driver.getAbsolutePath());
        env.put("DRNAS_TURNIP_BUILD_ID",plan.optString("turnipAutoSelected","custom"));
        if("A840_H2.9".equals(plan.optString("turnipAutoSelected"))) {
            env.put("DRNAS_TURNIP_EXPECTED_SO",driver.getAbsolutePath());
            env.put("DRNAS_TURNIP_BUILD_ID","A840-H2.9");
            env.put("TU_FRANE_A840_AGGRESSIVE","1");
            log.accept("DRNAS_A840_H29_WRAPPER_CONFIGURED: hooks="+
                    env.getOrDefault("ADRENOTOOLS_HOOKS_PATH","")+
                    " driver="+driver.getAbsolutePath()+
                    " (not evidence of actual Vulkan device creation)");
        }
        // Passed to the initial native Vulkan probe AND inherited by Wine/DXVK.
        // This requests profiling-based Mesa/Turnip autotuning when supported by
        // the installed driver. It never forces a GMEM/SYSMEM render path.
        enableProfiledAutotuner(env,log);
        env.put("LD_LIBRARY_PATH",driver.getParentFile().getAbsolutePath()+":"+
                env.getOrDefault("LD_LIBRARY_PATH",""));
        log.accept("Drnas Turnip backend requested through AdrenoTools: "+driver.getName()+"; native device/swapchain probe follows.");
    }

    static void enableProfiledAutotuner(Map<String,String> env,Consumer<String> log) {
        env.put("TU_AUTOTUNE_ALGO","profiled");
        log.accept("Turnip autotuner startup: TU_AUTOTUNE_ALGO=profiled requested "
                +"for native Vulkan probe and Wine/DXVK; actual support depends on loaded driver.");
    }

    static void configureWrapper(File lib,Map<String,String> env) throws Exception {
        File wrapper=new File(lib,"libvulkan_wrapper.so");
        if(!wrapper.isFile() || !new File(lib,"libmain_hook.so").isFile())
            throw new IOException("Bundled Vulkan wrapper/hooks missing");
        JSONObject icd=new JSONObject().put("file_format_version","1.0.0")
                .put("ICD",new JSONObject().put("library_path",wrapper.getAbsolutePath()).put("api_version","1.3.0"));
        File descriptor=new File(lib.getParentFile(),"drnas-wrapper-icd.json");
        byte[] bytes=icd.toString(2).getBytes(StandardCharsets.UTF_8);
        if(!descriptor.isFile() || !java.util.Arrays.equals(Files.readAllBytes(descriptor.toPath()),bytes)) {
            Path tmp=Files.createTempFile(descriptor.getParentFile().toPath(),".wrapper-icd-",".tmp");
            try {
                Files.write(tmp,bytes);
                try {Files.move(tmp,descriptor.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,descriptor.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            } finally {Files.deleteIfExists(tmp);}
        }
        env.put("VK_DRIVER_FILES",descriptor.getAbsolutePath());
        env.put("VK_ICD_FILENAMES",descriptor.getAbsolutePath());
        env.put("ADRENOTOOLS_HOOKS_PATH",lib.getAbsolutePath()+"/");
        env.remove("ADRENOTOOLS_DRIVER_PATH");env.remove("ADRENOTOOLS_DRIVER_NAME");
        env.remove("DRNAS_TURNIP_EXPECTED_SO");env.remove("DRNAS_TURNIP_BUILD_ID");
        env.remove("TU_FRANE_A840_AGGRESSIVE");
        env.remove("DRNAS_MALI_PANVK_PREFERRED");env.remove("PANVK_DEBUG");
        env.put("DRNAS_TURNIP_PREFERRED","0");
        // sw selects CPU *presentation*, not software shader execution. The
        // real driver still renders; Mesa copies its image to X11 PutImage.
        // Our TCP X server has neither fd passing/DRI3 nor MIT-SHM.
        env.put("MESA_VK_WSI_DEBUG","sw,noshm");
        env.put("WRAPPER_EMULATE_BCN","0");
        env.put("WRAPPER_LOG_LEVEL","error");
    }

    static void configurePresentGuard(Context context,Map<String,String> env) throws IOException {
        File guard=new File(context.getApplicationInfo().nativeLibraryDir,"libdrnas_xcb_present_guard.so");
        if(!guard.isFile())throw new IOException("Packaged XCB Present guard missing");
        String path=guard.getAbsolutePath();
        String previous=env.getOrDefault("LD_PRELOAD","");
        if(!previous.contains(path))env.put("LD_PRELOAD",path+(previous.isEmpty()?"":" "+previous));
        env.put("DRNAS_XCB_PRESENT_GUARD","1");
    }

    static void probeWine(Context context,File gui,Map<String,String> env,
                          Consumer<String> log,RawWineTrace raw) {
        probeWine(context,gui,env,log,raw,false,false);
    }

    static void probeWine(Context context,File gui,Map<String,String> env,
                          Consumer<String> log,RawWineTrace raw,boolean present) {
        probeWine(context,gui,env,log,raw,present,false);
    }

    static void probeWine(Context context,File gui,Map<String,String> env,
                          Consumer<String> log,RawWineTrace raw,boolean present,boolean immediate) {
        probeWine(context,gui,env,log,raw,present,immediate?"immediate":"fifo");
    }

    static boolean probeWine(Context context,File gui,Map<String,String> env,
                          Consumer<String> log,RawWineTrace raw,boolean present,String testMode) {
        // Probe even without a bundled Turnip selection: distinguish Vulkan
        // enumeration from an X11 WSI/Direct3D adapter failure.
        if(!env.containsKey("VK_DRIVER_FILES"))
            log.accept("No Turnip ICD override; probing native system Vulkan availability.");
        Process p=null;
        try {
            File nativeDir=new File(context.getApplicationInfo().nativeLibraryDir);
            File loader=new File(gui,"lib/libvulkan.so.1");
            java.util.List<String> command=new java.util.ArrayList<>(java.util.Arrays.asList(
                    "/system/bin/linker64",new File(nativeDir,"libdrnas_vulkan_probe.so").getAbsolutePath(),loader.getAbsolutePath()));
            if(!present)command.add("--enumerate-only");
            else if("immediate".equals(testMode))command.add("--present-immediate");
            else if("create-only".equals(testMode))command.add("--create-destroy");
            else if("sync".equals(testMode))command.add("--present-sync");
            else if("x11-only".equals(testMode))command.add("--x11-only");
            else if("create-checkpoints".equals(testMode))command.add("--create-checkpoints");
            else if("present-checkpoints".equals(testMode))command.add("--present-checkpoints");
            else if(present&&!"fifo".equals(testMode))throw new IOException("Unknown experiment: "+testMode);
            log.accept(present ? "DRNAS_WSI_TEST mode="+testMode+
                    " (15 second isolated child, native watcher at 3s; inspect complete cleanup)" :
                    "Vulkan startup test: enumerate backend only.");
            ProcessBuilder pb=new ProcessBuilder(command).redirectErrorStream(true);
            pb.environment().clear();pb.environment().putAll(env);pb.environment().remove("LD_PRELOAD");
            configurePresentGuard(context,pb.environment());
            log.accept("DRNAS_EXPERIMENT_BEGIN mode="+testMode+" | isolated child; Explorer stays running");
            p=pb.start();Thread output=WineServerDiagnostics.pipe(p,"[Wine Vulkan] ",log,20,raw);
            boolean ended=p.waitFor(15,java.util.concurrent.TimeUnit.SECONDS);
            if(!ended)p.destroyForcibly();
            output.join(1000);
            log.accept("DRNAS_EXPERIMENT_END mode="+testMode+" result="+(!ended?"TIMEOUT":p.exitValue()==0?"PASS":"FAIL exit="+p.exitValue()));
            if(!ended)
                log.accept("DRNAS_WSI_TIMEOUT: native "+(present?"present/teardown":"enumeration")+
                        " probe exceeded 15 seconds; check LAST DRNAS_WSI_TEARDOWN marker to locate blocked cleanup. VK_SUCCESS on Present does not imply clean shutdown.");
            else if(p.exitValue()==0)
                log.accept("x11-only".equals(testMode)?"Native X11 lifecycle PASSED (no Vulkan loaded).":
                        "create-checkpoints".equals(testMode)||"create-only".equals(testMode)?"Native Vulkan create/destroy and X11 cleanup PASSED (no Present attempted).":present ? "Native Vulkan X11 PRESENT AND CLEANUP PASSED. Test Direct3D separately to verify the Wine/DXVK path." :
                        "Native Vulkan GPU enumeration PASSED. Windows DXVK adapter/presentation remain unverified.");
            else log.accept("Native Vulkan "+(present?"presentation/cleanup":"enumeration")+" FAILED (exit="+p.exitValue()+"); inspect [Wine Vulkan]. Exit 12 means requested Turnip was NOT verified; no silent success on the system driver.");
            return ended && p.exitValue()==0;
        } catch(Exception e){log.accept("Wine Vulkan probe: "+e.getMessage());return false;}
        finally {if(p!=null && p.isAlive())p.destroyForcibly();}
    }

    private TurnipRuntime(){}
}




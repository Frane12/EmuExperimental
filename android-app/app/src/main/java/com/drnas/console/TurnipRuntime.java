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
    private static final Pattern ADRENO =
            Pattern.compile("(?i)adreno\\s*(?:\\(tm\\))?[^0-9]{0,12}(810|830|840)(?![0-9])");

    static String gpuTarget(String gpuName) {
        if(gpuName==null) return "";
        Matcher match=ADRENO.matcher(gpuName);
        return match.find()?"A"+match.group(1):"";
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
            return walk.filter(p->p.getFileName().toString().equals("libvulkan_freedreno.so"))
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
        Files.write(descriptor.toPath(),icd.toString(2).getBytes(StandardCharsets.UTF_8));
        env.put("VK_DRIVER_FILES",descriptor.getAbsolutePath());
        env.put("VK_ICD_FILENAMES",descriptor.getAbsolutePath());
        env.put("ADRENOTOOLS_HOOKS_PATH",lib.getAbsolutePath()+"/");
        env.remove("ADRENOTOOLS_DRIVER_PATH");env.remove("ADRENOTOOLS_DRIVER_NAME");
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

    static void probeWine(Context context,File gui,Map<String,String> env,
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
        } catch(Exception e){log.accept("Wine Vulkan probe: "+e.getMessage());}
        finally {if(p!=null && p.isAlive())p.destroyForcibly();}
    }

    private TurnipRuntime(){}
}



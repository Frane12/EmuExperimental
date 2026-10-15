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
        env.put("VK_ICD_FILENAMES",icd.getAbsolutePath());
        env.put("VK_DRIVER_FILES",icd.getAbsolutePath());
        env.put("ADRENOTOOLS_DRIVER_PATH",driver.getParentFile().getAbsolutePath());
        env.put("ADRENOTOOLS_DRIVER_NAME",driver.getName());
        env.put("DRNAS_TURNIP_PREFERRED","1");
        env.put("LD_LIBRARY_PATH",driver.getParentFile().getAbsolutePath()+":"+
                env.getOrDefault("LD_LIBRARY_PATH",""));
        log.accept("Requested Drnas Turnip by default for Wine via ICD override: "+
                driver.getName()+". Actual Vulkan device usage requires an in-guest GPU probe; "+
                "Android's system Vulkan loader may ignore ICD env vars without AdrenoTools.");
    }

    static void probeWine(Context context,File gui,Map<String,String> env,
                          Consumer<String> log,RawWineTrace raw) {
        // Probe even without a bundled Turnip selection: distinguish Vulkan
        // enumeration from an X11 WSI/Direct3D adapter failure.
        if(!env.containsKey("VK_DRIVER_FILES"))
            log.accept("No Turnip ICD override; probing native system Vulkan availability.");
        Process p=null;
        try {
            File nativeDir=new File(context.getApplicationInfo().nativeLibraryDir);
            File loader=new File(gui,"lib/libvulkan.so.1");
            ProcessBuilder pb=new ProcessBuilder("/system/bin/linker64",
                    new File(nativeDir,"libdrnas_vulkan_probe.so").getAbsolutePath(),loader.getAbsolutePath()).redirectErrorStream(true);
            pb.environment().clear();pb.environment().putAll(env);pb.environment().remove("LD_PRELOAD");
            p=pb.start();Thread output=WineServerDiagnostics.pipe(p,"[Wine Vulkan] ",log,20,raw);
            boolean ended=p.waitFor(8,java.util.concurrent.TimeUnit.SECONDS);
            if(!ended)p.destroyForcibly();
            output.join(1000);
            if(ended && p.exitValue()==0)
                log.accept("Native Vulkan physical device enumeration PASSED. Inspect '[Wine Vulkan]' for vendor/device and Turnip marker. DXVK Windows adapter and X11 DRI3/Present are NOT verified.");
            else log.accept("Native Vulkan enumeration FAILED (exit="+(ended?p.exitValue():-1)+"); DXVK adapter cannot be assumed. Inspect [Wine Vulkan] for details.");
        } catch(Exception e){log.accept("Wine Vulkan probe: "+e.getMessage());}
        finally {if(p!=null && p.isAlive())p.destroyForcibly();}
    }

    private TurnipRuntime(){}
}


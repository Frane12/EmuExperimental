package com.drnas.console;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Experimental container bootstrap:
 * 1) Verify FEX PE bridges already imported by ComponentStore.
 * 2) Extract user-imported Proton 10 / DXVK WCP packages and Turnip ZIP.
 * 3) Prepare a disposable Wine prefix, assemble FEX and DXVK layers.
 * 4) Perform a real Android host process preflight.
 *
 * A Linux glibc Wine loader, X server, audio server and Vulkan ICD host bridge
 * are NOT bundled. Unlike the Android shell, a Windows runtime must not be
 * marked RUNNING until a real guest process reports readiness.
 */
public final class ContainerBootstrap {
    private static final String[] FEX_DLLS={"libarm64ecfex.dll","libwow64fex.dll"};
    public interface Callback {
        void onLog(String msg);
        void onComplete(boolean guestStarted,String status);
    }
    private final Context ctx;
    private final Callback callback;
    private volatile boolean busy;

    public ContainerBootstrap(Context context,Callback callback) {
        ctx=context.getApplicationContext();this.callback=callback;
    }
    public synchronized boolean isBusy() {return busy;}
    public synchronized boolean start() {
        if(busy)return false;
        busy=true;
        new Thread(()->{
            String result="BOOT BLOCKED";boolean guestStarted=false;
            try {
                prepare();
                result="NDK HOST READY / GLIBC GUEST LOADER MISSING";
            } catch(Throwable failure) {
                result="BOOT BLOCKED: "+failure.getClass().getSimpleName()+": "+failure.getMessage();
                log(result);
            } finally {
                synchronized(ContainerBootstrap.this){busy=false;}
                callback.onComplete(guestStarted,result);
            }
        },"drnas-container-bootstrap").start();
        return true;
    }
    private void log(String msg){callback.onLog("[container] "+msg);}
    private static boolean isSha(String sha) {
        return sha!=null && sha.matches("[0-9a-f]{64}");
    }
    private static File existing(File folder,String name) throws Exception {
        File f=new File(folder,name);
        if(!f.isFile())throw new IOException("Missing "+f.getAbsolutePath());
        return f;
    }
    private static void copyAtomic(File from,File into) throws Exception {
        if(!from.isFile())throw new IOException("Missing file "+from);
        File parent=into.getParentFile();
        if(!parent.isDirectory() && !parent.mkdirs())throw new IOException("mkdir "+parent);
        File tmp=new File(parent,".tmp-"+into.getName()+"-"+System.nanoTime());
        boolean done=false;
        try(InputStream input=new FileInputStream(from); OutputStream output=new FileOutputStream(tmp)) {
            byte[] buffer=new byte[65536];int n;
            while((n=input.read(buffer))!=-1)if(n>0)output.write(buffer,0,n);
        }
        try {
            if(into.exists()) {
                File backup=new File(parent,into.getName()+".drnas-backup");
                if(!backup.exists() && !into.renameTo(backup))
                    throw new IOException("Cannot save original DLL "+into.getName());
                // Our container-only copy may be refreshed after a repeated boot.
                if(into.exists() && !into.delete())throw new IOException("Cannot update DLL");
            }
            if(!tmp.renameTo(into))throw new IOException("Cannot apply DLL layer");
            done=true;
        } finally {if(!done)tmp.delete();}
    }
    private static String hash(File file) throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) {
            byte[] buffer=new byte[65536];int n;
            while((n=in.read(buffer))!=-1)if(n>0)d.update(buffer,0,n);
        }
        StringBuilder b=new StringBuilder();
        for(byte value:d.digest())b.append(String.format(java.util.Locale.US,"%02x",value&255));
        return b.toString();
    }
    private static JSONObject findBridge(List<JSONObject> entries) {
        for(JSONObject entry:entries)
            if(entry.optString("status").equals("fex_bridge_pair_verified_not_activated"))
                return entry.optJSONObject("fexBridge");
        return null;
    }
    private static List<JSONObject> inventory(File vault) throws Exception {
        File[] records=vault.listFiles((dir,name)->name.endsWith(".json"));
        if(records==null)return new ArrayList<>();
        Arrays.sort(records,Comparator.comparingLong(File::lastModified).reversed());
        List<JSONObject> found=new ArrayList<>();
        for(File file:records) {
            try {
                JSONObject obj=WcpRuntimeStager.readJson(file);
                if(isSha(obj.optString("sha256")))found.add(obj);
            } catch(Exception ignored){}
        }
        return found;
    }
    private void prepare() throws Exception {
        File vault=new File(ctx.getFilesDir(),"wcp-vault");
        if(!vault.isDirectory())throw new IOException("No WCP vault. Import FEXCore + Proton + DXVK first.");
        List<JSONObject> entries=inventory(vault);
        JSONObject bridge=findBridge(entries);
        if(bridge==null)throw new IOException("FEX 2609 is not verified; import its WCP first");
        log("FEX PE bridge verified in vault: "+bridge.optString("versionName"));
        File bridgeDir=new File(bridge.getString("stagedAt"));
        for(String dll:FEX_DLLS)existing(bridgeDir,dll);

        WcpRuntimeStager.PackageInfo proton=null,dxvk=null,turnip=null;
        List<String> unsupported=new ArrayList<>();
        for(JSONObject record:entries) {
            String sha=record.optString("sha256");
            String compression=record.optString("compression");
            File archive=new File(vault,sha+".wcp");
            if("ZIP".equals(compression)) {
                // Imported driver ZIP can have its original extension; archive is
                // content-addressed in the same vault for safekeeping.
                if(turnip==null && archive.isFile()) {
                    try {turnip=WcpRuntimeStager.stageTurnipZip(archive,vault,sha,this::log);}
                    catch(Exception e){unsupported.add("Turnip: "+e.getMessage());}
                }
            } else if(!"FEXCore".equals(record.optString("componentType")) && archive.isFile()) {
                if(proton!=null && dxvk!=null)continue;
                try {
                    WcpRuntimeStager.PackageInfo p=WcpRuntimeStager.stageWcp(archive,vault,sha,this::log);
                    if(("Proton".equals(p.kind) || "Wine".equals(p.kind))&&proton==null)proton=p;
                    if("DXVK".equals(p.kind)&&dxvk==null)dxvk=p;
                } catch(Exception e) {
                    unsupported.add(record.optString("name")+": "+e.getMessage());
                }
            }
        }
        for(String note:unsupported)log("Package skipped: "+note);
        if(proton==null)throw new IOException("Proton ARM64EC .wcp missing or invalid. Import it in Components.");
        if(dxvk==null)throw new IOException("DXVK 1.10.3 .wcp missing or invalid. Import it in Components.");
        if(turnip==null)throw new IOException("Turnip driver .zip missing or invalid. Import our .zip in Components.");
        log("Selected Proton "+proton.version+", DXVK "+dxvk.version+", "+turnip.version);

        File base=new File(ctx.getFilesDir(),"containers/default");
        if(!base.isDirectory()&&!base.mkdirs())throw new IOException("Cannot allocate sandbox");
        File prefix=new File(base,"prefix");
        JSONObject wineConfig=proton.profile.optJSONObject("proton");
        if(wineConfig==null)wineConfig=proton.profile.optJSONObject("wine");
        if(wineConfig==null)throw new IOException("Proton has no executable paths");
        String prefixPackPath=WcpRuntimeStager.clean(wineConfig.getString("prefixPack"),false);
        log("Provisioning isolated prefix from "+prefixPackPath);
        WcpRuntimeStager.unpackPrefixPack(
                existing(proton.folder,prefixPackPath),prefix,this::log);
        File winDir=findWindowsDir(prefix);
        log("Isolated prefix: "+winDir.getAbsolutePath());

        String[] expected={"arm64ecBridgeSha256","wow64BridgeSha256"};
        for(int i=0;i<FEX_DLLS.length;i++) {
            File dll=existing(bridgeDir,FEX_DLLS[i]);
            if(!hash(dll).equalsIgnoreCase(bridge.getString(expected[i])))
                throw new IOException("FEX checksum mismatch: "+FEX_DLLS[i]);
            copyAtomic(dll,new File(winDir,"system32/"+FEX_DLLS[i]));
        }
        log("Applied verified FEX PE pair to container-only system32");

        JSONArray mappings=dxvk.profile.getJSONArray("files");
        int applied=0;
        for(int i=0;i<mappings.length();i++) {
            JSONObject mapping=mappings.getJSONObject(i);
            String target=mapping.getString("target");
            String subdir;
            if(target.startsWith("${system32}/"))subdir="system32";
            else if(target.startsWith("${syswow64}/"))subdir="syswow64";
            else throw new IOException("Untrusted DXVK target");
            String source=WcpRuntimeStager.clean(mapping.getString("source"),false);
            String name=source.substring(source.lastIndexOf('/')+1);
            if(!target.equals("${"+subdir+"}/"+name))throw new IOException("Mismatch DXVK target");
            copyAtomic(existing(dxvk.folder,source),new File(winDir,subdir+"/"+name));
            applied++;
        }
        log("Applied "+applied+" DXVK DLLs to the isolated prefix only");

        File wine=new File(proton.folder,
                WcpRuntimeStager.clean(wineConfig.getString("binPath"),false)+"/wine");
        if(!wine.isFile())throw new IOException("Missing Wine entrypoint in Proton package");
        if(!NativeHost.available())
            throw new IOException("NDK ARM64 host library unavailable: "+NativeHost.loadError());
        log("[ndk] "+NativeHost.hostInfo());
        log("[ndk] "+NativeHost.probeFile(wine.getAbsolutePath()));
        log("[ndk] "+NativeHost.vulkanInfo());
        JSONObject plan=new JSONObject();
        plan.put("proton",proton.version).put("protonWine",wine.getAbsolutePath())
            .put("fexCore",bridge.getString("versionName")).put("dxvk",dxvk.version)
            .put("turnipArchive",turnip.version).put("turnipStore",turnip.folder.getAbsolutePath())
            .put("prefix",prefix.getAbsolutePath()).put("hostApi",Build.VERSION.SDK_INT)
            .put("guestExecutableStarted",false)
            .put("nativeBionicHostAvailable",NativeHost.available())
            .put("wineLoaderConnected",false).put("xServerConnected",false)
            .put("audioConnected",false).put("turnipVulkanICDConnected",false);
        WcpRuntimeStager.writeJson(new File(base,"boot-plan.json"),plan);
        log("Boot plan written: "+new File(base,"boot-plan.json"));
        boolean vulkan=ctx.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
        log("Android Vulkan feature: "+(vulkan?"present":"NOT advertised")+
                "; custom Turnip ICD is staged, not loaded");
        // v0.4 now runs real compiled ARM64 code via JNI; the native code
        // probes guest ELF and Android Vulkan, but it is not an ELF loader.
        // Never pass untrusted Wine/Proton executables to ProcessBuilder:
        // dynamic glibc/PT_INTERP, executable storage and X11 must be solved.
        log("[ndk] Actual Bionic ARM64 native library executed successfully.");
        log("BOOT WAITING: glibc userland/ELF interpreter, X11 display, audio and Turnip ICD activation.");
        log("Wine guest process NOT STARTED; FEX DLL files are bridges, not an executable CPU emulator.");
        log("Use the Wine ELF/PT_INTERP diagnostic above to select a compatible runtime adapter.");
    }
    private static File findWindowsDir(File prefix) throws IOException {
        File[] roots={
                new File(prefix,"drive_c/windows"),
                new File(prefix,"prefix/drive_c/windows"),
                new File(prefix,"wineprefix/drive_c/windows"),
                new File(prefix,".wine/drive_c/windows")
        };
        for(File root:roots)if(root.isDirectory())return root;
        throw new IOException("prefixPack has no drive_c/windows; check Proton template layout");
    }
}

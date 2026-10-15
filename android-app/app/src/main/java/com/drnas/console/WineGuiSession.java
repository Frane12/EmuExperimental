package com.drnas.console;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** A bounded GUI session on the already verified disposable Wineboot prefix. */
final class WineGuiSession implements AutoCloseable {
    private static WineGuiSession active;
    private final java.util.concurrent.CountDownLatch finished=new java.util.concurrent.CountDownLatch(1);
    private volatile boolean stopped;
    private volatile PulseAudioRuntime audio;
    private volatile Thread folderWatcher;

    static void stopActive() throws Exception {
        WineGuiSession current;
        synchronized(WineGuiSession.class){current=active;}
        if(current!=null){
            current.close();
            if(!current.finished.await(10,TimeUnit.SECONDS))throw new IOException("Previous Wine session is still stopping");
        }
    }
    private volatile Process launcher;
    private volatile WineServerDiagnostics.ManagedServer server;

    static final int DESKTOP_WIDTH=1280;
    static final int DESKTOP_HEIGHT=720;
    static List<String> command(File linker,File wine) {
        return command(linker,wine,"desktop");
    }
    static List<String> command(File linker,File wine,String app) {
        List<String> args=new ArrayList<>(WineLaunchProbe.linkerCommand(linker,wine));
        // Explorer is the persistent guest desktop. Do not start winecfg by
        // default; keep it available for explicit maintenance only.
        args.set(args.size()-1,"explorer.exe");
        // Wine's special desktop name "shell" enables its built-in taskbar
        // and Start menu; "DRNAS" used in v0.5.17 intentionally did not.
        args.add("/desktop=shell,"+DESKTOP_WIDTH+"x"+DESKTOP_HEIGHT);
        if ("files".equals(app) || "desktop".equals(app)) args.add("winefile.exe");
        else if ("config".equals(app)) args.add("winecfg.exe");
        else if (!"desktop".equals(app)) throw new IllegalArgumentException("Unknown desktop mode");
        return args;
    }
    static void displayEnvironment(Map<String,String> env) {
        env.put("DISPLAY","127.0.0.1:12.0");
        // Prefer native DXVK DLLs in the isolated Wine prefix; without this,
        // d3d9.dll can silently fall back to WineD3D and mask missing Vulkan.
        env.put("WINEDLLOVERRIDES","winex11.drv=b;wineandroid.drv=d;winewayland.drv=d;"+
                "d3d9=n,b;dxgi=n,b;d3d11=n,b;d3d10core=n,b");
        // Avoid high-volume per-module / X11 tracing on every GUI operation.
        // Full logs can still be exported from the app when needed.
        env.put("WINEDEBUG","-all");
    }

    void run(Context context,Consumer<String> log,RawWineTrace raw) throws Exception {
        run(context,log,raw,"desktop");
    }
    void run(Context context,Consumer<String> log,RawWineTrace raw,String app) throws Exception {
        synchronized(WineGuiSession.class) {
            if(active!=null)throw new IOException("A previous desktop is still stopping; retry shortly");
            active=this;
        }
        try { runOwned(context,log,raw,app); }
        finally {
            close();
            synchronized(WineGuiSession.class){if(active==this)active=null;}
            finished.countDown();
        }
    }
    private void runOwned(Context context,Consumer<String> log,RawWineTrace raw,String app) throws Exception {
        File base=new File(context.getFilesDir(),"containers/default");
        JSONObject plan=new JSONObject(new String(Files.readAllBytes(
                new File(base,"boot-plan.json").toPath()),StandardCharsets.UTF_8));
        if(!plan.optBoolean("winebootGuestVerified"))
            throw new IOException("Run successful isolated Wineboot test first.");
        // v0.5.5 plans predate protonRoot: their bin/wine path is authoritative.
        File proton=plan.has("protonRoot")?new File(plan.getString("protonRoot")):
                new File(plan.getString("protonWine")).getParentFile().getParentFile();
        proton=proton.getCanonicalFile();
        if(!proton.toPath().startsWith(new File(context.getFilesDir(),
                "wcp-vault/runtime-packages").getCanonicalFile().toPath()))
            throw new IOException("Runtime root outside staged vault");
        File prefix=new File(base,"wineboot-probe/template/.wine").getCanonicalFile();
        if(!new File(prefix,"drive_c/windows").isDirectory())
            throw new IOException("Verified Wineboot prefix missing");
        WinePrefixLayout.ensure(prefix,log);
        SharedFolderDrive.mapWineDrive(context,prefix,log);
        File wine=WineLaunchProbe.selectWine(proton,log);
        if(wine==null)throw new IOException("Bionic Wine entry missing");
        File lib=new File(proton,plan.optString("protonLibPath","lib")).getCanonicalFile();
        if(!lib.toPath().startsWith(proton.toPath()) || !lib.isDirectory())
            throw new IOException("Invalid Proton library directory");
        File nativeDir=new File(context.getApplicationInfo().nativeLibraryDir);
        File tmp=new File(base,"gui-tmp");if(!tmp.isDirectory()&&!tmp.mkdirs())throw new IOException("GUI tmp unavailable");
        Map<String,String> env=new HashMap<>(System.getenv());
        env.put("WINEPREFIX",prefix.getAbsolutePath());env.put("WINEARCH","win64");
        env.put("HOME",base.getAbsolutePath());env.put("TMPDIR",tmp.getAbsolutePath());
        env.put("XDG_RUNTIME_DIR",tmp.getAbsolutePath());env.put("LC_ALL","C");
        StringBuilder libs=new StringBuilder(nativeDir.getAbsolutePath());
        for(File dir:new File[]{lib,new File(lib,"wine/aarch64-unix"),new File(lib,"wine")})
            if(dir.isDirectory())libs.append(":").append(dir.getAbsolutePath());
        env.put("LD_LIBRARY_PATH",libs.toString());
        // A log without device enumeration is not proof of a DXVK adapter.
        env.put("DXVK_LOG_LEVEL","info");
        env.put("DXVK_LOG_PATH",tmp.getAbsolutePath());
        for(String dll:new String[]{"d3d9.dll","dxgi.dll","d3d11.dll"}) {
            File x64=new File(prefix,"drive_c/windows/system32/"+dll);
            File x86=new File(prefix,"drive_c/windows/syswow64/"+dll);
            log.accept("DXVK staged "+dll+" x64="+(x64.isFile()?x64.length()+" bytes":"MISSING")+
                    " x86="+(x86.isFile()?x86.length()+" bytes":"MISSING"));
        }
        env.put("WINEDLLPATH",new File(lib,"wine").getAbsolutePath());
        env.put("WINELOADER",wine.getAbsolutePath());
        File serverElf=new File(proton,"bin/wineserver");
        env.put("WINESERVER",serverElf.getAbsolutePath());env.put("PROTON_NO_NTSYNC","1");
        WineBootProbe.configureBootstrapMode(env);
        displayEnvironment(env);
        File guiRuntime=WineGuiRuntime.install(context,log);
        WineGuiRuntime.configure(guiRuntime,prefix,env);
        TurnipRuntime.configureWine(context,plan,env,log);
        TurnipRuntime.probeWine(context,guiRuntime,env,log,raw);
        if(stopped)return;
        audio=new PulseAudioRuntime();
        try {audio.start(context,env,log,raw);}
        catch(Exception e) {audio.close();log.accept("Audio unavailable: "+e.getMessage()+"; desktop remains available.");}
        File linker=new File("/system/bin/linker64");
        X11Probe.check(6012);log.accept("X11 protocol 11 handshake VERIFIED; DISPLAY="+env.get("DISPLAY"));
        for(String relative:new String[]{"wine/aarch64-unix/winex11.so",
                "wine/aarch64-windows/winex11.drv","wine/aarch64-windows/winecfg.exe"}) {
            File f=new File(lib,relative);log.accept("GUI component "+relative+": "+(f.isFile()?f.length()+" bytes":"MISSING"));
        }
        File[] dependencies=lib.listFiles((dir,name)->name.startsWith("libX") || name.contains("xcb") || name.contains("freetype") || name.contains("fontconfig"));
        if(dependencies!=null)for(File f:dependencies)log.accept("GUI runtime library: "+f.getName());
        WineGuiRuntime.probe(nativeDir,new File(lib,"wine/aarch64-unix/winex11.so"),env,log,raw);
        if(stopped)return;
        server=WineServerDiagnostics.prestart(linker,serverElf,proton,nativeDir,env,log,raw);
        if(server==null)throw new IOException("GUI wineserver failed to start");
        try {
            if(stopped)return;
            WineBootProbe.configureChildExeRelay(env,nativeDir,wine);
            WineBootProbe.configureSecondStage(env);
            // The desktop is held until one-time prerequisite installers complete.
            // A missing MSI/EXE never masquerades as an installed dependency.
            WinePrerequisites.installPending(context,prefix,linker,wine,proton,env,raw,log);
            if(stopped)return;
            // Wine explorer.exe's /desktop=shell paints a solid background unless
            // the guest desktop wallpaper is installed and registered. A proper
            // Windows BMP is staged inside xuser/Pictures; failure never blocks GUI.
            try { WineWallpaper.ensure(prefix,linker,wine,proton,env,log,raw); }
            catch(Exception error) { log.accept("Wine wallpaper setup skipped: "+error.getMessage()); }
            if(stopped)return;
            startFolderWatcher(context,log);
            log.accept("Launching Wine explorer.exe virtual desktop "+
                    DESKTOP_WIDTH+"x"+DESKTOP_HEIGHT+
                    ("files".equals(app)?" with winefile.exe":
                     "config".equals(app)?" with winecfg.exe":" with taskbar and file manager")+
                    "; HWUI desktop + Mesa Vulkan X11 readback presentation (experimental).");
            ProcessBuilder pb=new ProcessBuilder(command(linker,wine,app)).directory(proton).redirectErrorStream(true);
            pb.environment().clear();pb.environment().putAll(env);
            launcher=pb.start();
            if(stopped){launcher.destroyForcibly();return;}
            Thread output=WineServerDiagnostics.pipe(launcher,"[GUI Wine] ",log,60,raw);
            long until=System.nanoTime()+TimeUnit.HOURS.toNanos(4);
            boolean exitReported=false;
            while(!stopped && server.alive() && System.nanoTime()<until) {
                // Explorer can delegate its window to a child and return.
                // Exit alone is never proof that a GUI exists.
                if(!launcher.isAlive()&&!exitReported) {
                    log.accept("GUI launcher exit="+launcher.exitValue()+"; inspect X11 window mapping evidence.");
                    exitReported=true;
                }
                Thread.sleep(200);
            }
            log.accept(stopped?"GUI stopped by user/lifecycle.":"GUI bounded session ended.");
            if(launcher.isAlive())launcher.destroyForcibly();
            server.close();
            output.join(1500);
            launcher.getInputStream().close();
        } finally {close();}
    }
    private void startFolderWatcher(Context context,Consumer<String> log) {
        if(SharedFolderDrive.selectedTree(context)==null)return;
        folderWatcher=new Thread(()->{
            while(!stopped && !Thread.currentThread().isInterrupted()) {
                try {
                    String summary=SharedFolderDrive.importSelected(context,s->{});
                    if(!summary.startsWith("R: imported 0 file(s)"))
                        log.accept("[R: live refresh] "+summary);
                } catch(Exception e) {
                    if(!stopped)log.accept("R: live refresh failed: "+e.getMessage());
                }
                try {Thread.sleep(20000);}
                catch(InterruptedException interrupted){return;}
            }
        },"drnas-r-drive-refresh");
        folderWatcher.setDaemon(true);
        folderWatcher.start();
    }
    @Override public void close() {
        stopped=true;
        Thread watcher=folderWatcher;if(watcher!=null)watcher.interrupt();
        PulseAudioRuntime a=audio;if(a!=null)a.close();
        Process p=launcher;if(p!=null&&p.isAlive())p.destroyForcibly();
        WineServerDiagnostics.ManagedServer s=server;if(s!=null)s.close();
    }
}




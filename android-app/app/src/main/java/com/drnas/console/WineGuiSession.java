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
    // Volatile: menu toggle takes effect for Java/X11 diagnostics immediately.
    private volatile boolean diagnosticsEnabled;
    private volatile int frameCap;
    private volatile String dxvkVariant;
    void setDxvkVariant(String value){dxvkVariant=value;}
    void setFrameCap(int cap){frameCap=DxvkFrameCap.clamp(cap);}
    void setDiagnosticsEnabled(boolean enabled) { diagnosticsEnabled=enabled; }
    private final java.util.concurrent.CountDownLatch finished=new java.util.concurrent.CountDownLatch(1);
    private volatile boolean stopped;
    private volatile PulseAudioRuntime audio;
    private volatile Thread sessionThread;
    private volatile GuestTools guestTools;
    private volatile Thread vulkanProbeThread;

    static void stopActive() throws Exception {
        WineGuiSession current;
        synchronized(WineGuiSession.class){current=active;}
        if(current!=null){
            current.close();
            if(!current.finished.await(10,TimeUnit.SECONDS))throw new IOException("Previous Wine session is still stopping");
        }
    }
    private volatile Process launcher;
    private volatile Process desktopCompanion;
    private volatile WineServerDiagnostics.ManagedServer server;

    static final int DESKTOP_WIDTH=1280;
    static final int DESKTOP_HEIGHT=720;
    static List<String> command(File linker,File wine) {
        return command(linker,wine,"desktop");
    }
    static List<String> command(File linker,File wine,String app) {
        return command(linker,wine,app,Collections.emptyList());
    }
    static List<String> command(File linker,File wine,String app,List<String> launchArguments) {
        List<String> args=new ArrayList<>(WineLaunchProbe.linkerCommand(linker,wine));
        // Explorer is the persistent guest desktop. Do not start winecfg by
        // default; keep it available for explicit maintenance only.
        args.set(args.size()-1,"explorer.exe");
        // Wine's special desktop name "shell" enables its built-in taskbar
        // and Start menu; "DRNAS" used in v0.5.17 intentionally did not.
        args.add("/desktop=shell,"+DESKTOP_WIDTH+"x"+DESKTOP_HEIGHT);
        if ("files".equals(app) || "desktop".equals(app)) args.add("winefile.exe");
        else if ("config".equals(app)) args.add("winecfg.exe");
        else if ("tasks".equals(app)) args.add("taskmgr.exe");
        else if (app.startsWith("builtin:")) {
            String exe=WineShortcuts.builtinExecutable(app.substring(8));
            if(exe==null)throw new IllegalArgumentException("Unknown built-in tool");
            args.add(exe);
        } else if(app.startsWith("linkwin:") || app.startsWith("execwin:")) {
            // explorer.exe expects a Wine DOS path, not an Android Unix file.
            // The root and path are validated/translated in runOwned first.
            args.add(app.substring(8));
            if(launchArguments!=null)args.addAll(launchArguments);
        } else if(app.startsWith("link:") || app.startsWith("exec:")) {
            // Legacy path retained only for old unit-test/tool launch callers.
            args.add("start.exe");
            args.add("/unix");
            args.add(app.substring(5));
            if(launchArguments!=null)args.addAll(launchArguments);
        } else if (!"desktop".equals(app)) throw new IllegalArgumentException("Unknown desktop mode");
        return args;
    }
    /** Validate a real source file and return its C:/R: DOS path to the Wine
     * shell. Passing /data/user/0/... to Explorer caused 'path not found'
     * for apps copied from Android R:. Preserve spaces as one argv element. */
    static String windowsPath(File root,File target,char drive) throws IOException {
        if(drive!='C' && drive!='R')throw new IOException("Unsupported drive");
        File base=root.getCanonicalFile(), file=target.getCanonicalFile();
        if(!file.isFile() || !file.toPath().startsWith(base.toPath()))
            throw new IOException("Program path is no longer inside Wine "+drive+": drive");
        String relative=base.toPath().relativize(file.toPath()).toString()
                .replace(File.separatorChar,'\\');
        if(relative.isEmpty() || relative.indexOf((char)0)>=0)
            throw new IOException("Invalid guest program path");
        return drive+":\\"+relative;
    }
    private static String userWindowsPath(Context context,File prefix,File executable) throws IOException {
        File c=new File(prefix,"drive_c").getCanonicalFile();
        if(executable.getCanonicalFile().toPath().startsWith(c.toPath()))
            return windowsPath(c,executable,'C');
        File r=SharedFolderDrive.mirror(context).getCanonicalFile();
        return windowsPath(r,executable,'R');
    }
    static void displayEnvironment(Map<String,String> env) {
        env.put("DISPLAY","127.0.0.1:12.0");
        // Prefer native DXVK DLLs in the isolated Wine prefix; without this,
        // d3d9.dll can silently fall back to WineD3D and mask missing Vulkan.
        env.put("WINEDLLOVERRIDES","winex11.drv=b;wineandroid.drv=d;winewayland.drv=d;"+
                "d3d9=n,b;dxgi=n,b;d3d11=n,b;d3d10core=n,b");
        // Avoid high-volume per-module / X11 tracing on every GUI operation.
        // Full logs can still be exported from the app when needed.
        // Process creation/termination traces identify which guest PID dies:
        // Explorer can remain alive even when a game exits immediately.
        env.put("WINEDEBUG","-all,err+all"); // quiet default, no per-process traces
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
        sessionThread=Thread.currentThread();
        // R: is persistent. Import only on explicit folder selection / manual refresh.
        // Starting a desktop or shortcut must never rescan the Android document tree.
        if(stopped)return;
        log.accept("[Startup] Priprema Windows okruženja");
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
        final AppLaunchSettings.Profile appProfile=AppLaunchSettings.load(context,app);
        final String guestTarget=app.startsWith("link:") ?
                "linkwin:"+windowsPath(new File(prefix,"drive_c"),
                        WineShortcuts.resolveLink(prefix,app.substring(5)),'C'):
                UserShortcuts.isId(app)?"execwin:"+userWindowsPath(context,prefix,
                        UserShortcuts.resolve(context,app)):app;
        if(guestTarget.startsWith("linkwin:") || guestTarget.startsWith("execwin:"))
            log.accept("DRNAS_GUEST_PATH_OK: "+guestTarget.substring(8)+
                    " (Wine DOS path; not Android /data/... path)");
        // Saved Blender --gpu-backend vulkan must also select WineVulkan
        // when the per-app graphics preference has been left on Auto.
        final boolean nativeVulkan=appProfile.nativeVulkan() ||
                ("auto".equals(appProfile.graphicsApi) &&
                 LaunchArguments.explicitlyRequestsVulkan(appProfile.launchArgs));
        if(nativeVulkan && !appProfile.nativeVulkan())
            log.accept("DRNAS_WINEVULKAN_AUTO: explicit --gpu-backend vulkan in shortcut arguments");
        // A shell .lnk is not an executable. explorer.exe may ignore trailing
        // flags on .lnk files, so use our Wine-side IShellLinkW launcher when
        // an explicit argument or Native Vulkan is requested.
        final List<String> linkLaunchArgs=guestTarget.startsWith("linkwin:")?
                LaunchArguments.forLink(guestTarget.substring(8),appProfile.launchArgs,nativeVulkan):
                Collections.emptyList();
        final boolean linkViaHelper=!linkLaunchArgs.isEmpty();
        final String launchMode=linkViaHelper?
                "execwin:C:\\DRNAS\\Tools\\DRNASLinkLaunch.exe":guestTarget;
        final List<String> launchArgs=linkViaHelper?linkLaunchArgs:
                guestTarget.startsWith("execwin:")?
                LaunchArguments.forApp(guestTarget.substring(8),appProfile.launchArgs,nativeVulkan):
                Collections.emptyList();
        if(linkViaHelper)log.accept("DRNAS_LINK_ARGUMENT_FORWARDER selected: "+
                "Wine IShellLinkW reads .lnk target, original args and working directory; "+
                "requested nativeVulkan="+nativeVulkan+" extraArgc="+(launchArgs.size()-2));
        log.accept("DRNAS_LAUNCH_PROFILE graphics="+appProfile.graphicsApi+
                " program="+(guestTarget.startsWith("execwin:")||guestTarget.startsWith("linkwin:")?
                    guestTarget.substring(8):app)+
                " argc="+launchArgs.size()+
                (nativeVulkan?" (WineVulkan requested; device validation pending)":""));
        if("builtin:cmd".equals(launchMode)) {
            File systemCmd=new File(prefix,"drive_c/windows/system32/cmd.exe");
            log.accept("DRNAS_CMD_PREFIX_FILE="+(systemCmd.isFile()?"PRESENT":"NOT_STAGED")+
                    "; system cmd may be provided by Wine builtin modules");
        }
        // Fix only Crysis 1's windowed user config; NEVER touch its R: binaries.
        // An invalid/locked game.cfg must not prevent the whole Wine desktop.
        try {
            boolean directCrysis=launchMode.startsWith("execwin:")&&
                    launchMode.substring(8).replace('/','\\').toLowerCase(Locale.ROOT)
                            .endsWith("\\crysis.exe");
            if(SharedFolderDrive.knownCrysisPresent(context)||directCrysis)
                CrysisWindowedGuard.prepareKnownInstall(prefix,log);
        } catch(IOException error) {
            log.accept("Crysis fullscreen guard unavailable: "+error.getMessage());
        }
        // DLLs are swapped exclusively before launching Wine: switching mode
        // in the UI needs Reset Wine, never a live d3d9/dxgi replacement.
        String selectedDxvk=dxvkVariant==null?appProfile.dxvk(DxvkVariants.selected(context)):dxvkVariant;
        boolean asyncDxvk=DxvkVariants.install(context,new File(base,"prefix"),prefix,log,selectedDxvk);
        WineGraphicsTests.install(context,prefix,log);
        // Always available source-owned Paint, independent of Wine's optional mspaint.
        try { DrnasPaintRuntime.install(context,prefix,log); }
        catch(IOException problem) { log.accept("DRNAS Paint staging failed: "+problem.getMessage()); }
        // Never silently fall back to explorer.exe's .lnk opening path when
        // extra flags were requested: that path may drop them without an error.
        if(linkViaHelper && !new File(prefix,"drive_c/DRNAS/Tools/DRNASLinkLaunch.exe").isFile())
            throw new IOException("DRNAS .lnk launcher missing; cannot forward saved shortcut flags");
        // Local source-built XInput ABI shim: no Wine bus/uinput/root access.
        // If installation fails, retain the original Wine XInput built-ins.
        boolean xinputReady=false;
        try {DrnasXInputRuntime.install(context,prefix,log);xinputReady=true;}
        catch(Exception error){log.accept("DRNAS_XINPUT_SETUP_FAILED: "+error.getMessage()+"; Wine default preserved");}
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
        env.put("DXVK_ASYNC",asyncDxvk?"1":"0");
        env.put("DRNAS_DXVK_SELECTED_VARIANT",asyncDxvk?"async":"standard");
        log.accept("DRNAS_DXVK_MODE "+(asyncDxvk?"1.10.3-async":"1.10.3-standard")+
                " (DLLs validated and staged, game runtime not yet verified)");
        env.put("HOME",base.getAbsolutePath());env.put("TMPDIR",tmp.getAbsolutePath());
        env.put("XDG_RUNTIME_DIR",tmp.getAbsolutePath());env.put("LC_ALL","C");
        StringBuilder libs=new StringBuilder(nativeDir.getAbsolutePath());
        for(File dir:new File[]{lib,new File(lib,"wine/aarch64-unix"),new File(lib,"wine")})
            if(dir.isDirectory())libs.append(":").append(dir.getAbsolutePath());
        env.put("LD_LIBRARY_PATH",libs.toString());
        // A log without device enumeration is not proof of a DXVK adapter.
        env.put("DXVK_LOG_LEVEL",diagnosticsEnabled?"debug":"error"); // process-start setting
        env.put("DXVK_LOG_PATH",tmp.getAbsolutePath());
        // Guest path, not Android /data/..: DXVK's Win32 file stream reads this
        // application-scoped config. DXVK 1.10.3 ignores other executable sections.
        env.put("DXVK_CONFIG_FILE",WineGraphicsTests.cubeConfigWindowsPath());
        log.accept("DRNAS_WSI_EXPERIMENT: DXVK_CONFIG_FILE="+env.get("DXVK_CONFIG_FILE")+
                " | [TestD3D.exe] d3d9.presentInterval=1, dxgi.syncInterval=1; verify actual Present mode in DXVK log.");
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
        WineVulkanRuntime.configure(env,nativeVulkan);
        DxvkFrameCap.configure(env,frameCap);
        log.accept("DRNAS_DXVK_FRAME_CAP "+frameCap+
                (frameCap==0?" (no DXVK cap)":" FPS, D3D9 and DXGI"));
        if(xinputReady) {
            env.put("WINEDLLOVERRIDES",env.get("WINEDLLOVERRIDES")+
                    ";xinput1_3=n,b;xinput1_4=n,b;xinput9_1_0=n,b");
            log.accept("DRNAS_XINPUT_NATIVE_OVERRIDE xinput1_3/xinput1_4/xinput9_1_0 enabled; startup probe still requires game test.");
        }
        // These two native environment switches are read by Wine/DXVK when
        // their processes start; Java/X11 logging can also change mid-session.
        env.put("WINEDEBUG",diagnosticsEnabled?
                "-all,err+all,+seh,+process,+timestamp,+pid":"-all,err+all");
        env.put("DXVK_LOG_LEVEL",diagnosticsEnabled?"debug":"error");
        log.accept("DRNAS_DIAGNOSTICS="+(diagnosticsEnabled?"ON":"OFF")+
                " | Wine/DXVK verbosity changes require restarting the Wine desktop.");
        File guiRuntime=WineGuiRuntime.install(context,log);
        WineGuiRuntime.configure(guiRuntime,prefix,env);
        if(nativeVulkan){
            WineVulkanRuntime.requireBridge(lib,new File(guiRuntime,"lib"),log);
            log.accept("DRNAS_WINEVULKAN_REQUESTED: vulkan-1=b;winevulkan=b, "+
                    "WineVulkan -> Khronos -> X11 wrapper -> Turnip. "+
                    "Actual GPU and Present remain unverified.");
        }
        log.accept("X11 guest root is fixed at 1280x720; RandR mode switching unavailable. Crysis uses windowed compatibility config.");
        // H2.9 is embedded in APK but must also be *selected* for the actual
        // Wine loader process. This refreshes old H2.8 boot plans in-memory
        // after an APK upgrade, without rewriting the Wine prefix.
        boolean pinnedTurnipSelected=TurnipRuntime.selectBundledA840H29(context,plan,log);
        log.accept("DRNAS_PINNED_TURNIP_SELECTION="+(pinnedTurnipSelected?
                "STAGED_AND_SELECTED_NOT_YET_VERIFIED":"NOT_SELECTED"));
        TurnipRuntime.configureWine(context,plan,env,log);
        TurnipRuntime.probeWine(context,guiRuntime,env,log,raw);
        if(stopped)return;
        audio=new PulseAudioRuntime();
        try {audio.start(context,env,log,raw);}
        catch(Exception e) {audio.close();log.accept("Audio unavailable: "+e.getMessage()+"; desktop remains available.");}
        File linker=new File("/system/bin/linker64");
        X11Probe.check(6012);log.accept("X11 protocol 11 handshake VERIFIED; DISPLAY="+env.get("DISPLAY"));
        try {
            boolean glx=X11Probe.hasExtension(6012,"GLX");
            WineGraphicsPreflight.report(
                    new File(guiRuntime,"lib/libGL.so.1").isFile(),glx,nativeVulkan,log);
        }catch(IOException e) {
            log.accept("DRNAS_OPENGL_PREFLIGHT UNKNOWN: X11 GLX query failed: "+
                    e.getMessage()+"; desktop startup continues");
        }
        for(String relative:new String[]{"wine/aarch64-unix/winex11.so",
                "wine/aarch64-windows/winex11.drv","wine/aarch64-windows/winecfg.exe"}) {
            File f=new File(lib,relative);log.accept("GUI component "+relative+": "+(f.isFile()?f.length()+" bytes":"MISSING"));
        }
        File[] dependencies=lib.listFiles((dir,name)->name.startsWith("libX") || name.contains("xcb") || name.contains("freetype") || name.contains("fontconfig"));
        if(dependencies!=null)for(File f:dependencies)log.accept("GUI runtime library: "+f.getName());
        WineNativeDependencyAudit.report(guiRuntime,lib,env,log);
        WineGuiRuntime.probe(nativeDir,new File(lib,"wine/aarch64-unix/winex11.so"),env,log,raw);
        // v0.5.39: the sacrificial native WSI probe returned VK_SUCCESS on
        // vkQueuePresentKHR, but hung inside vkDestroySwapchainKHR for 15s.
        // Never run that suspect teardown automatically in front of Wine:
        // keep GPU enumeration above; the manual Vulkan menu action runs the
        // full present/teardown experiment in its own watchdog-bound process.
        log.accept("DRNAS_WSI_ISOLATED: startup swapchain probe SKIPPED after confirmed vkDestroySwapchainKHR stall; manual Vulkan test remains available from the menu.");
        log.accept("DRNAS_WSI_ISOLATED: Wine/DXVK will exercise its own swapchain; X11 and DXVK markers identify the first actual frame.");
        if(stopped)return;
        log.accept("[Startup] Pokretanje Wine servisa");
        server=WineServerDiagnostics.prestart(linker,serverElf,proton,nativeDir,env,log,raw);
        if(server==null)throw new IOException("GUI wineserver failed to start");
        try {
            if(stopped)return;
            WineBootProbe.configureChildExeRelay(env,nativeDir,wine);
            TurnipRuntime.configurePresentGuard(context,env);
            log.accept("DRNAS_XCB_GUARD enabled: absent Present subscriptions bypassed only for sw,noshm; device validation pending.");
            WineBootProbe.configureSecondStage(env);
            // The desktop is held until one-time prerequisite installers complete.
            // A missing MSI/EXE never masquerades as an installed dependency.
            log.accept("[Startup] Provjera Wine dodataka");
            WinePrerequisites.installPending(context,prefix,linker,wine,proton,env,raw,log);
            if(stopped)return;
            // Only locally imported, offline setup files. No .NET or websetup.
            // The [Dependencies] progress messages drive the first-install bar.
            log.accept("[Startup] Provjera offline DirectX / VC++ paketa");
            WineDependencyBundle.installPending(context,prefix,linker,wine,proton,env,raw,log);
            if(stopped)return;
            // Wine explorer.exe's /desktop=shell paints a solid background unless
            // the guest desktop wallpaper is installed and registered. A proper
            // Windows BMP is staged inside xuser/Pictures; failure never blocks GUI.
            log.accept("[Startup] Priprema Windows pozadine");
            try { WineWallpaper.ensure(context,prefix,linker,wine,proton,env,log,raw); }
            catch(Exception error) { log.accept("Wine wallpaper setup skipped: "+error.getMessage()); }
            if(stopped)return;
            FexPerformanceProfile.apply(env);
            log.accept("FEX Performance requested: TSO/vector/memcpy/halfbarrier=0; x87 reduced precision=1; multiblock=1. Effective bridge settings and speed require device verification.");
            guestTools=new GuestTools(linker,wine,proton,new HashMap<>(env),log,raw);
            log.accept("DRNAS_GUEST_LAUNCH_REQUEST="+(app.startsWith("user:")?
                    "user-shortcut":app)+" target="+launchMode);
            log.accept("Launching Wine explorer.exe virtual desktop "+
                    DESKTOP_WIDTH+"x"+DESKTOP_HEIGHT+
                    ("files".equals(app)?" with winefile.exe":
                     "config".equals(app)?" with winecfg.exe":" with taskbar and file manager")+
                    "; HWUI desktop + Mesa Vulkan X11 readback presentation (experimental).");
            Map<String,String> dxvkSeen=new HashMap<>();
            DxvkDiagnosticSnapshot.prime(tmp,dxvkSeen);
            log.accept("DRNAS_DXVK_AUDIT previous session logs ignored until modified; tracking only fresh updates.");
            ProcessBuilder pb=new ProcessBuilder(command(linker,wine,launchMode,launchArgs)).directory(proton).redirectErrorStream(true);
            pb.environment().clear();pb.environment().putAll(env);
            log.accept("[Startup] Pokretanje Explorera · čekam Windows prozor");
            if(nativeVulkan)log.accept("DRNAS_WINEVULKAN_LAUNCH_ARGUMENTS "+launchArgs+
                    " | WineVulkan files checked, actual Windows Vulkan device unverified.");
            launcher=pb.start();
            if(stopped){launcher.destroyForcibly();return;}
            Thread output=WineServerDiagnostics.pipe(launcher,"[GUI Wine] ",log,60,raw,()->diagnosticsEnabled);
            try{
                File helper=new File(prefix,"drive_c/DRNAS/Tools/DRNASDesktop.exe");
                if(helper.isFile()){
                    android.content.SharedPreferences options=AppOptionsView.desktop(context);
                    List<String> desktopArgs=command(linker,wine,"exec:"+helper.getAbsolutePath());
                    desktopArgs.add(options.getBoolean("desktop_live",true)?"/live:1":"/live:0");
                    desktopArgs.add(options.getBoolean("desktop_taskbar_large",true)?"/taskbar:44":"/taskbar:0");
                    ProcessBuilder companion=new ProcessBuilder(desktopArgs).directory(proton).redirectErrorStream(true);
                    companion.environment().clear();companion.environment().putAll(env);
                    desktopCompanion=companion.start();
                    WineServerDiagnostics.pipe(desktopCompanion,"[Desktop style] ",log,20,raw);
                    log.accept("DRNAS_DESKTOP_STYLE requested: original 1280x720 meadow + optional idle pollen; taskbar 44 guest pixels when enabled. Actual visual verification pending.");
                }
            }catch(Exception styleError){log.accept("Desktop style helper unavailable: "+styleError.getMessage());}
            long until=System.nanoTime()+TimeUnit.HOURS.toNanos(4);
            boolean exitReported=false;
            long nextDxvkAudit=0;
            while(!stopped && server.alive() && System.nanoTime()<until) {
                long now=System.nanoTime();
                if(diagnosticsEnabled && now>=nextDxvkAudit) {
                    DxvkDiagnosticSnapshot.report(tmp,dxvkSeen,log);
                    nextDxvkAudit=now+TimeUnit.SECONDS.toNanos(5);
                }
                // Explorer can delegate its window to a child and return.
                // Exit alone is never proof that a GUI exists.
                if(!launcher.isAlive()&&!exitReported) {
                    log.accept("GUI launcher exit="+launcher.exitValue()+"; inspect X11 window mapping evidence.");
                    exitReported=true;
                }
                Thread.sleep(200);
            }
            if(diagnosticsEnabled)DxvkDiagnosticSnapshot.report(tmp,new HashMap<>(),log);
            log.accept("DRNAS_DXVK_AUDIT: a created swapchain is not proof of the first successful Present.");
            log.accept(stopped?"GUI stopped by user/lifecycle.":"GUI bounded session ended.");
            if(launcher.isAlive())launcher.destroyForcibly();
            server.close();
            output.join(1500);
            launcher.getInputStream().close();
        } finally {close();}
    }
    private static final class GuestTools {
        final File linker,wine,proton;
        final Map<String,String> env;
        final Consumer<String> log;
        final RawWineTrace raw;
        GuestTools(File linker,File wine,File proton,Map<String,String> env,Consumer<String> log,RawWineTrace raw) {
            this.linker=linker;this.wine=wine;this.proton=proton;this.env=env;this.log=log;this.raw=raw;
        }
    }
    synchronized void openTaskManager() throws IOException {
        GuestTools tools=guestTools;
        if(stopped || tools==null)throw new IOException("Windows desktop još nije spreman");
        ProcessBuilder pb=new ProcessBuilder(command(tools.linker,tools.wine,"tasks"))
                .directory(tools.proton).redirectErrorStream(true);
        pb.environment().clear();pb.environment().putAll(tools.env);
        Process task=pb.start();
        WineServerDiagnostics.pipe(task,"[Task Manager] ",tools.log,30,tools.raw,()->diagnosticsEnabled);
        tools.log.accept("Task Manager requested in existing shell desktop and Wine prefix.");
    }
    void testVulkanPresentation(Context context,String mode) throws IOException {
        GuestTools tools;
        synchronized(this) {
            tools=guestTools;
            if(stopped || tools==null)throw new IOException("Windows još nije spreman");
            if(vulkanProbeThread!=null)throw new IOException("Vulkan test već traje");
            vulkanProbeThread=Thread.currentThread();
        }
        try {
            TurnipRuntime.probeWine(context,new File(context.getFilesDir(),"gui-runtime/v1"),
                    new HashMap<>(tools.env),tools.log,tools.raw,true,mode);
        } finally { synchronized(this){vulkanProbeThread=null;} }
    }
    @Override public synchronized void close() {
        stopped=true;
        guestTools=null;
        Thread probe=vulkanProbeThread;if(probe!=null)probe.interrupt();
        Thread worker=sessionThread;if(worker!=null && worker!=Thread.currentThread())worker.interrupt();
        PulseAudioRuntime a=audio;if(a!=null)a.close();
        Process p=launcher;if(p!=null&&p.isAlive())p.destroyForcibly();
        Process companion=desktopCompanion;if(companion!=null&&companion.isAlive())companion.destroyForcibly();
        WineServerDiagnostics.ManagedServer s=server;if(s!=null)s.close();
    }
}



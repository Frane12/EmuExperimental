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
    private volatile boolean stopped;
    private volatile Process launcher;
    private volatile WineServerDiagnostics.ManagedServer server;

    static List<String> command(File linker,File wine) {
        return command(linker,wine,"config");
    }
    static List<String> command(File linker,File wine,String app) {
        List<String> args=new ArrayList<>(WineLaunchProbe.linkerCommand(linker,wine));
        args.set(args.size()-1,"explorer.exe");
        args.add("/desktop=DRNAS,1024x768");
        // Wine's built-in ARM64 PE file manager does not require an x86 translator.
        args.add("files".equals(app) ? "winefile.exe" : "winecfg.exe");
        return args;
    }
    static void displayEnvironment(Map<String,String> env) {
        env.put("DISPLAY","127.0.0.1:12.0");
        env.put("WINEDLLOVERRIDES","winex11.drv=b;wineandroid.drv=d;winewayland.drv=d");
        env.put("WINEDEBUG","-all,+x11drv,+module,+seh");
    }

    void run(Context context,Consumer<String> log,RawWineTrace raw) throws Exception {
        run(context,log,raw,"config");
    }
    void run(Context context,Consumer<String> log,RawWineTrace raw,String app) throws Exception {
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
        env.put("WINEDLLPATH",new File(lib,"wine").getAbsolutePath());
        env.put("WINELOADER",wine.getAbsolutePath());
        File serverElf=new File(proton,"bin/wineserver");
        env.put("WINESERVER",serverElf.getAbsolutePath());env.put("PROTON_NO_NTSYNC","1");
        WineBootProbe.configureBootstrapMode(env);
        displayEnvironment(env);
        File guiRuntime=WineGuiRuntime.install(context,log);
        WineGuiRuntime.configure(guiRuntime,prefix,env);
        TurnipRuntime.configureWine(context,plan,env,log);
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
            log.accept("Launching Wine virtual desktop + "+
                    ("files".equals(app)?"Wine's built-in file manager (R: available)":"winecfg")+
                    "; Canvas X11 renderer, 5 minute session. Turnip/Vulkan acceleration NOT yet verified.");
            ProcessBuilder pb=new ProcessBuilder(command(linker,wine,app)).directory(proton).redirectErrorStream(true);
            pb.environment().clear();pb.environment().putAll(env);
            launcher=pb.start();
            if(stopped){launcher.destroyForcibly();return;}
            Thread output=WineServerDiagnostics.pipe(launcher,"[GUI Wine] ",log,60,raw);
            long until=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
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
    @Override public void close() {
        stopped=true;
        Process p=launcher;if(p!=null&&p.isAlive())p.destroyForcibly();
        WineServerDiagnostics.ManagedServer s=server;if(s!=null)s.close();
    }
}


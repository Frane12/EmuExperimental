package com.drnas.console;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Explicitly requested wineboot --init test, isolated from the regular
 * installed component vault and existing Wine prefix. No graphics or games.
 * Run only after a successful real Wine --version on the same component.
 */
public final class WineBootProbe {
    private static final int TIMEOUT_SECONDS=50;
    private static final int MAX_LOG_LINES=120;
    private static final int SERVER_PROBE_TIMEOUT_SECONDS=7;
    private static final String[] REGISTRY={"system.reg","user.reg","userdef.reg"};

    public static final class Result {
        public final String code;
        public final int exitCode;
        public final int changedRegistries;
        Result(String code,int exit,int changed) {
            this.code=code;exitCode=exit;changedRegistries=changed;
        }
    }

    static List<String> command(File linker,File wine) {
        List<String> result=new ArrayList<>(WineLaunchProbe.linkerCommand(linker,wine));
        result.set(result.size()-1,"wineboot.exe");
        result.add("--init");
        return result;
    }

    /**
     * Wine's ntdll/unix/loader.c -> __wine_main() checks
     * WINELOADERNOEXEC before check_command_line(). Wine's ordinary
     * command-line path tries a second execv() of its ELF loader,
     * which cannot run from Android 16 writable app storage.
     *
     * Our ProcessBuilder has ALREADY launched this exact Bionic ELF through
     * Android's linker64, so tell Wine it is at its second-stage entrypoint.
     * Wine unsets this variable itself before starting the main thread.
     * Never add this env var to the independent wine --version preflight.
     */
    static void configureSecondStage(Map<String,String> env) {
        env.put("WINELOADERNOEXEC","1");
    }

    private static String registryFingerprint(File folder) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(String file:REGISTRY) {
            File reg=new File(folder,file);
            digest.update(file.getBytes(StandardCharsets.US_ASCII));
            if(reg.isFile() && !Files.isSymbolicLink(reg.toPath()) && reg.length()>0) {
                try(FileInputStream in=new FileInputStream(reg)) {
                    byte[] data=new byte[65536];int n;
                    while((n=in.read(data))!=-1)if(n>0)digest.update(data,0,n);
                }
            } else digest.update((byte)0);
        }
        StringBuilder hex=new StringBuilder();
        for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }

    private static boolean validRegistry(File file) throws Exception {
        if(!file.isFile() || Files.isSymbolicLink(file.toPath()) ||
                file.length()<16 || file.length()>512L*1024*1024) return false;
        byte[] buf=new byte[64];
        try(FileInputStream in=new FileInputStream(file)){
            int n=in.read(buf);
            if(n<16)return false;
        }
        String h=new String(buf,StandardCharsets.US_ASCII);
        return h.startsWith("WINE REGISTRY Version");
    }

    static void removeWithoutFollowingLinks(File file) throws IOException {
        if(!Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS))return;
        if(Files.isSymbolicLink(file.toPath())) {
            Files.delete(file.toPath());return;
        }
        if(Files.isDirectory(file.toPath(),LinkOption.NOFOLLOW_LINKS)) {
            File[] children=file.listFiles();
            if(children==null)throw new IOException("Cannot list boot-probe sandbox");
            for(File child:children)removeWithoutFollowingLinks(child);
        }
        Files.delete(file.toPath());
    }

    private static String fileKind(File f) throws IOException {
        if(!f.isFile()) return "absent";
        long n=f.length();
        if(n==0) return "empty";
        byte[] magic=new byte[4];
        try(FileInputStream in=new FileInputStream(f)) {
            if(in.read(magic,0,4)!=4) return "truncated:"+n;
        }
        if(magic[0]=='M' && magic[1]=='Z') return "PE/MZ "+n+"B";
        if((magic[0]&255)==0x7f && magic[1]=='E' &&
           magic[2]=='L' && magic[3]=='F')return "ELF "+n+"B";
        return "unknown magic "+n+"B";
    }

    /**
     * Before wineboot, locate known builtin PE modules in our staged Proton
     * and freshly extracted prefix. These are diagnostics only: the actual
     * GameNative Wine implementation chooses its own DLL search paths.
     */
    static boolean inspectWinebootArtifacts(File wineDir,File prefix,
                                              Consumer<String> report) throws IOException {
        String[] relative={
                "aarch64-windows/wineboot.exe",
                "arm64ec-windows/wineboot.exe",
                "x86_64-windows/wineboot.exe",
                "aarch64-windows/ntdll.dll",
                "aarch64-unix/ntdll.so"
        };
        boolean winebootFound=false;
        for(String path:relative) {
            File file=new File(wineDir,path);
            String kind=fileKind(file);
            report.accept("Component "+path+": "+kind);
            if(path.endsWith("/wineboot.exe") && kind.startsWith("PE/MZ"))
                winebootFound=true;
        }
        String[] prefixEntries={"drive_c/windows/system32/wineboot.exe",
                "drive_c/windows/system32/ntdll.dll",
                "drive_c/windows/syswow64/wineboot.exe"};
        for(String path:prefixEntries) {
            File file=new File(prefix,path);
            String kind=fileKind(file);
            report.accept("Prefix "+path+": "+kind);
            if(path.endsWith("/wineboot.exe") && kind.startsWith("PE/MZ"))
                winebootFound=true;
        }
        return winebootFound;
    }

    static String probeWineserver(File linker,File server,Map<String,String> env,
                                  File workingDir,Consumer<String> report) throws Exception {
        if(!server.isFile() || !WineLaunchProbe.inspect(server).runnable)
            return "SERVER_ELF_MISSING";
        List<String> args=new ArrayList<>();
        args.add(linker.getAbsolutePath());
        args.add(server.getAbsolutePath());
        args.add("--version");
        ProcessBuilder pb=new ProcessBuilder(args);
        pb.directory(workingDir);
        pb.redirectErrorStream(true);
        // Environment is copied. The probe NEVER starts persistent wineserver.
        Map<String,String> child=pb.environment();
        child.clear();
        child.putAll(env);
        child.remove("WINELOADERNOEXEC");
        report.accept("wineserver smoke test: linker64 <verified server ELF> --version");
        final Process process;
        try {process=pb.start();}
        catch(IOException e) {
            report.accept("wineserver start rejected: "+e.getMessage());
            return "SERVER_EXEC_REJECTED";
        }
        final StringBuilder captured=new StringBuilder();
        Thread out=new Thread(()->{
            try(BufferedReader lines=new BufferedReader(new InputStreamReader(
                    process.getInputStream(),StandardCharsets.UTF_8))) {
                String line;int n=0;
                while((line=lines.readLine())!=null){
                    if(n++<8) {
                        String shortLine=line.substring(0,Math.min(line.length(),320));
                        synchronized(captured){captured.append(shortLine).append("\\n");}
                        report.accept("[server stdout] "+shortLine);
                    }
                }
            } catch(IOException e){report.accept("server output stream: "+e.getMessage());}
        },"drnas-server-version");
        out.setDaemon(true);out.start();
        boolean ended=process.waitFor(SERVER_PROBE_TIMEOUT_SECONDS,TimeUnit.SECONDS);
        if(!ended) {
            process.destroyForcibly();process.waitFor(3,TimeUnit.SECONDS);
        }
        out.join(1500);
        if(!ended)return "SERVER_VERSION_TIMEOUT";
        int exit=process.exitValue();
        report.accept("wineserver --version exit="+exit);
        return exit==0?"SERVER_VERSION_EXIT_0":"SERVER_VERSION_EXIT_"+exit;
    }

    private static File winePrefix(File root) throws IOException {
        File nested=new File(root,".wine");
        File selected=new File(nested,"drive_c").isDirectory()?nested:root;
        if(!new File(selected,"drive_c/windows").isDirectory())
            throw new IOException("Boot template has no Windows drive");
        return selected;
    }

    private static void copyBridge(File reference,File destination) throws Exception {
        if(!reference.isFile() || Files.isSymbolicLink(reference.toPath()) ||
                reference.length()==0 || reference.length()>24*1024*1024)
            throw new IOException("FEX bridge file invalid: "+reference.getName());
        if(!destination.getParentFile().isDirectory() &&
                !destination.getParentFile().mkdirs())throw new IOException("Cannot make FEX target");
        // Never replace any symlink in a third-party prefix template.
        if(Files.isSymbolicLink(destination.toPath()))
            throw new IOException("FEX target is a symlink: "+destination.getName());
        Files.copy(reference.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Only an approved GameNative ARM64 Bionic Wine entrypoint is used. Wine
     * runs through Android's system linker64. Child wineserver spawning may
     * still fail under Android exec restrictions: log, don't conceal.
     */
    public static Result run(File protonFolder,JSONObject wineConfig,
                             File template,File originalWindows,File containerRoot,
                             File nativeLibraryDir,Consumer<String> report) throws Exception {
        File wine=WineLaunchProbe.selectWine(protonFolder,
                s -> report.accept("[boot/elf] "+s));
        if(wine==null || !WineLaunchProbe.inspect(wine).runnable)
            return new Result("NO_BIONIC_WINE_LOADER",-1,0);
        File linker=new File("/system/bin/linker64");
        if(!linker.isFile())return new Result("ANDROID_LINKER_MISSING",-1,0);

        File probeRoot=new File(containerRoot,"wineboot-probe");
        if(!probeRoot.getCanonicalPath().startsWith(
                containerRoot.getCanonicalPath()+File.separator))
            throw new IOException("Invalid wineboot sandbox");
        if(Files.isSymbolicLink(probeRoot.toPath()))
            throw new IOException("Refuse symlinked wineboot sandbox");
        // This is a dedicated test-only sandbox; never delete the live prefix.
        if(probeRoot.exists()) {
            report.accept("Resetting previous disposable wineboot test sandbox");
            removeWithoutFollowingLinks(probeRoot);
        }
        if(!probeRoot.mkdirs())throw new IOException("Cannot make wineboot probe root");
        File templateRoot=new File(probeRoot,"template");
        report.accept("Unpacking disposable Wine prefix template. Existing prefix untouched.");
        WcpRuntimeStager.unpackPrefixPack(template,templateRoot,
                msg->report.accept("[boot/prefix] "+msg));
        File isolated=winePrefix(templateRoot);
        File sys32=new File(isolated,"drive_c/windows/system32");
        for(String dll:new String[]{"libarm64ecfex.dll","libwow64fex.dll"}) {
            File from=new File(originalWindows,"system32/"+dll);
            copyBridge(from,new File(sys32,dll));
        }
        report.accept("Applied FEX bridge DLL pair inside disposable wineboot prefix");
        String before=registryFingerprint(isolated);
        report.accept("Baseline registries: "+registrySummary(isolated));

        File libdir=new File(protonFolder,
                WcpRuntimeStager.clean(wineConfig.getString("libPath"),false));
        if(!libdir.isDirectory())throw new IOException("Missing Proton libs");
        StringBuilder libs=new StringBuilder(libdir.getAbsolutePath());
        for(String item:new String[]{"wine/aarch64-unix","wine","aarch64-unix"}) {
            File p=new File(libdir,item);
            if(p.isDirectory())libs.append(":").append(p.getAbsolutePath());
        }
        File tmp=new File(probeRoot,"tmp");
        if(!tmp.mkdirs())throw new IOException("No tmp");

        List<String> args=command(linker,wine);
        report.accept("wineboot command: linker64 <verified Wine AArch64 ELF> wineboot.exe --init");
        report.accept("Watchdog "+TIMEOUT_SECONDS+"s; Wine guest execution NOT yet established.");
        ProcessBuilder pb=new ProcessBuilder(args);
        pb.directory(protonFolder);
        pb.redirectErrorStream(true);
        Map<String,String> env=pb.environment();
        env.put("WINEPREFIX",isolated.getAbsolutePath());
        env.put("WINEARCH","win64");
        env.put("HOME",probeRoot.getAbsolutePath());
        env.put("TMPDIR",tmp.getAbsolutePath());
        env.put("XDG_RUNTIME_DIR",tmp.getAbsolutePath());
        env.put("LD_LIBRARY_PATH",libs.toString());
        // Wine's get_pe_dir() appends /aarch64-windows to each WINEDLLPATH
        // root, so passing aarch64-unix here incorrectly doubles the suffix.
        // GameNative ntdll/unix/loader.c -> set_dll_path().
        env.put("WINEDLLPATH",new File(libdir,"wine").getAbsolutePath());
        env.put("WINELOADER",wine.getAbsolutePath());
        File wineServer=new File(protonFolder,"bin/wineserver");
        if(wineServer.isFile() && WineLaunchProbe.inspect(wineServer).runnable)
            env.put("WINESERVER",wineServer.getAbsolutePath());
        env.put("LC_ALL","C");
        env.put("WINEDEBUG","+process,+server"); // bounded output shows wineserver/PE initialization failures
        env.put("PROTON_NO_NTSYNC","1");
        File wineLib=new File(libdir,"wine");
        boolean pePresent=inspectWinebootArtifacts(wineLib,isolated,report);
        report.accept("At least one Wineboot PE candidate="+pePresent+
                "; missing candidates are not automatically proof of failure");
        String serverStatus=probeWineserver(linker,wineServer,env,protonFolder,report);
        report.accept("Wineserver preflight status="+serverStatus);
        // Two different operations: a direct exec() sanity check and a real
        // foreground server started through Android's Bionic linker.
        // A --version smoke test cannot establish whether the Wine client
        // can connect to its unix-domain wineserver socket.
        String directStatus=WineServerDiagnostics.probeDirectExec(
                wineServer,protonFolder,env,report);
        report.accept("Direct wineserver exec A/B="+directStatus);
        WineServerDiagnostics.ManagedServer prestarted=WineServerDiagnostics.prestart(
                linker,wineServer,protonFolder,nativeLibraryDir,env,report);
        if(prestarted==null) {
            report.accept("Foreground server could not remain alive; skipping Wineboot.");
            return new Result("WINESERVER_PRESTART_FAILED",-1,0);
        }
        try {
        // Isolate the Wine second-stage bypass to this controlled experiment.
        configureSecondStage(env);
        report.accept("WINELOADERNOEXEC=1 (second-stage handoff experiment)");
        report.accept("WINEPREFIX="+isolated.getAbsolutePath());
        report.accept("WINELOADER/LD_LIBRARY_PATH set; X11, audio, Turnip ICD not connected.");
        Process child;
        try {child=pb.start();}
        catch(IOException ex) {
            report.accept("Wineboot ProcessBuilder rejected: "+ex.getMessage());
            return new Result("WINEBOOT_LINKER_DENIED",-1,0);
        }
        Thread output=new Thread(()->{
            int count=0;
            try(BufferedReader lines=new BufferedReader(new InputStreamReader(
                    child.getInputStream(),StandardCharsets.UTF_8))) {
                String line;
                while((line=lines.readLine())!=null) {
                    if(++count<=MAX_LOG_LINES)
                        report.accept("[wineboot] "+line.substring(0,Math.min(line.length(),480)));
                }
                if(count>MAX_LOG_LINES)
                    report.accept("[wineboot] Suppressed "+(count-MAX_LOG_LINES)+" additional lines");
            }catch(IOException ex) {
                report.accept("[wineboot] output stream closed: "+ex.getClass().getSimpleName());
            }
        },"drnas-wineboot-output");
        output.setDaemon(true);output.start();
        boolean ended;
        try {ended=child.waitFor(TIMEOUT_SECONDS,TimeUnit.SECONDS);}
        catch(InterruptedException interrupted) {
            child.destroyForcibly();Thread.currentThread().interrupt();
            throw interrupted;
        }
        if(!ended) {
            child.destroyForcibly();
            child.waitFor(3,TimeUnit.SECONDS);
            report.accept("Wineboot exceeded watchdog; terminated its launcher.");
            report.accept("A wineserver child may remain; do not reuse this test prefix.");
        }
        output.join(1500);
        String after=registryFingerprint(isolated);
        boolean changed=!before.equals(after);
        int count=0;
        for(String registry:REGISTRY)
            if(validRegistry(new File(isolated,registry)))count++;
        report.accept("After test registries: "+registrySummary(isolated));
        report.accept("Registry content changed="+changed+", valid registry count="+count);
        if(!ended)return new Result("WINEBOOT_TIMEOUT",-1,changed?1:0);
        int exit=child.exitValue();
        report.accept("Wineboot launcher exit="+exit);
        if(exit!=0) {
            if(exit==127) {
                report.accept("EXIT 127 despite prestarted wineserver (if server remained alive).");
                report.accept("This narrows but does NOT prove the failing Wine startup stage.");
                return new Result("WINEBOOT_EARLY_EXIT_127",exit,changed?1:0);
            }
            return new Result("WINEBOOT_EXIT_"+exit,exit,changed?1:0);
        }
        if(count<2)return new Result("WINEBOOT_EXIT_0_REGISTRY_INCOMPLETE",0,changed?1:0);
        if(!changed)return new Result("WINEBOOT_EXIT_0_REGISTRY_UNCHANGED",0,0);
        return new Result("WINEBOOT_PREFIX_INITIALIZED",0,count);
        } finally {
            prestarted.close();
        }
    }

    private static String registrySummary(File prefix) {
        StringBuilder out=new StringBuilder();
        for(String key:REGISTRY) {
            File f=new File(prefix,key);
            if(out.length()>0)out.append(", ");
            out.append(key).append("=").append(f.isFile()?f.length()+"B":"absent");
        }
        return out.toString();
    }
    private WineBootProbe(){}
}

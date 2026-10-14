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
    private static final int MAX_LOG_LINES=85;
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
                             Consumer<String> report) throws Exception {
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
        env.put("WINEDLLPATH",new File(libdir,"wine/aarch64-unix").getAbsolutePath());
        env.put("WINELOADER",wine.getAbsolutePath());
        File wineServer=new File(protonFolder,"bin/wineserver");
        if(wineServer.isFile() && WineLaunchProbe.inspect(wineServer).runnable)
            env.put("WINESERVER",wineServer.getAbsolutePath());
        env.put("LC_ALL","C");
        env.put("WINEDEBUG","-all");
        env.put("PROTON_NO_NTSYNC","1");
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
        if(exit!=0)
            return new Result("WINEBOOT_EXIT_"+exit,exit,changed?1:0);
        if(count<2)return new Result("WINEBOOT_EXIT_0_REGISTRY_INCOMPLETE",0,changed?1:0);
        if(!changed)return new Result("WINEBOOT_EXIT_0_REGISTRY_UNCHANGED",0,0);
        return new Result("WINEBOOT_PREFIX_INITIALIZED",0,count);
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

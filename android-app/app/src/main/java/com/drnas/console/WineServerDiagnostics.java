package com.drnas.console;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A/B diagnostic only. Wineserver --version succeeding through linker64 does
 * not prove that Wine can start its own wineserver. The server in this helper
 * is owned by the app and is always shut down after the isolated test.
 */
public final class WineServerDiagnostics {
    private static final int LOG_LIMIT=36;
    private static final int DIRECT_TIMEOUT_SECONDS=4;
    private static final int STARTUP_OBSERVE_MS=1500;

    static List<String> nativeCommand(File linker, File wineserver) {
        if(!linker.isAbsolute() || !wineserver.isAbsolute())
            throw new IllegalArgumentException("Only absolute server paths allowed");
        List<String> command=new ArrayList<>();
        command.add(linker.getAbsolutePath());
        command.add(wineserver.getAbsolutePath());
        command.add("--foreground");
        command.add("--persistent=60");
        return command;
    }

    /**
     * Direct exec is expected to be blocked by Android policy on files in
     * writable app-private storage. No privilege elevation or bypass.
     */
    public static String probeDirectExec(File wineServer,File cwd,
                                          Map<String,String> env,
                                          Consumer<String> log) throws Exception {
        ProcessBuilder pb=new ProcessBuilder(wineServer.getAbsolutePath(),"--version")
                .directory(cwd).redirectErrorStream(true);
        pb.environment().putAll(env);
        log.accept("[server A] direct ELF exec test: wineserver --version (4s)");
        Process p;
        try {p=pb.start();}
        catch(IOException error) {
            log.accept("[server A] direct exec rejected: "+error.getMessage());
            return "DIRECT_EXEC_REJECTED";
        }
        Thread output=pipe(p,"[server A] ",log,8);
        boolean ended=p.waitFor(DIRECT_TIMEOUT_SECONDS,TimeUnit.SECONDS);
        if(!ended) {p.destroyForcibly();p.waitFor(2,TimeUnit.SECONDS);}
        output.join(500);
        if(!ended)return "DIRECT_EXEC_TIMEOUT";
        log.accept("[server A] exit="+p.exitValue());
        return "DIRECT_EXEC_EXIT_"+p.exitValue();
    }

    static Thread pipe(Process process,String prefix,Consumer<String> log,int limit) {
        return pipe(process,prefix,log,limit,null);
    }
    static Thread pipe(Process process,String prefix,Consumer<String> log,int limit,
                       RawWineTrace raw) {
        Thread reader=new Thread(()->{
            try(BufferedReader br=new BufferedReader(new InputStreamReader(
                    process.getInputStream(),StandardCharsets.UTF_8))) {
                String line;int n=0;
                while((line=br.readLine())!=null) {
                    if(raw!=null)raw.write(prefix.startsWith("[server B]")?
                            "wineserver":"server",line);
                    if(n++<limit)
                        log.accept(prefix+line.substring(0,Math.min(line.length(),350)));
                    else if(n==limit+1) log.accept(prefix+"output limit reached");
                }
            } catch(IOException ex) {
                log.accept(prefix+"pipe closed: "+ex.getClass().getSimpleName());
            }
        },"drnas-wineserver-pipe");
        reader.setDaemon(true);reader.start();
        return reader;
    }

    public static final class ManagedServer implements AutoCloseable {
        final Process process;
        final Thread output;
        final Consumer<String> log;
        ManagedServer(Process process,Thread output,Consumer<String> log) {
            this.process=process;this.output=output;this.log=log;
        }
        public boolean alive(){return process.isAlive();}
        @Override public void close() {
            process.destroy();
            try {
                if(!process.waitFor(2,TimeUnit.SECONDS)) {
                    process.destroyForcibly();process.waitFor(2,TimeUnit.SECONDS);
                }
                output.join(1000);
            } catch(InterruptedException ex) {
                process.destroyForcibly();Thread.currentThread().interrupt();
            }
            log.accept("[server B] App-owned background wineserver stopped.");
        }
    }

    /**
     * Runs a real Bionic wineserver in the foreground via system linker64.
     * `--persistent=60` is a bounded session; we also explicitly terminate
     * the child in finally. Alive != socket-ready; Wineboot remains the test.
     */
    public static ManagedServer prestart(File linker,File wineserver,File cwd,
                                          File nativeLibDir,Map<String,String> env,
                                          Consumer<String> log) throws Exception {
        return prestart(linker,wineserver,cwd,nativeLibDir,env,log,null);
    }

    public static ManagedServer prestart(File linker,File wineserver,File cwd,
                                          File nativeLibDir,Map<String,String> env,
                                          Consumer<String> log,RawWineTrace raw) throws Exception {
        // GameNative server/unicode.c derives its NLS directory from
        // realpath("/proc/self/exe"). Under the linker64 trick that points
        // to Android's linker, not the staged Wine binary.
        File nls=new File(cwd,"share/wine/nls/l_intl.nls");
        if(!nls.isFile() || nls.length()<1024 || nls.length()>32L*1024*1024) {
            log.accept("[server B] Proton's share/wine/nls/l_intl.nls is absent/invalid; cannot start safely.");
            return null;
        }
        File shim=new File(nativeLibDir,"libdrnas_wine_nls.so");
        if(!shim.isFile()) {
            log.accept("[server B] ARM64 NLS path shim is absent from APK native library directory.");
            return null;
        }
        File verifiedServer=wineserver.getCanonicalFile();
        if(!verifiedServer.isFile() || !WineLaunchProbe.inspect(verifiedServer).runnable)
            throw new IOException("Wine server path not verified as Bionic ARM64");
        log.accept("[server B] l_intl.nls exists ("+nls.length()+
                " bytes); testing linker64 /proc/self/exe path compatibility.");
        log.accept("[server B] shim only changes wineserver realpath(/proc/self/exe), not Android system.");
        List<String> args=nativeCommand(linker,verifiedServer);
        log.accept("[server B] prestart: linker64 <wineserver> --foreground --persistent=60");
        ProcessBuilder pb=new ProcessBuilder(args);
        pb.directory(cwd).redirectErrorStream(true);
        Map<String,String> child=pb.environment();
        child.putAll(env);
        child.remove("WINELOADERNOEXEC");
        // Do not inherit this into Wine CLI, shell or Android app.
        child.put("LD_PRELOAD",shim.getCanonicalPath());
        child.put("DRNAS_WINESERVER_BIN",verifiedServer.getCanonicalPath());
        Process p;
        try {p=pb.start();}
        catch(IOException e) {
            log.accept("[server B] linker launch rejected: "+e.getMessage());
            return null;
        }
        Thread pipe=pipe(p,"[server B] ",log,LOG_LIMIT,raw);
        long until=System.nanoTime()+STARTUP_OBSERVE_MS*1000000L;
        while(p.isAlive() && System.nanoTime()<until)Thread.sleep(100);
        if(!p.isAlive()) {
            int code=p.exitValue(); pipe.join(500);
            log.accept("[server B] wineserver stopped during startup, exit="+code);
            return null;
        }
        log.accept("[server B] wineserver process alive after startup window; socket connection not yet proven.");
        return new ManagedServer(p,pipe,log);
    }

    private WineServerDiagnostics(){}
}

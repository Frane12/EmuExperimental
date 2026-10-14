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
        Thread reader=new Thread(()->{
            try(BufferedReader br=new BufferedReader(new InputStreamReader(
                    process.getInputStream(),StandardCharsets.UTF_8))) {
                String line;int n=0;
                while((line=br.readLine())!=null) {
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
                                          Map<String,String> env,
                                          Consumer<String> log) throws Exception {
        List<String> args=nativeCommand(linker,wineserver);
        log.accept("[server B] prestart: linker64 <wineserver> --foreground --persistent=60");
        ProcessBuilder pb=new ProcessBuilder(args);
        pb.directory(cwd).redirectErrorStream(true);
        Map<String,String> child=pb.environment();
        child.putAll(env);
        child.remove("WINELOADERNOEXEC");
        Process p;
        try {p=pb.start();}
        catch(IOException e) {
            log.accept("[server B] linker launch rejected: "+e.getMessage());
            return null;
        }
        Thread pipe=pipe(p,"[server B] ",log,LOG_LIMIT);
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

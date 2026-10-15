package com.drnas.console;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.*;
import org.json.JSONObject;

/** Session-owned PulseAudio with Android AAudio output. No external app needed. */
final class PulseAudioRuntime implements AutoCloseable {
    private volatile Process process;
    private volatile boolean stopped;
    private File root;

    void start(Context context,Map<String,String> wineEnv,Consumer<String> log,RawWineTrace raw) throws Exception {
        root=new File(context.getFilesDir(),"audio-runtime/v1");
        Files.createDirectories(new File(root,"modules").toPath());
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipInputStream zip=new ZipInputStream(context.getAssets().open("audio-runtime-v1.zip"))) {
            ZipEntry e;long total=0;byte[] buffer=new byte[16384];
            while((e=zip.getNextEntry())!=null) {
                if(e.isDirectory() || (!e.getName().equals("manifest.json") &&
                        !e.getName().matches("modules/(?:module-|lib)[A-Za-z0-9_.-]+\\.so")) || entries.size()>64)
                    throw new IOException("Invalid audio bundle entry");
                ByteArrayOutputStream out=new ByteArrayOutputStream();int n;
                while((n=zip.read(buffer))!=-1) {
                    total+=n;if(total>16L*1024*1024)throw new IOException("Audio bundle too large");
                    out.write(buffer,0,n);
                }
                if(entries.put(e.getName(),out.toByteArray())!=null)throw new IOException("Duplicate audio entry");
            }
        }
        byte[] manifest=entries.remove("manifest.json");
        if(manifest==null)throw new IOException("Audio manifest missing");
        JSONObject hashes=new JSONObject(new String(manifest,java.nio.charset.StandardCharsets.UTF_8)).getJSONObject("files");
        if(hashes.length()!=entries.size())throw new IOException("Audio manifest mismatch");
        for(Map.Entry<String,byte[]> e:entries.entrySet()) {
            String name=new File(e.getKey()).getName();
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(e.getValue());
            StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(Locale.ROOT,"%02x",b&255));
            if(!hex.toString().equals(hashes.getString(name)))throw new IOException("Audio checksum failed");
            Path dest=new File(root,e.getKey()).toPath();
            if(Files.isSymbolicLink(dest))throw new IOException("Audio module must be regular file");
            if(!Files.exists(dest)||!Arrays.equals(Files.readAllBytes(dest),e.getValue())) {
                Path tmp=new File(root,e.getKey()+".tmp").toPath();
                Files.write(tmp,e.getValue());Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING);
            }
        }
        File socket=new File(root,"native.sock");Files.deleteIfExists(socket.toPath());
        String config="load-module module-native-protocol-unix auth-anonymous=1 auth-cookie-enabled=false socket=\""+socket+"\"\n"+
                "load-module module-aaudio-sink volume=1.0 performance_mode=1\n"+
                "set-default-sink AAudioSink\n";
        Files.write(new File(root,"default.pa").toPath(),config.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        File nativeDir=new File(context.getApplicationInfo().nativeLibraryDir);
        ProcessBuilder pb=new ProcessBuilder(new File(nativeDir,"libpulseaudio.so").getAbsolutePath(),
                "--system=false","--disable-shm=true","--fail=true","-n","--file=default.pa",
                "--daemonize=false","--use-pid-file=false","--exit-idle-time=-1",
                "--dl-search-path="+new File(root,"modules").getAbsolutePath()).directory(root).redirectErrorStream(true);
        pb.environment().put("LD_LIBRARY_PATH","/system/lib64:"+nativeDir+":"+new File(root,"modules"));
        pb.environment().put("HOME",root.getAbsolutePath());pb.environment().put("TMPDIR",root.getAbsolutePath());
        pb.environment().put("PULSE_RUNTIME_PATH",root.getAbsolutePath());
        pb.environment().remove("LD_PRELOAD");
        if(stopped)return;
        process=pb.start();
        WineServerDiagnostics.pipe(process,"[PulseAudio] ",log,20,raw);
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!stopped && process.isAlive() && System.nanoTime()<until) {
            try(LocalSocket probe=new LocalSocket()) {
                probe.connect(new LocalSocketAddress(socket.getAbsolutePath(),LocalSocketAddress.Namespace.FILESYSTEM));
                wineEnv.put("PULSE_SERVER","unix:"+socket.getAbsolutePath());
                wineEnv.put("PULSE_LATENCY_MSEC","40");
                log.accept("PulseAudio native socket ready; AAudio sink loaded (speaker playback needs device test).");
                return;
            } catch(IOException notReady){Thread.sleep(50);}
        }
        close();throw new IOException("PulseAudio did not become ready");
    }

    @Override public synchronized void close() {
        stopped=true;Process p=process;
        if(p!=null && p.isAlive()) {
            p.destroy();
            try {if(!p.waitFor(1,TimeUnit.SECONDS))p.destroyForcibly();}
            catch(InterruptedException e){p.destroyForcibly();Thread.currentThread().interrupt();}
        }
    }
}


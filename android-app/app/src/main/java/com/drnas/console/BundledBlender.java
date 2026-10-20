package com.drnas.console;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.function.Consumer;

/** App-owned Blender payload: refresh integration scripts without touching projects or settings. */
final class BundledBlender {
    static synchronized void install(Context context,File prefix,Consumer<String> log) throws Exception {
        Path target=prefix.toPath().resolve("drive_c/DRNAS/Apps/"+BundledBlenderArchive.DIRECTORY);
        if(BundledBlenderArchive.ready(target)) {
            // Existing installs must pick up new startup logic, but identical files
            // should not be rewritten on every desktop launch.
            if(writeIfChanged(target.resolve("drnas-startup.py"),
                    BundledBlenderArchive.SETUP.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                log.accept("DRNAS_BLENDER_STARTUP_REFRESHED: app-owned integration script updated");
            installWindowLayout(context,target,log);
            return;
        }
        File available=target.toFile().getParentFile();
        while(available!=null&&!available.exists())available=available.getParentFile();
        if(available!=null&&available.getUsableSpace()<BundledBlenderArchive.REQUIRED_FREE_BYTES)
            throw new IOException("Blender requires at least "+
                    ((BundledBlenderArchive.REQUIRED_FREE_BYTES+1024*1024-1)/(1024*1024))+
                    " MiB free internal storage");
        try(InputStream in=context.getAssets().open("blender/"+BundledBlenderArchive.NAME)) {
            BundledBlenderArchive.extract(in,target,BundledBlenderArchive.SHA256,log);
        }
        installWindowLayout(context,target,log);
    }
    private static void installWindowLayout(Context context,Path target,Consumer<String> log) throws IOException {
        Path script=target.resolve("drnas-window-layout.py");
        try(InputStream in=context.getAssets().open("blender/drnas-window-layout.py")) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] buffer=new byte[8192];
            int n;
            while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
            if(writeIfChanged(script,bytes.toByteArray()))
                log.accept("DRNAS_BLENDER_PRIMARY_POLICY_REFRESHED: primary-only controller staged");
        }
    }
    /**
     * Atomic and idempotent integration-file refresh. Never edit user settings,
     * project data or the Blender executable; only our two app-owned scripts.
     */
    static boolean writeIfChanged(Path file,byte[] desired) throws IOException {
        if(Files.isSymbolicLink(file))throw new IOException("Unsafe Blender integration script");
        if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) &&
                Files.size(file)==desired.length &&
                Arrays.equals(Files.readAllBytes(file),desired))return false;
        Path temporary=Files.createTempFile(file.getParent(),".drnas-integration-",".tmp");
        try {
            Files.write(temporary,desired);
            try {
                Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            }catch(AtomicMoveNotSupportedException exception) {
                Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
            }
        }finally {
            Files.deleteIfExists(temporary);
        }
        return true;
    }
    private BundledBlender(){}
}

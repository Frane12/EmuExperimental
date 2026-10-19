package com.drnas.console;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.function.Consumer;

final class BundledBlender {
    static synchronized void install(Context context,File prefix,Consumer<String> log) throws Exception {
        Path target=prefix.toPath().resolve("drive_c/DRNAS/Apps/"+BundledBlenderArchive.DIRECTORY);
        if(BundledBlenderArchive.ready(target)) {
            // Refresh our integration script on upgrades without re-extracting Blender
            // or changing any saved project, startup.blend or user preferences.
            Path script=target.resolve("drnas-startup.py");
            if(Files.isSymbolicLink(script))throw new IOException("Unsafe Blender setup script");
            Files.write(script,BundledBlenderArchive.SETUP.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            installWindowLayout(context,target);
            return;
        }
        File available=target.toFile().getParentFile();
        while(available!=null&&!available.exists())available=available.getParentFile();
        if(available!=null&&available.getUsableSpace()<900L*1024*1024)
            throw new IOException("Blender requires at least 900 MiB free internal storage");
        try(InputStream in=context.getAssets().open("blender/"+BundledBlenderArchive.NAME)) {
            BundledBlenderArchive.extract(in,target,BundledBlenderArchive.SHA256,log);
        }
        installWindowLayout(context,target);
    }
    private static void installWindowLayout(Context context,Path target) throws IOException {
        Path script=target.resolve("drnas-window-layout.py");
        if(Files.isSymbolicLink(script))throw new IOException("Unsafe Blender layout script");
        try(InputStream in=context.getAssets().open("blender/drnas-window-layout.py")) {
            Files.copy(in,script,StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private BundledBlender(){}
}




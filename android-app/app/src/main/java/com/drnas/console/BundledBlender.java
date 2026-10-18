package com.drnas.console;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.function.Consumer;

final class BundledBlender {
    static synchronized void install(Context context,File prefix,Consumer<String> log) throws Exception {
        Path target=prefix.toPath().resolve("drive_c/DRNAS/Apps/"+BundledBlenderArchive.DIRECTORY);
        if(BundledBlenderArchive.ready(target))return;
        File available=target.toFile().getParentFile();
        while(available!=null&&!available.exists())available=available.getParentFile();
        if(available!=null&&available.getUsableSpace()<900L*1024*1024)
            throw new IOException("Blender requires at least 900 MiB free internal storage");
        try(InputStream in=context.getAssets().open("blender/"+BundledBlenderArchive.NAME)) {
            BundledBlenderArchive.extract(in,target,BundledBlenderArchive.SHA256,log);
        }
    }
    private BundledBlender(){}
}


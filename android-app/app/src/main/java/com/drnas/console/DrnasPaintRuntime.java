package com.drnas.console;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/** Stages source-owned Windows GDI apps; no third-party executables downloaded. */
final class DrnasPaintRuntime {
    static void install(Context context,File prefix,Consumer<String> log) throws IOException {
        File dir=new File(prefix,"drive_c/DRNAS/Tools");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot make DRNAS Tools directory");
        stage(context,dir,"windows-tools/DRNASPaint.exe","DRNASPaint.exe",log);
        stage(context,dir,"windows-tools/DRNASPad.exe","DRNASPad.exe",log);
        stage(context,dir,"windows-tools/DRNASDesktop.exe","DRNASDesktop.exe",log);
    }
    private static void stage(Context context,File dir,String source,String name,
                              Consumer<String> log) throws IOException {
        File target=new File(dir,name),staging=new File(dir,name+".new");
        try(InputStream src=context.getAssets().open(source)) {
            // Build-generated APK payload is trusted; bound memory/disk use.
            try(java.io.OutputStream dst=Files.newOutputStream(staging.toPath())) {
                byte[] buffer=new byte[32*1024];long size=0;int read;
                while((read=src.read(buffer))!=-1) {
                    size+=read;
                    if(size>4L*1024*1024)throw new IOException("Bundled tool too large");
                    dst.write(buffer,0,read);
                }
                if(size<2048)throw new IOException("Bundled tool invalid/empty");
            }
            Files.move(staging.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
            log.accept("DRNAS original Windows tool "+name+
                    " installed to Wine C: ("+target.length()+" bytes).");
        } finally {
            Files.deleteIfExists(staging.toPath());
        }
    }
    private DrnasPaintRuntime(){}
}

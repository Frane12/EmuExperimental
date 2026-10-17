package com.drnas.console;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/** Installs our MIT-style original source-owned Windows tool into this Wine C: drive. */
final class DrnasPaintRuntime {
    private static final String SOURCE="windows-tools/DRNASPaint.exe";
    static File install(Context context,File prefix,Consumer<String> log) throws IOException {
        File dir=new File(prefix,"drive_c/DRNAS/Tools");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot make DRNAS Tools directory");
        File target=new File(dir,"DRNASPaint.exe"),staging=new File(dir,"DRNASPaint.exe.new");
        try(InputStream src=context.getAssets().open(SOURCE)) {
            // Bundled cross-compiled program is trusted build output; still cap size.
            try(java.io.OutputStream dst=Files.newOutputStream(staging.toPath())) {
                byte[] buffer=new byte[32*1024];long size=0;int read;
                while((read=src.read(buffer))!=-1) {
                    size+=read;
                    if(size>4L*1024*1024)throw new IOException("Bundled Paint too large");
                    dst.write(buffer,0,read);
                }
                if(size<2048)throw new IOException("Bundled Paint invalid/empty");
            }
            Files.move(staging.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
            log.accept("DRNAS Retro Paint installed to Wine C: ("+target.length()+" bytes).");
            return target;
        } finally {
            Files.deleteIfExists(staging.toPath());
        }
    }
    private DrnasPaintRuntime(){}
}

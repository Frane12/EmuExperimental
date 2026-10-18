package com.drnas.console;

import android.content.Context;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Separate Android EGL/OpenGL ES experiment; never claim Wine WGL compatibility. */
final class OpenGlAlternative {
    static void reportWineEglCandidates(File guiRoot,Consumer<String> log) {
        File lib=new File(guiRoot,"lib");
        boolean egl=new File(lib,"libEGL.so.1").isFile();
        boolean opengl=new File(lib,"libOpenGL.so.0").isFile();
        log.accept("DRNAS_WINE_EGL_CANDIDATE EGL="+(egl?"BUNDLED":"MISSING")+
                " desktopOpenGL="+(opengl?"BUNDLED":"MISSING"));
        if(!egl||!opengl)
            log.accept("DRNAS_WINE_EGL_BLOCKED: Wine 11 prefers EGL but requires "+
                    "loadable Bionic EGL and full OpenGL libraries; GLES is not a drop-in replacement.");
        else
            log.accept("DRNAS_WINE_EGL_UNVERIFIED: Bionic library files staged. "+
                    "EGL X11 surface/context/desktop OpenGL version and actual Wine WGL still unverified.");
    }

    static void probe(Context context,Consumer<String> log) {
        File executable=new File(context.getApplicationInfo().nativeLibraryDir,
                "libdrnas_gles_probe.so");
        if(!executable.isFile()) {
            log.accept("DRNAS_GLES_RESULT FAIL native probe absent");return;
        }
        Process process=null;
        try {
            ProcessBuilder builder=new ProcessBuilder("/system/bin/linker64",
                    executable.getAbsolutePath()).redirectErrorStream(true);
            Map<String,String> env=builder.environment();
            env.remove("LD_PRELOAD");
            env.put("LD_LIBRARY_PATH","/system/lib64");
            process=builder.start();
            boolean complete=process.waitFor(8,TimeUnit.SECONDS);
            if(!complete) {
                process.destroyForcibly();
                log.accept("DRNAS_GLES_RESULT TIMEOUT: isolated probe terminated");
                return;
            }
            try(InputStream in=process.getInputStream()) {
                byte[] output=in.readNBytes(8192);
                for(String line:new String(output,StandardCharsets.UTF_8).split("\\R"))
                    if(!line.isEmpty())log.accept(line);
            }
            log.accept("DRNAS_GLES_EXIT "+process.exitValue()+
                    "; this tests Android EGL/GLES only, NOT Windows OpenGL/WGL.");
        }catch(Exception error) {
            log.accept("DRNAS_GLES_RESULT FAIL: "+error.getMessage());
        }finally {
            if(process!=null && process.isAlive())process.destroyForcibly();
        }
    }
    private OpenGlAlternative(){}
}

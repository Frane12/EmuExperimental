package com.drnas.console;

import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

/** Read-only host loader inventory. Actual availability is checked with dlopen in
 * the separate Bionic GUI dependency probe; a file's presence proves nothing. */
final class WineNativeDependencyAudit {
    static void report(File guiRuntime,File wineLib,Map<String,String> environment,
                       Consumer<String> log) {
        log.accept("DRNAS_DEP_AUDIT BEGIN: Wine native loader search paths (no changes or installs)");
        String paths=environment.getOrDefault("LD_LIBRARY_PATH","");
        for(String name:new String[]{"libX11.so.6","libvulkan.so.1",
                "libEGL.so.1","libGL.so.1","libavutil.so.60","libgstgl-1.0.so"}) {
            boolean found=existsOnPath(paths,name);
            String role=name.equals("libEGL.so.1")?"opt-in: Wine11 EGL/Zink":
                     name.equals("libGL.so.1")?"optional: OpenGL GLX":
                    name.equals("libavutil.so.60")?"optional: Wine DMO/media":
                    name.equals("libgstgl-1.0.so")?"optional: Wine GStreamer/media":
                    "core GUI/Vulkan";
            log.accept("DRNAS_DEP_AUDIT FILE "+name+"="+(found?"PRESENT":"ABSENT")+
                    " ("+role+"; verify with native dlopen)");
        }
        for(String module:new String[]{"winex11.so","winegstreamer.so","winedmo.so"}) {
            File candidate=new File(wineLib,"wine/aarch64-unix/"+module);
            log.accept("DRNAS_DEP_AUDIT WINE_MODULE "+module+"="+
                    (candidate.isFile()?"PRESENT":"MISSING")+"; native dependencies need dlopen");
        }
        log.accept("DRNAS_DEP_AUDIT PACKAGE_RUNTIME="+
                (new File(guiRuntime,"lib/libX11.so.6").isFile()?"PRESENT":"MISSING")+
                " | OpenGL and media components are NOT prerequisites for DXVK Vulkan by themselves");
    }
    static boolean existsOnPath(String searchPath,String name) {
        if(searchPath==null || name==null || name.indexOf('/')>=0)return false;
        for(String directory:searchPath.split(":")) {
            if(!directory.isEmpty() && new File(directory,name).isFile())return true;
        }
        return false;
    }
    private WineNativeDependencyAudit(){}
}

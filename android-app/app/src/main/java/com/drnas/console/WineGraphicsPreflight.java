package com.drnas.console;

import java.util.function.Consumer;

/**
 * Wine X11 WGL needs a working libGL and GLX. A file on disk alone cannot
 * make the Canvas X server implement an extension. Native Vulkan is separate.
 */
final class WineGraphicsPreflight {
    static String classifyOpenGl(boolean libGLFile,boolean glxAvailable) {
        if(!libGLFile && !glxAvailable)return "BLOCKED_LIBGL_AND_GLX";
        if(!libGLFile)return "BLOCKED_LIBGL";
        if(!glxAvailable)return "BLOCKED_GLX";
        return "PREREQUISITES_PRESENT_CONTEXT_NOT_VERIFIED";
    }
    static void report(boolean libGLFile,boolean glxAvailable,boolean nativeVulkan,
                       Consumer<String> log) {
        log.accept("DRNAS_OPENGL_PREFLIGHT libGL.so.1="+(libGLFile?"FILE_PRESENT":"FILE_MISSING")+
                " X11_GLX="+(glxAvailable?"ADVERTISED":"ABSENT")+
                " WGL="+classifyOpenGl(libGLFile,glxAvailable));
        if(!glxAvailable)
            log.accept("DRNAS_OPENGL_NOTICE: Canvas X11 lacks GLX; libGL alone cannot enable Wine WGL.");
        if(nativeVulkan)
            log.accept("DRNAS_VULKAN_ALTERNATIVE: WineVulkan requested independently of WGL. "+
                    "Actual GPU, WSI and first-frame rendering remain unverified.");
    }
    private WineGraphicsPreflight(){}
}

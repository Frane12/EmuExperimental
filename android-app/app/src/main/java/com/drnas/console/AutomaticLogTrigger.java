package com.drnas.console;

/** Normal short-lived X11 probes and clean user exits must not generate reports. */
final class AutomaticLogTrigger {
    static boolean matches(String line) {
        return line.contains("X11 request FAILED") ||
                line.contains("XIO:  fatal IO error") ||
                line.contains("X11 client IO ended") && line.contains("Connection reset") ||
                line.startsWith("Desktop stopped:") ||
                line.startsWith("Native Vulkan presentation FAILED") ||
                line.startsWith("Vulkan test:") ||
                line.equals("GUI bounded session ended.");
    }
    private AutomaticLogTrigger(){}
}

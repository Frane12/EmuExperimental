package com.drnas.console;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class R64Arm64Isolation {
    static final String CMD_NORMAL="cmd-normal";
    static final String CMD_BOOTSTRAP="cmd-bootstrap";
    static final String HEADLESS="headless";
    static final int CMD_TIMEOUT_SECONDS=25;
    static boolean isCpuOnly(String mode) {
        return CMD_NORMAL.equals(mode) || CMD_BOOTSTRAP.equals(mode) || HEADLESS.equals(mode);
    }
    static boolean isCommand(String mode) {
        return CMD_NORMAL.equals(mode) || CMD_BOOTSTRAP.equals(mode);
    }
    static String newMarker() {
        return "DRNAS_R64_CMD_OK_"+UUID.randomUUID().toString().replace("-","");
    }
    static boolean matchesMarker(String line,String expected) {
        return expected!=null && line!=null && line.trim().equals(expected);
    }
    static List<String> cmdCommand(File linker,File wine,String marker) {
        if(marker==null || !marker.matches("DRNAS_R64_CMD_OK_[0-9a-f]{32}"))
            throw new IllegalArgumentException("Invalid CMD marker");
        List<String> args=new ArrayList<>(WineLaunchProbe.linkerCommand(linker,wine));
        args.set(args.size()-1,"cmd.exe");
        args.add("/d"); args.add("/c"); args.add("echo "+marker);
        return args;
    }
    static void configure(Map<String,String> env,String mode) {
        if(!isCpuOnly(mode))throw new IllegalArgumentException("Unknown isolated mode");
        for(String key:new String[]{"DISPLAY","WAYLAND_DISPLAY","XAUTHORITY",
                "WINE_USE_EGL","EGL_PLATFORM","MESA_LOADER_DRIVER_OVERRIDE",
                "GALLIUM_DRIVER","VK_ICD_FILENAMES","VK_DRIVER_FILES","VK_LAYER_PATH"})
            env.remove(key);
        env.remove("WINEBOOTSTRAPMODE");
        if(CMD_BOOTSTRAP.equals(mode))WineBootProbe.configureBootstrapMode(env);
        // Do not advertise a working X11 driver to headless guests.
        String overrides=env.getOrDefault("WINEDLLOVERRIDES","");
        env.put("WINEDLLOVERRIDES",overrides.replace("winex11.drv=b","winex11.drv=d"));
    }
    private R64Arm64Isolation(){}
}

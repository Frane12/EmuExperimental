package com.drnas.console;

import java.io.IOException;
import java.util.List;

final class BlenderShellLaunch {
    static final String ID="builtin:blender";
    static List<String> arguments(String custom,String graphicsApi) throws IOException {
        List<String> args=new java.util.ArrayList<>(LaunchArguments.forApp(
                BundledBlenderArchive.WINDOWS_EXE,custom,graphicsApi));
        boolean backend=false,windowMode=false,python=false;
        for(String a:args) {
            if(a.equals("--gpu-backend")||a.startsWith("--gpu-backend="))backend=true;
            if(a.equals("-W")||a.equals("--window-fullscreen")||a.equals("-w")||
                    a.equals("--window-border")||a.equals("-M")||a.equals("--window-maximized"))windowMode=true;
            if(a.equals("-P")||a.equals("--python"))python=true;
        }
        // Match the tested r27 ARM64 launch; auto must not fall back to GLX.
        if(!backend){args.add("--gpu-backend");args.add("vulkan");}
        // Keep GHOST in normal window mode. Only the primary HWND fills the screen.
        if(!windowMode)args.add("--window-border");
        if(!python){args.add("--python");args.add(BundledBlenderArchive.WINDOWS_SETUP);}
        return args;
    }
    private BlenderShellLaunch(){}
}


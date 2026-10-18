package com.drnas.console;

import java.io.IOException;
import java.util.List;

final class BlenderShellLaunch {
    static final String ID="builtin:blender";
    static List<String> arguments(String custom,String graphicsApi) throws IOException {
        List<String> args=new java.util.ArrayList<>(LaunchArguments.forApp(
                BundledBlenderArchive.WINDOWS_EXE,custom,graphicsApi));
        boolean backend=false,fullscreen=false,python=false;
        for(String a:args) {
            if(a.equals("--gpu-backend")||a.startsWith("--gpu-backend="))backend=true;
            if(a.equals("-W")||a.equals("--window-fullscreen"))fullscreen=true;
            if(a.equals("-P")||a.equals("--python"))python=true;
        }
        // Match the tested r27 ARM64 launch; auto must not fall back to GLX.
        if(!backend){args.add("--gpu-backend");args.add("vulkan");}
        if(!fullscreen)args.add("--window-fullscreen");
        if(!python){args.add("--python");args.add(BundledBlenderArchive.WINDOWS_SETUP);}
        return args;
    }
    private BlenderShellLaunch(){}
}

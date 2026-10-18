package com.drnas.console;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Configure Wine's Vulkan PE->Unix bridge for a native Vulkan Windows app.
 * This is separate from DXVK (D3D9/10/11->Vulkan); it does not emulate a GPU.
 * A successful preflight proves only the expected binaries exist, NOT that
 * vkCreateDevice, X11 WSI and vkQueuePresentKHR work on a particular phone.
 */
final class WineVulkanRuntime {
    static void configure(Map<String,String> env,boolean nativeVulkan) {
        if(!nativeVulkan)return;
        String previous=env.getOrDefault("WINEDLLOVERRIDES","");
        env.put("WINEDLLOVERRIDES",previous+
                (previous.isEmpty()?"":";")+"vulkan-1=b;winevulkan=b");
        env.put("DRNAS_GRAPHICS_API","NATIVE_WINEVULKAN");
        // Do not disable DXVK globally: other apps in the persistent prefix
        // may still use D3D. A native Vulkan app calls vulkan-1.dll directly.
    }

    static File requireBridge(File wineLib,File guiLib,Consumer<String> log)
            throws IOException {
        File pe=findModule(wineLib,"winevulkan.dll");
        if(pe==null){
            log.accept("DRNAS_WINEVULKAN_BLOCKED: winevulkan.dll is absent/invalid in staged Wine for ARM64/ARM64EC/x64.");
            throw new IOException("WineVulkan PE module missing: winevulkan.dll. Vulkan mode cannot be verified.");
        }
        File loader=new File(guiLib,"libvulkan.so.1");
        if(!loader.isFile()||Files.isSymbolicLink(loader.toPath())||loader.length()<1024){
            log.accept("DRNAS_WINEVULKAN_BLOCKED: packaged Khronos libvulkan.so.1 missing.");
            throw new IOException("WineVulkan Unix loader is unavailable.");
        }
        log.accept("DRNAS_WINEVULKAN_BRIDGE_PRESENT: "+pe.getAbsolutePath()+
                "; Khronos="+loader.getAbsolutePath()+
                " (file presence only, not successful GPU/WSI initialization).");
        return pe;
    }

    static File findModule(File wineLib,String name) throws IOException {
        for(String arch:new String[]{"aarch64-windows","arm64ec-windows","x86_64-windows"}){
            File f=new File(wineLib,"wine/"+arch+"/"+name);
            if(isPE(f))return f.getCanonicalFile();
        }
        return null;
    }

    static boolean isPE(File file) throws IOException {
        if(!file.isFile()||Files.isSymbolicLink(file.toPath())||file.length()<512)return false;
        try(FileInputStream in=new FileInputStream(file)){
            return in.read()=='M' && in.read()=='Z';
        }
    }
    private WineVulkanRuntime(){}
}

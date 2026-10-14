package com.drnas.console;

/**
 * The first compiled Android/ARM64 NDK component of the Drnas host.
 * It inspects guest files but never executes ELF binaries from writable
 * app storage or claims a Wine process is running.
 */
public final class NativeHost {
    private static final String ERROR;
    static {
        String error=null;
        try {System.loadLibrary("drnas_host");}
        catch (Throwable e) {error=e.getClass().getSimpleName()+": "+e.getMessage();}
        ERROR=error;
    }
    public static boolean available(){return ERROR==null;}
    public static String loadError(){return ERROR==null?"":ERROR;}
    public static String hostInfo(){
        return available()?nativeHostInfo():"Native host unavailable: "+ERROR;
    }
    public static String vulkanInfo(){
        return available()?nativeVulkanInfo():"Native Vulkan probe unavailable: "+ERROR;
    }
    public static String vulkanDeviceName(){
        return available()?nativeVulkanDeviceName():"";
    }
    public static String probeFile(String path){
        return available()?nativeProbeFile(path):"Native ELF probe unavailable: "+ERROR;
    }
    /**
     * Observe executable-memory policy for the selected read-only DLL/file.
     * No guest instruction is executed and the source file is not modified.
     */
    public static String execMappingProbe(String path){
        return available()?nativeExecMappingProbe(path):
                "Native executable-mapping probe unavailable: "+ERROR;
    }
    private static native String nativeExecMappingProbe(String path);
    private static native String nativeHostInfo();
    private static native String nativeVulkanInfo();
    private static native String nativeVulkanDeviceName();
    private static native String nativeProbeFile(String path);
    private NativeHost(){}
}

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
    public static String probeFile(String path){
        return available()?nativeProbeFile(path):"Native ELF probe unavailable: "+ERROR;
    }
    public static String cpuAssistProbe(){
        return available()?nativeCpuAssistProbe():"CPU Assist unavailable: "+ERROR;
    }
    public static String cpuAssistBaseline(){
        return available()?nativeCpuAssistBaseline():"CPU Assist unavailable: "+ERROR;
    }
    public static String acceleratorProbe(){
        return available()?nativeAcceleratorProbe():"Accelerator probe unavailable: "+ERROR;
    }
    private static native String nativeAcceleratorProbe();
    private static native String nativeCpuAssistProbe();
    private static native String nativeCpuAssistBaseline();
    private static native String nativeHostInfo();
    private static native String nativeVulkanInfo();
    private static native String nativeProbeFile(String path);
    private NativeHost(){}
}

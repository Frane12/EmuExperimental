package com.drnas.console;

/** Device-family classification for release notes and automatic Vulkan routing.
 * This does not constitute a hardware render validation.
 */
final class BlenderGpuRouting {
    static String backend(String name,int api) {
        if(SamsungXclipseSupport.isXclipse(name))
            return "XCLIPSE_FIRMWARE_VULKAN";
        String mali=MaliDriverCatalog.gpu("",name);
        if(!mali.isEmpty()) {
            if(!MaliDriverCatalog.compatible("",name,api,"").isEmpty())
                return "MALI_PANVK_CANDIDATE_SYSTEM_FALLBACK";
            return "MALI_SYSTEM_VULKAN";
        }
        if(name!=null && name.toLowerCase(java.util.Locale.ROOT).contains("mali"))
            return "MALI_SYSTEM_VULKAN";
        String adreno=TurnipDevicePolicy.target(name);
        if(!GraphicsDriverCatalog.compatible(adreno,api).isEmpty())
            return "ADRENO_TURNIP_CANDIDATE_SYSTEM_FALLBACK";
        if(adreno.startsWith("A"))return "ADRENO_SYSTEM_VULKAN";
        return "SYSTEM_VULKAN";
    }
    private BlenderGpuRouting(){}
}

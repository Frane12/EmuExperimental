package com.drnas.console;

import java.util.regex.Pattern;

/** Xclipse 940 / Exynos 2400 uses the phone's AMD RDNA 3-based system Vulkan.
 * No Adreno Turnip or Mali PanVK binary is compatible with this GPU.
 * The vendor ICD is supplied by the Samsung firmware, not distributed by APK.
 */
final class SamsungXclipseSupport {
    private static final Pattern XCLIPSE_940=Pattern.compile(
            "(?i)\\bxclipse[\\s_-]*940\\b");
    static boolean isXclipse940(String deviceName) {
        return deviceName!=null && XCLIPSE_940.matcher(deviceName).find();
    }
    // Xclipse family (Samsung AMD) always uses its own firmware Vulkan
    // stack. Generic recognition is intentionally safe: unknown Xclipse
    // generations NEVER receive an incompatible Turnip/PanVK binary.
    private static final Pattern XCLIPSE_FAMILY=Pattern.compile(
            "(?i)\\bxclipse[\\s_-]*[0-9]{3,4}\\b");
    static boolean isXclipse(String name) {
        return name!=null && XCLIPSE_FAMILY.matcher(name).find();
    }
    static String backend(String deviceName) {
        if(isXclipse940(deviceName))return "SAMSUNG_XCLIPSE_940_SYSTEM_VULKAN";
        return isXclipse(deviceName)?"SAMSUNG_XCLIPSE_SYSTEM_VULKAN":"";
    }
    private SamsungXclipseSupport(){}
}

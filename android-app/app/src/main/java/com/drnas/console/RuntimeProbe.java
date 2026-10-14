package com.drnas.console;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.Arrays;
import java.util.Locale;

/** Capability checks are observations, not compatibility claims. */
public final class RuntimeProbe {
    public static String inspect(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        boolean vulkanFeature = ctx.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
        return "Android API " + Build.VERSION.SDK_INT +
                "\nHardware: " + Build.MANUFACTURER + " " + Build.MODEL +
                "\nABIs: " + Arrays.toString(Build.SUPPORTED_ABIS) +
                "\nRAM total: " + (mi.totalMem / (1024 * 1024)) + " MiB" +
                "\nAndroid Vulkan feature: " + (vulkanFeature ? "present" : "not advertised") +
                "\nAdreno chip: NOT PROBED (no GPU ID spoofing)" +
                "\nProton/Wine: NDK + guarded Android/Bionic CLI startup probe" +
                "\nFEXCore: PE bridge check + prefix layering available" +
                "\nDXVK/Turnip: archives stageable; Vulkan ICD NOT CONNECTED";
    }

    public static String windowsRuntimeStatus() {
        return "DRNAS v0.5.17 desktop interface: embedded X11 presents a stable 1280x720 " +
                "Wine Explorer virtual desktop, scaled to the Android display with translated touch. " +
                "GUI diagnostic text is hidden, not deleted; RAW logs remain available on demand. " +
                "Proton 11.0-2 ARM64EC, FEX 2609 and DXVK 1.10.3 are bundled. " +
                "Turnip ICD and GPU acceleration must be verified separately.";
    }
    private RuntimeProbe() {}
}

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
                "\nProton/Wine loader: NOT CONNECTED" +
                "\nFEXCore bridge: PE bridge staging available; NOT CONNECTED" +
                "\nDXVK/Turnip: NOT CONNECTED";
    }

    public static String windowsRuntimeStatus() {
        return "UNSUPPORTED in v0.1: no native Wine/Bionic loader, FEXCore bridge, " +
                "X server, PulseAudio or Vulkan loader is wired to Android. " +
                "WCP archives and FEXCore PE bridges can be validated and staged, but cannot yet be executed.";
    }
    private RuntimeProbe() {}
}

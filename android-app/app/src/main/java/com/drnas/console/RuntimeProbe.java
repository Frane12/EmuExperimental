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
        return "EXPERIMENTAL v0.5.0: active wineserver + kernel32, kernelbase and NTDLL Wine PE loader diagnostics. " +
                "X server, PulseAudio or Vulkan loader is wired to Android. " +
                "Start container attempts Wine --version via Android linker64 PROGRAM --version only for verified Bionic ARM64 ELF. Opt-in wineboot attempts disposable prefix initialization; real success needs exit 0 and registry changes. X11/audio/Turnip ICD and games are not connected.";
    }
    private RuntimeProbe() {}
}

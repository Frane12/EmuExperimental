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
        return "EXPERIMENTAL v0.5.15: Wine CLI verified on Adreno 840, Wineboot not initialized. " +
                "RAW trace shows executable PE sections of ntdll.dll fail to obtain RX memory protection. " +
                "A new native probe compares file-backed R->RX, dirty private RW->RX and anonymous RW->RX " +
                "mappings without executing guest bytes. Results go to both session and RAW logs. " +
                "External storage permissions do NOT change Android SELinux executable-memory policy. " +
                "X11/audio/Turnip ICD and Windows games remain unverified.";
    }
    private RuntimeProbe() {}
}

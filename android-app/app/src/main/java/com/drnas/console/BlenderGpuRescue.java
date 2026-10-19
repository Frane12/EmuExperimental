package com.drnas.console;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Experimental, device-scoped fallback for the Nothing Phone (2a) A142.
 * Its Mali-G610 already has an Android Vulkan ICD; never inject Adreno
 * Turnip or a PanVK build intended for older Mali generations.
 *
 * For the stock "auto" Blender profile only, try the APK's independent
 * Wine/EGL/Mesa-Zink OpenGL route when Blender's Vulkan GPU configuration
 * screen rejects this GPU. This is a test candidate, not a claim that
 * eglInitialize, GL 4.3 or the first Blender frame will succeed.
 */
final class BlenderGpuRescue {
    static boolean useOpenGl(String model, String gpu, String graphicsApi, String customArgs) {
        if (!"auto".equals(graphicsApi) || model == null) return false;
        String phone = model.trim().toLowerCase(Locale.ROOT);
        if (!phone.equals("nothing a142") && !phone.equals("a142")) return false;
        // If native device-name enumeration succeeded, don't offer this
        // route for an unexpected GPU (e.g., an emulator or spoofed model).
        if (gpu != null && !gpu.trim().isEmpty()) {
            String candidate = gpu.toLowerCase(Locale.ROOT);
            if (!candidate.matches(".*\\bmali\\s*[- ]?g610(?![0-9]).*")) return false;
        }
        try {
            List<String> args = LaunchArguments.parse(customArgs);
            for (String arg : args) {
                if (arg.equalsIgnoreCase("--gpu-backend") ||
                        arg.toLowerCase(Locale.ROOT).startsWith("--gpu-backend="))
                    return false; // user override always wins
            }
        } catch (IOException invalidUserArgs) {
            return false; // never override an invalid explicit profile
        }
        return true;
    }
    private BlenderGpuRescue() {}
}

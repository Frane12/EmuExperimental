package com.drnas.console;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Strictly scoped OpenGL/Zink experiment for the Xiaomi 24117RN76E,
 * observed with a Mali-G57 MC2. The normal public app is unchanged.
 *
 * This chooses a *route*, not a claim that libEGL can initialize or that
 * Zink exposes Blender's OpenGL 4.3 plus required extensions.
 */
final class BlenderMaliG57Lab {
    static final String MODEL = "24117RN76E";

    static boolean useOpenGl(String model, String gpu, String graphicsApi, String customArgs) {
        if (model == null || !"auto".equals(graphicsApi) ||
                !MODEL.equalsIgnoreCase(model.trim())) return false;

        // If Vulkan enumeration reports a real renderer, never reroute an
        // unexpected GPU just because the device model string was spoofed.
        if (gpu != null && !gpu.trim().isEmpty()) {
            String name = gpu.toLowerCase(Locale.ROOT);
            if (!name.matches(".*\\bmali\\s*[- ]?g57(?![0-9]).*")) return false;
        }

        // Explicit settings and command line options must always take priority.
        try {
            List<String> args = LaunchArguments.parse(customArgs);
            for (String arg : args) {
                if (arg.equalsIgnoreCase("--gpu-backend") ||
                        arg.toLowerCase(Locale.ROOT).startsWith("--gpu-backend=")) return false;
            }
        } catch (IOException invalidArguments) {
            return false;
        }
        return true;
    }

    private BlenderMaliG57Lab() {}
}

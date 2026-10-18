# DRNAS EMU preAlpha 0.1 r18 — graphics preflight and Blender Vulkan arguments

Builds on r17's native WineVulkan and Windows IShellLink launch helper.

## Implemented

- A shortcut with an explicit `--gpu-backend vulkan` or
  `--gpu-backend=vulkan` requests the WineVulkan bridge while its graphics
  profile is `Auto`. An explicit DXVK-only profile is not silently overridden.
- On each desktop boot, issue a real X11 protocol 11 `QueryExtension("GLX")`
  to the built-in X server and report libGL presence separately.
  This does not fake GLX or affect the desktop when the query fails.
- Export `DRNAS_OPENGL_PREFLIGHT`, `DRNAS_WINEVULKAN_AUTO` and existing
  `DRNAS_WINEVULKAN_BRIDGE_PRESENT` in the full Wine GUI TXT diagnostic.
- Retain version code 83, the app ID, stable signing policy and user files.

## Known unsupported / unverified

The Java Canvas X11 server does not implement GLX or DRI3. Pinned Bionic
libGL.so.1 may be absent or a dangling symlink; do not package invalid
libraries or pretend Wine's WGL OpenGL renderer works. Native WineVulkan
does not need libGL, but GPU device creation, X11 presentation,
first-frame and cleanup are still unverified on actual hardware.
A previous manual Vulkan WSI experiment hung inside vkDestroySwapchainKHR.
r18 keeps the native startup enumeration probe and does not run the
dangerous swapchain teardown automatically.

## Test

1. Open Wine desktop. Export GUI TXT. Find `DRNAS_OPENGL_PREFLIGHT`.
2. Create a Blender shortcut, leave graphics API = Auto, and enter custom
   arguments `--gpu-backend vulkan` (supported Blender versions only).
   Alternatively explicitly select Native Vulkan in the shortcut options.
3. Look for `DRNAS_WINEVULKAN_AUTO`, `DRNAS_WINEVULKAN_BRIDGE_PRESENT`,
   the native Vulkan enumeration and Wine/Blender logs.
4. Device enumeration or a mapped window does **not** prove a Vulkan frame
   was actually presented. Do not infer successful Blender rendering.

The underlying GLX/EGL rendering backend is a separate larger engineering
task, not implemented in this patch.

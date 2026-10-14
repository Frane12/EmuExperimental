# DRNAS Console v0.5.9 — X11 dependencies and log following

The 19:05 GUI trace reached Wine explorer/winecfg but mapped no Wine window.
It reported missing FreeType, failure loading libvulkan.so.1, repeated winex11
unloads, display configuration failures and nodrv_CreateWindow. Those are
observations; the exact failing dependency is not established by that trace.

This build bundles the dependency closure of X11 clients, FreeType,
Fontconfig and the Vulkan loader from a checksum-pinned Bionic rootfs.
The APK verifies per-file hashes and installs only its own gui-runtime/v1
folder. It preserves imported components and uses Android system fonts with
an app-private Fontconfig configuration/cache. Both isolated wineboot and
GUI children receive the new library path.

A separate 8-second native probe checks dlopen and prints dlerror, attempts
XOpenDisplay against the embedded X11 server, and reports winex11 standalone
loading (Wine-specific unresolved exports are not proof of a missing library).
Wine still must initialize the driver and map a real window. GUI trace errors
remain visible after the routine-output cap; full trace remains on disk.
Vulkan loader availability is not proof of custom Turnip or working DXVK.

Log view follows after text layout using local scrollTo, without focus changes
or scrolling the outer page. Touch scrolling pauses following; returning to
the bottom or pressing Latest resumes it. Follow state survives panel redraws.

On-device acceptance: dependencies load; Xlib connects; Wine window maps and
Canvas draws it; text is legible; init log follows; scrolling back stays still;
Latest resumes following; Save GUI TXT produces a complete readable file.
If no window maps, export GUI TXT and inspect the new dependency errors.

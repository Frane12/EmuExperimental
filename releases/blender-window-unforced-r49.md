# Blender DRNAS r49 — PRIVATE unforced-window diagnostic (A/B vs r48)

This Android ARM64 Blender test removes the forced **Blender guest fullscreen** command-line flag; it does not disable immersive Android screen presentation. It is NOT a confirmed window-management fix.

## Changes from r48

- No default `--window-fullscreen`, `--window-border`, or `--window-maximized` from DRNAS. Explicit user-supplied Blender window arguments still work.
- Android immersive fullscreen and the existing X11 framebuffer stretch remain enabled. This test isolates only the Blender-side command-line flag.
- Retains r48's removal of the persistent per-app Wine `Managed=N` / `Decorated=N` overrides and keeps the r46 Win32 geometry hook disabled.
- Retains the existing direct Wine launch, Vulkan/Turnip preflight, GPU driver selection, UI controls, Blender 4.5.14 ARM64, and Android D: project folder.
- Corrects a GUI log message so it does not claim that Blender's own fullscreen was requested.
- versionCode **117** with the same existing private stable signing identity for in-place upgrades from r48.

## Device A/B instructions

1. Upgrade from r48 to r49 and start the application (no app-data wipe needed).
2. Confirm Blender's *main window* appears, then test **Render View / F11** and **Edit → Preferences**. Observe whether these are separate controllable windows; F11 may switch to a Render Result editor depending on Blender state and is **not** proof of a separate OS-level window.
3. See whether the unexplained upper border remains.
4. Share screenshot and GUI log. Search for `DRNAS_WINEVULKAN_LAUNCH_ARGUMENTS`, which must contain Vulkan but **no `--window-fullscreen`** unless manually configured.

**This remains a PRIVATE draft, not a public release.** CI tests/build cannot verify Blender guest behavior on actual Android hardware.

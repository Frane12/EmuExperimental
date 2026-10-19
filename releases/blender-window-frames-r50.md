# DRNAS Blender 4.5.14 ARM64 r50 — private Wine frame-policy A/B experiment

**Observed in r49:** Blender Preferences opens as a separate window, but has no titlebar, close X or minimize controls. F11 switches to Render Result, which may be an editor instead of a separate OS-level window.

## One isolated change
- Maintain **no forced Blender fullscreen** flag, as in r49. Android remains immersive with existing X11 display stretch.
- Set app-specific Wine X11 driver values **Managed=N**, **Decorated=Y** for `blender.exe`.
- Reason: Android DRNAS X server has no external X11 window manager. Wine's default Managed=Y can delegate frame decorations to a WM that does not exist. This experiment asks Wine to handle nonclient rendering while retaining decorations.
- Bump one-time registry migration marker to apply the new policy even after installation of r46/r48/r49.
- Keep WineVulkan, Vulkan-only GPU backend, Turnip preflight, device drivers, Save/Load D:, touchscreen controls, and the same signing identity/package ID. versionCode **118**.

## Device verification
1. Upgrade without uninstalling; open **Edit → Preferences**.
2. Check for a visible titlebar and **X**, drag and minimize behavior; also check whether the main Blender viewport remains usable.
3. Check **Render → Render Image** (F12) with **Preferences → Interface → Temporary Editors → Render In: New Window**. Test whether render has independent decorations, and close it.
4. Share screenshots and a GUI diagnostic log containing `DRNAS_BLENDER_R50_FRAME_POLICY` and `DRNAS_X11_CRITICAL_` markers.

No promise that the X11 server supports all needed window-manager operations. If frame controls are still absent, the next step is to implement a real minimal X11 window-manager/decorator layer rather than claiming a registry-only fix.

Private review build, not public.

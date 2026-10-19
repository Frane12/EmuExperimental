# Blender r59 - PRIVATE X11 wheel mapping correction
Based on r58 commit 224061402ed2390f6aabbfc4e6cf218c4f27de56. VersionCode 127. Build and device verification are separate steps. Do not publish.

## Real source-code root cause
- User reports visible slider (screenshot) but no actual Blender zoom.
- `BlenderZoomPanel.pulseWheel()` sends X11 button 4 (wheel up) and 5 (wheel down) via `ScreenView.setOverlayPointerButton()`.
- `ScreenView.updatePointerButtons()` starts with `Pointer.mapButton()`.
- `Pointer._buttonMap` in r58 was **only `{1,2,3}`**. Consequently `mapButton(4)` and `mapButton(5)` both returned **0**; updatePointerButtons returned immediately. This is a confirmed mapping bug, not a rendering fault.

## Narrow fix
- Initialize the X11 pointer mapping to `{1,2,3,4,5}` so the X11 protocol advertises all five buttons, lets wheel presses/releases through and supports SetPointerMapping with five entries.
- Keep mouse LMB/RMB/Middle behavior and existing r58 slider/zoom tick conversion, keyboard dimensions, Vulkan, r57 safe fullscreen, selected D:, and separate Preferences/Render window handling unchanged.
- Add JVM regression test for default mapping 1..5 and rejection of out-of-range button 0,6,-1.

## Real device validation (A840)
1. Upgrade r58 -> r59 in-place. Cursor over the 3D View and tap +, drag slider toward + and toward -. Model camera distance should change, and FPS/PutImage must continue.
2. Test at least one external mouse wheel scroll and normal touch click and LMB/RMB.
3. Check pointer when hovering *Properties* vs 3D View; wheel goes to hovered editor in Blender, not necessarily viewport.
4. Test the r57 safe fullscreen menu, Preferences, Render, Save/Open on D:, then export GUI log.
5. Keep draft private until on-device success.

The native Blender/Wine fullscreen command remains unsafe. Use DRNAS safe fullscreen only.

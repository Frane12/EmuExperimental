# Blender ARM64 DRNAS r60 — PRIVATE smooth 3D viewport zoom

Based on working r59 c5d114e75470b4c0b17444e736242bf40a6df141, no public release. Stable signing identity, package com.drnas.console, versionCode 128.

## User video 46894.mp4
User confirms r59 zoom works but requests truly smooth, continuous control analogous to dragging Blender's zoom navigation gizmo. Current r59 generated discrete X11 wheel buttons 4/5, visibly jumping per tick.

## Implementation
- Preserve same Android ZOOM pill, - and + buttons and slider layout; switch **default mode** to fine-grained smooth zoom rather than quantized mouse wheel. Every SeekBar unit is sent as a signed delta (no spring-back reverse movement).
- Use dedicated Android HandlerThread with coalesced atomic **r60:seq:cumulativeDelta** signal file in Blender's app-owned Wine C: folder, never write from UI or X11 worker. At most one update for a burst of slider movements. Limit message size and deltas.
- Blender app startup registers a lightweight `bpy.app.timers` callback. It reads that bounded signal while idle about every 65 ms, active at most 40 Hz, and changes only active main 3D View's `region_3d.view_distance` with exponential interpolation factor 0.32; each changed frame calls `area.tag_redraw()`.
- This is Blender's own viewport camera movement, **not** Wine window geometry or fullscreen API; UI mouse/keyboard, graphics backend and guest memory ownership remain unchanged.
- Smooth mode independently targets the largest VIEW_3D region even if the cursor is over UI Properties. Wheel r59 mode remains accessible by **long-press ZOOM**, and may be chosen if Python startup timer reports installed=false.
- Existing r57 Android safe fullscreen and r56 titlebar crop, Vulkan/Adreno route, unchanged Blender keyboard dimensions, D: save, Preferences/Render remain in scope for regression testing.
- This changes Blender 3D viewport orbit/view distance only, not render camera focal length, scene object scale, or rendering resolution.

## A840 acceptance checks
1. Install over r59, same signed app package, do not delete data. Blender 4.5.14 startup should log `DRNAS_SMOOTH_ZOOM_TIMER registered=true r60`.
2. Zoom slowly with slider. The cube should grow/shrink smoothly with visible intermediate frames and not jump by a discrete wheel notch. Reverse mid-animation and tap +/-.
3. Verify the desktop GUI remains responsive, no significant X11 request flooding or frame stall; compare 20-second sampled render FPS while dragging and idle.
4. Long press ZOOM once to turn on WHEEL fallback. Confirm r59 previous behavior still works, then long-press to return to Smooth.
5. Open Blender Window > Toggle Window Fullscreen (DRNAS), ensure Android-only crop toggle works without freeze. Preferences/Render windows remain separate.
6. Save/Open from D:, close Blender, relaunch and load .blend, repeat slider (timer persistent across blend load). Do not publish before successful device test.

**Caution:** a Blender timer is involved only for native RNA View3D distance animation. The previous broken r51 timer synchronously called Win32 fullscreen APIs from Blender main thread; r60 deliberately does not do so. Still requires real-device test because the Wine viewport's FPS can limit visible smoothness.

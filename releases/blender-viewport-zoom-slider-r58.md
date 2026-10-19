# Blender ARM64 DRNAS r58 — PRIVATE viewport zoom control

Parent r57 at 694a0b55ea781b769cb6daa13e8be5f87abfbc61. Same signing identity and package `com.drnas.console`, versionCode **126**. Remains a private draft, not on-device tested.

## What changed
- Adds an unobtrusive transparent **ZOOM** pill on the Android right edge, center vertically. Tap ZOOM to collapse/reopen. Preferences remember whether expanded.
- When expanded: **−** zoom out, a 0..100 spring-loaded center slider, **+** zoom in.
- The slider converts signed 5-point moves to mouse wheel press/release pulses: X11 button 4 = zoom in, button 5 = zoom out. Up to ten pulses per UI callback; cumulative steps accounted for, no per-frame polling, no shared X11 locks on Android UI thread.
- Input is sent through the established nonblocking input executor; it does **not** trigger native Wine fullscreen, move the X11 pointer, change Blender render zoom or alter native window geometry.
- Position the mouse cursor over the 3D View editor before moving the slider; the wheel affects whichever Blender editor is hovered.
- Original Blender shortcut keyboard remains unchanged in layout/dimensions. Existing r57 Android safe fullscreen bridge, Vulkan, selected D: drive, GPU paths, multiwindow dialogs remain unchanged.

## Test on A840 before public release
1. Open Blender and wait for mapped window. ZOOM pill appears right-center. Drag left/right while pointer stays above 3D View.
2. Verify +/− and slider zoom the model smoothly and leave Blender interactive; repeated touch drag should not cause X11 queue backlog or UI stalls. Try top/bottom safe cropped region and window popups.
3. Collapse ZOOM and confirm it no longer obscures Blender's right-side tools. Reopen without changing the shortcut keyboard dimensions.
4. Verify Window > Toggle Window Fullscreen (DRNAS) via r57 bridge still toggles Android crop (not native Wine fullscreen) and Preferences/Render close normally.
5. Export GUI log and FPS sample to confirm input completion and continued PutImage after zoom. Do not publish until verified.

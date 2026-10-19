# Blender r56 — PRIVATE Android-only safe fullscreen

Parent: r55 07c10d4d25b5d3ac54dfb13d167a36479abd54bf. com.drnas.console versionCode 124, established permanent certificate. Not public and not yet verified on A840.

## Device evidence from 2026-10-19 r55 log
- Editing, keyboard, touch and Vulkan view operate before selecting Blender Window > Toggle Window Fullscreen.
- After that action input worker completes 549/549 events, focus stays on Blender HWND 11534337, no persistent pointer grab.
- PutImage stays 121/121 and new-frame production stops as X11 request count rises from approximately 3108 to 90975. Last opcode snapshot 43 GetInputFocus does not establish that every request is a focus request.
- Android process survives; no demonstrated RAM OOM/native crash. Native Wine fullscreen transition suspected but exact wait remains unproven.

## r56 isolated change
- Display Blender content full Android height using a top-only crop of 23 guest pixels, mapped consistently for X11 touchscreen, native cursor and independent Android cursor overlay.
- Preserve Wine HWND geometry, 1280x720 / 960x544 guest resolution, Vulkan, drivers, Android D:, popup decorations, renderer and key-panel dimensions.
- Blender startup script replaces the Window menu native fullscreen entry with DRNAS safe informational operator. Logs explicit installed=true/false. Does NOT override arbitrary F3 Python calls or custom key bindings that invoke native Wine fullscreen.
- DRNAS menu adds Android Fullscreen ON/OFF to compare cropped and unmodified host composition without requesting Wine fullscreen.
- Original r55 Python window-layout timer remains disabled.

## A840 verification before any release
1. Upgrade in place; keep project/preferences; inspect Window menu for `Fullscreen (Android safe)` and log `DRNAS_BLENDER_SAFE_FULLSCREEN_MENU installed=true`.
2. Toggle safe menu action, rotate the cube, move cursor near Y=0 and Y=height, and ensure PutImage continues advancing.
3. Use Android shortcut menu > Android Fullscreen ON/OFF. Verify top chrome disappears when ON, cursor and touch hit tests align in both modes.
4. Open/close Preferences and Render, ensure X and minimize remain accessible. Save/Open existing project from D:.
5. Export r56 GUI log and 20 s FPS sample. Compare r55 and r56. Do not publish Hotfix 1.1 before real-device acceptance.

Native Blender fullscreen from other hotkeys remains unsupported pending proper Wine/X11 protocol remediation. Never call that route in this device test.
# Blender r55 — private r50/r51 regression isolation

Based on r54 f43d4552a2ce23d0f7f50f4f40a9987f6a090d02. Package com.drnas.console, versionCode 123, same signing identity. No public release.

## Evidence

The user reported working windows after r50 (08:51 local), then first-input freezing after r51 (09:19). Compare r50 acdead7cf8bf94c8d4baee35712364a03c897a03 with r51 8cced56e79efcc94e11fca6e204fba1806926d43: r51 enabled runpy loading of drnas-window-layout.py from startup and refreshed it on existing installs. That script registers a Blender main-thread timer, enumerates HWNDs and synchronously calls ShowWindow, SetWindowLongPtrW and SetWindowPos. Vulkan and X11 renderer code were unchanged between these revisions.

The r54 trace shows last PutImage at 41222710, first post-startup button at 41223501, all 362 queued inputs completed, no retained pointer grab, focus 12582913, and X11 request count increasing to 20714. Last-opcode snapshots show 43 (GetInputFocus); these snapshots alone cannot prove every request is that opcode. No PRIMARY_FULLSCREEN completion marker appears. This makes the enabled timer the leading regression suspect, not a proven exact blocking call.

## Change

Remove only automatic loading of that controller from the generated startup script; retain atomic refresh so existing installs also receive the rollback. Keep the staged script inert, native Wine decorations, Android immersive mode, Vulkan, drivers, D: and input diagnostics. Main window may show its r50 frame/top gap again. No replacement fullscreen implementation in this isolation build.

## Acceptance

Fully exit the previous Blender session before launching the update. Leave startup idle for 15 seconds, then click menus, orbit the viewport, open/close Preferences and load/save a project. Export diagnostics. Confirm DRNAS_BLENDER_LAYOUT_DISABLED and continuing image updates after interaction. This is a private regression test, not an on-device verified fix.

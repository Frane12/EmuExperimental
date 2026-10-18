# Blender ARM64 private app preview (r28)

This draft updates `com.drnas.console` with the existing signing identity and
versionCode 94. It reuses the existing DRNAS Wine data. The launcher is labeled
Blender with the Blender icon; the regular DRNAS dashboard remains an internal
maintenance screen. This is an unofficial Wine presentation build, not a native
Android Blender port or a Blender Foundation release.

The APK embeds the pinned Blender 4.5.14 Windows ARM64 distribution. A new
installation initializes the bundled Wine environment automatically. First use
extracts Blender transactionally into C:\DRNAS\Apps\Blender-4.5.14, with at least
900 MiB of available storage required. Existing user installations elsewhere
in the prefix are left in place. The bundled copy has separate portable settings.

Launch requests fullscreen and the tested Vulkan backend; explicit per-app
backend settings take precedence. The Android display fills the screen. Startup
remains covered until the primary Blender window maps. The desktop animation
companion and game controls do not start for this profile. The r27 popup guard
is retained.

The translucent two-row shortcut panel starts at the bottom left. Drag its grip
to reposition it; its location and collapsed state persist. It groups Tab,
G/R/S, X/Y/Z, E/B/A, F3/Space, Shift/Ctrl/Alt, Esc/Enter/Delete and Undo/Save.
Shift/Ctrl/Alt latch for a following key or pointer interaction; a regular
shortcut, collapse, menu, or Activity pause releases all modifiers. The small
keyboard tab restores the panel. The keyboard button opens the full Android
IME, also available from the three-dot menu when the panel is collapsed.

Small pointer shapes render with their original aspect ratio and hotspot at a
bounded 16–28 physical pixels, independent of desktop stretch. Large brush
cursors retain their guest-coordinate size. No continuous desktop redraw is
added for pointer motion.

Use panel menu → Test performansi · 20 sekundi. Rotate and pan the same model
throughout the sample. Downloads/DRNAS/Blender-Performance-20s*.txt reports X11
read/decode/upload timings, display intervals (p50/p95/p99), transport volume,
Java heap endpoints and viewport/device metadata. These are X11/HWUI timings;
guest engine FPS and GPU Present timestamps are not measured. Repeat cold/warm
with the same scene, resolution, frame cap and thermal conditions for comparisons.

Validation: launcher arguments, cursor geometry/hotspot, transactional extraction
and popup order have JVM regression tests. Android CI compiles the UI and native
runtime, runs the complete JVM suite and verifies the permanent APK certificate.
Fullscreen behavior, actual shortcuts, keyboard and performance still require
device validation.

Icon source: https://github.com/blender/blender/blob/main/release/freedesktop/icons/scalable/apps/blender.svg
Blender licensing and distribution notices remain inside the original bundle.

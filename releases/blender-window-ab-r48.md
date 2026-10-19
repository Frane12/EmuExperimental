# DRNAS Blender ARM64 – r48 private A/B test (r38 window policy)

This build isolates the **r46 window handling changes** while preserving r47 features and graphics drivers. This is not published publicly and actual device behavior is not verified by CI.

## Experimental rollback

- Restore `--window-fullscreen` from the previously working r38 default, while honoring user-supplied window arguments.
- Do not run the experimental r46 `drnas-window-layout.py` Win32 geometry hook at Blender startup. D: file browser logic is preserved.
- Delete the persistent Blender-only Wine registry values `Managed=N` and `Decorated=N` added in r46, including on devices that already installed r46/r47. An independent one-time marker ensures this migration executes.
- Keep r41 direct Blender launch and all r47 graphics improvements, Turnip drivers, WineVulkan preflight, Vulkan-first-frame experiment, selected D: project directory, display profiles, and touch controls.

## Test on phone

1. Install r48 using the existing stable signing identity (package unchanged, versionCode 116).
2. Confirm Blender starts and the main window fits the screen.
3. Open Blender Render with F11, then Edit → Preferences. Check whether each auxiliary window stays visible, is independently sized, and can be closed/minimized.
4. Share the GUI log: look for `DRNAS_BLENDER_AB_R38_POLICY` and `DRNAS_X11_CRITICAL_STACK`.
5. Compare to the earlier r47 test. If still broken, isolate the r41 direct launch next.

**Caution:** This is an A/B diagnostic, not a verified final fix. Android normally prevents downgrading from versionCode 116; switching back to older APKs may need a version bump rather than uninstalling. Existing user projects/preferences remain unchanged, but the two per-app Wine registry overrides are deliberately removed.

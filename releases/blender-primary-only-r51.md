# Blender DRNAS r51 — PRIVATE primary-only fullscreen candidate

**Based on r50**, not public and not device-verified. Android package `com.drnas.console`; versionCode **119**; permanent signing identity preserved. Build only in the private EmuExperimental repository and stage as a private CI artifact/draft. Do **not** publish to Testturnip.

## User-requested behaviour

- Only the original Blender `GHOST_WindowClass` top-level HWND is made borderless and fitted to the X11 desktop (1280×720 or 960×544).
- Preferences, Render, file browser, tooltips and transient windows retain their r50 Wine-provided decorations, positions, X11 stacking and normal close/minimize/move behaviour. **No auxiliary HWND styling or resizing**.
- Track a stable primary HWND from the initial Blender window rather than applying a rule to every window with the title "Blender".
- Recheck only this primary geometry after .blend reload or external changes. When already correct, do not call the Win32 style/geometry setters again; poll slowly.
- Refresh the app-owned `drnas-startup.py` and `drnas-window-layout.py` on existing Blender installations, preserving user preferences, data, D: mapping and the verified Blender executable.
- Continue using r50's `Managed=N / Decorated=Y` per-app Wine setting (other Wine apps untouched). Avoid `--window-fullscreen` which previously affected auxiliary windows.
- Keep Vulkan, Adreno/Mali driver-selection paths, X11 renderer, GPU FPS instrumentation, and Android immersive presentation unchanged.

## Review of r50 log (2026-10-19 08:50) and code

| Subsystem | Evidence / finding | Action |
| --- | --- | --- |
| Blender startup | Primary X11 window mapped; secondary Preferences mapped; Android did not record a Java/native crash. | No unrelated startup rewrite. |
| X11 owner/stack | `DRNAS_X11_CRITICAL_TRANSIENT_REPAIRED` shows Preferences was reattached above its owner. | Preserve existing fallback; no retouching popups. |
| Window geometry | Main window `[-4,-4][1284,724]`, larger than 1280×720 root. Main frame likely creates border/offscreen area. | Remove frame only for tracked primary and place at (0,0). |
| Window policy registration | One-time r50 registry marker causes silent shortcut on subsequent runs. | Log a *cached* policy marker; do not re-import each time. |
| Legacy Blender layout script | `drnas-window-layout.py` was staged but no longer loaded in r48–r50. Previous experimental hook also mutated secondary windows. | Activate only after editing out auxiliary mutations. |
| Vulkan evidence | `DRNAS_TURNIP_AUTOPICK_SUCCESS` stages A840 driver; first Vulkan pixel remains **unverified** in the log. | No fabricated GPU verification. |
| OpenGL | `libGL.so.1` is absent on this Vulkan profile; Wine logs its GLX initialisation failure. | Keep Vulkan path; separately test GL profile if needed. |
| Audio | `mmdevapi:load_driver ... c0000135` indicates a Wine multimedia loader issue; pulse/audio output cannot be certified from this log alone. | Retain for isolated later investigation; no risky runtime rebuild. |
| Other Wine warnings | `nsi` bind errno 13, `winebth` service failure and missing Kerberos support logged; no direct failure of Blender mapped GUI demonstrated. | Monitor, don't suppress warnings by lying about success. |
| Legacy installers/DXVK | Session still checks optional offline redistributables and stages x64 DXVK before selecting ARM64 DXGI. These are potential startup/redundant-I/O cleanup candidates, but DXGI restoration depends on staged baseline. | **Do not remove yet:** change only after a separate A/B test. |
| Old markers/cache | Existing Wine-prefix markers and user configs persist across upgrades. Deleting them could rerun installers or break D: setup. | Retain intentional migration history and user data. |

## Verification scope

Pure Python controller tests cover multiple screen sizes, geometry repair, no repeated style setter for a stable main, restoration after loading a .blend, HWND reuse, no-app cases, and never touching Preferences / Render / owned transient. Java archive regression asserts the startup loader is present. CI also runs the existing Android unit tests and native/packaging checks.

**Not proved by CI:** Win32 `SetWindowLongPtrW` on this particular Wine ARM64 build; real Android screenshot quality, hardware Vulkan first Present, Preferences buttons, render-window controls, GPU FPS or sound.

## Phone acceptance after installing the signed private build

1. Open Blender and confirm only the main workspace covers the guest display edge-to-edge with no top gap. Check diagnostics for `DRNAS_BLENDER_PRIMARY_FULLSCREEN primary ...`.
2. Open **Edit → Preferences** and verify independent title bar, X/minimize, dragging and closing without Blender disappearing. Open Render (prefer **Render In: New Window**) and repeat.
3. Load/save a project on D:, navigate the 3D viewport, and confirm Vulkan/Eevee has no regression. Test a second file load for window geometry reapplication.
4. Export the GUI log. If setup fails, expect `DRNAS_BLENDER_PRIMARY_FULLSCREEN_SETUP_FAILED` or `DRNAS_BLENDER_WINDOW_LAYOUT_FAILED`; report rather than silently claiming fullscreen.

This is an isolated developer-review experiment. Keep all public releases unchanged until explicit approval.

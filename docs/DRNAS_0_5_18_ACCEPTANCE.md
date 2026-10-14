# DRNAS v0.5.18 — power LED, startup progress, Start/taskbar and relative touch

Based strictly on the on-device-verified v0.5.17 GUI base, with a separate `.labv0518` app ID.
Do not alter or remove the v0.5.17 LAB build.

## UI
- The first home control is `TURN ON WINDOWS` with a RED LED until isolated Wineboot completion is verified.
- `WINDOWS EXPLORER` is the second control and remains disabled until a verified boot-plan is present.
- The boot progress bar advances only at observed component/CLI/wineboot milestones; its percentages are phase indicators, NOT time estimates. It reaches 100 only with a successful Wineboot verification.
- App opening does not automatically start Wine or open the guest GUI.
- RAW logs stay private and are not rendered on the desktop.

## Wine/X11
- The default guest process remains `explorer.exe`, but virtual desktop is now `/desktop=shell,1280x720`.
  Wine's built-in `shell` desktop has the taskbar and Start menu enabled by default (per `programs/explorer/desktop.c` / `systray.c`). On-device verification is still required.
- Guest X11 size remains 1280x720, scaled uniformly to the physical device.
- Relative one-finger touchpad movement keeps cursor position on finger-down (no jump), tap gives left click, two-finger tap right click, hold and slow move initiates a drag.
- Cursor redraws dirty rectangles, rather than the full software X11 screen, to reduce pointer-triggered rendering work.
- Normal GUI starts Wine with `WINEDEBUG=-all`, avoiding the earlier high-volume X11/module tracing.

## Pass/reject checks (Poco F8 Ultra)
1. On launch: red LED, Explorer disabled; no automatic initialization.
2. Tap TURN ON WINDOWS: progress advances monotonically; LED stays red during initialization.
3. Verified Wineboot: green LED, 100% marker, Explorer enabled.
4. Tap WINDOWS EXPLORER: Wine shell appears at 1280x720; check taskbar and Start menu.
5. Slide finger: cursor moves relatively; no warp to finger-down. Tap selects at cursor, press-hold slides a window.
6. Open the R: mirror and a TXT file; verify the v0.5.17 working features remain functional.
7. Compare latency during cursor movement and Wine GUI startup against v0.5.17; don't claim an FPS gain without measurement.
8. On failure: LED stays red and progress must not reach 100%.

## Caveats
The native Wine shell taskbar and Start menu may rely on shell extensions not yet exercised by the previous winefile test. 
Actual on-device GUI responsiveness and Wine window mapping still need to be tested. Turnip A840 Vulkan ICD is not yet proven connected.

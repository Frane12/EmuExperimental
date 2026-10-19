# Blender r57 — private full-screen menu to Android host bridge

Based on r56 e3f1b08072aa475d278b0c9f215fbd1d82b869a3. VersionCode 125; `com.drnas.console` permanent stable-signed. **Not public; device acceptance is pending.**

## User feedback / rationale
- User confirms r56 Android compositor crop removed the grey title strip. The outstanding request is to make Blender's **Window > Toggle Window Fullscreen** actually operate.
- Native Wine/X11 fullscreen freezes Blender: prior r55 log shows input processed, X11 request count grows rapidly, `PutImage` stops. Native transition must not be triggered by our menu.

## Change
- Blender's Window menu now displays **Toggle Window Fullscreen (DRNAS)**, mapped to `drnas.safe_fullscreen`, *not* `wm.window_fullscreen_toggle`.
- On each click Blender writes an **atomic monotonic sequence** `r57:N` to the app-owned Wine C: Blender folder `drnas-host-fullscreen.signal`. No Window-manager Win32 calls, no background Python loops.
- Java polls the tiny signal on an Android HandlerThread every 350 ms, outside main/UI and X11 threads. It applies an odd number of toggles via the existing Android compositor crop and shared mouse/cursor transform. Duplicate reads do nothing. Previous session signal cleared when bridge starts.
- Keep D:, Vulkan, previously working input, existing Wine window management, separate Preferences/Render windows and keyboard dimensions. r56 safe crop starts ON so first toggle will turn it OFF (grey title strip briefly returns); subsequent toggle restores full crop.
- Native Blender F3/fullscreen shortcuts or add-ons invoking `wm.window_fullscreen_toggle` are **not** intercepted. Native Wine fullscreen remains unsupported.

## Acceptance on A840
1. Install over r56 without deleting app data. Check original Blender 4.5.14 ARM64 and Vulkan route.
2. Open Window > Toggle Window Fullscreen (DRNAS), click once and wait under 1 s. Observe Android grey strip return; repeat and confirm it disappears with no freeze.
3. Check cube orbit, Preferences, Render windows, cursor alignment at the very top, keyboard and D: Save/Open.
4. Export GUI log; confirm `DRNAS_BLENDER_SAFE_FULLSCREEN_REQUEST seq=N`, `DRNAS_BLENDER_FULLSCREEN_BRIDGE r57 seq=N`, and `DRNAS_BLENDER_HOST_FULLSCREEN r57`.
5. Do not publish until on-device evidence confirms both directions work.

# DRNAS Console 0.5.19

Based on the user-confirmed 0.5.18 desktop and original 0.5.16 Wine path.

Changes:
- Reset Wine directly below Turn on Windows, plus a desktop menu reset. Stops only the app-owned Wine/audio session, waits for cleanup, then opens a new desktop. Does not rerun the disposable wineboot template extraction or delete prefix/user data.
- Default desktop starts shell + winefile in one launch, using the same sequence that the user reports makes Start visible. 1280x720 and relative touch remain.
- X11 window damage rectangles are transformed from guest coordinates to physical view coordinates, including letterboxing. Invalidations are scheduled on animation frames. Software Canvas preserves existing shape drawing support. Verified GUI libraries are reused within the running app process.
- Wine's versioned and unversioned Vulkan library names resolve to the bundled Khronos ICD loader. Per-chip staged Turnip ICD environment is retained. An isolated native probe runs that loader with the same Wine environment and reports actual physical devices. A successful probe is not proof of Wine/DXVK rendering or Vulkan presentation through this software X server, which still lacks DRI3/Present integration.
- Pinned GameNative Android PulseAudio/AAudio binaries and modules are packaged inside APK; starts in foreground per desktop, private Unix socket, 40ms client latency preference, closes with Wine. Desktop remains usable if sound startup fails. No microphone permission or audio input.
- Original four-pane DRNAS launcher vector/adaptive icon.

CI checks: JVM suite, native compilation, pinned upstream blob/hash checks, APK assembly/signature, bundled assets. Device checks still required:
1. Turn on Windows, then Windows Explorer. Start/taskbar and file manager appear without another manual launch.
2. Move pointer/windows across all edges, including bottom taskbar, on differently shaped displays. Check repaint correctness and compare responsiveness to 0.5.18.
3. Reset repeatedly from main and desktop menu. One X11 server, one owned wineserver, one PulseAudio process; no bind errors. A test text file and settings persist.
4. Play Windows test sound via Wine configuration. Confirm speaker output, then exit/reset/background/return. No audio leaks after exit. Inspect hidden GUI log only if needed.
5. Inspect native Vulkan device probe for Turnip. Reject claims of GPU-accelerated games until an in-guest DXVK test and working X11 presentation path are demonstrated.
6. Confirm launcher icon in launcher, app info, and recents. Verify stable certificate compatibility; without persistent signing secrets this remains an isolated LAB package.

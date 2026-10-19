# Blender Hotfix 1.1 r52 — FIRST INPUT anti-hang (PRIVATE DEVICE TEST)

This is a **private, experimental testing build**, not a public release. It has not yet been confirmed to resolve the freeze on actual Android hardware.

## Fixed / instrumented
- Blender-only Android touch, mouse, overlay LMB/RMB, and keyboard inputs now enter one bounded FIFO input worker rather than waiting for the global X11 server monitor on Android's UI thread.
- Incoming MotionEvents are copied and recycled after dispatch to preserve lifetime and press/release order.
- Detect input stalls after 2.5 seconds. New logs: DRNAS_X11_INPUT_ASYNC, _QUEUED, _DONE, _SLOW, _STALL and _QUEUE_OVERFLOW; capture blocked worker stack.
- Prevent X11 input-worker shutdown from synchronously blocking the Android UI. All non-Blender applications retain previous event path.
- Fullscreen policy, Android touchpad, Blender shortcut buttons, Wine, graphics pipelines, stored files and render settings are preserved from r51.

## Samsung Xclipse 940 (Exynos 2400, AMD RDNA 3)
- Recognize the Samsung Xclipse 940 display/GPU name in Blender's automatic Vulkan selection.
- Select the phone's **Samsung firmware-provided Vulkan driver (system ICD)**, not Turnip (Adreno-only) and not PanVK (Mali-only).
- An independent redistributable Xclipse 940 driver is **not bundled**; no unsupported claims of verified Vulkan or Blender compatibility.
- If the Samsung system Vulkan driver does not expose the required Wine/WSI features, Blender may still fail on that phone.

## Device test
1. Install over r51/r50 with the same permanent signing certificate (versionCode 120). Keep a backup of projects.
2. Launch Blender, click the File menu and the default cube; then test virtual LMB/RMB, Tab, and Esc.
3. Export GUI diagnostics. If click still blocks Blender, check DRNAS_X11_INPUT_STALL and worker stack; check if the Android menu remains usable.
4. Test separately on Xclipse 940 device with its stock firmware driver. No Samsung hardware test was performed in CI.

This build is **not confirmed hang-free**. GPU enumeration and a mapped Blender window are not proof of successful Vulkan first-present or working interactive GUI.

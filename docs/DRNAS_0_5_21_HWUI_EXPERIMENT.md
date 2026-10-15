# DRNAS Console v0.5.21 — GPU Canvas / independent cursor experiment

Status: **unverified on Android device**. Do not claim a working Direct3D adapter or Vulkan game renderer.

## What was learned from Winlator / Bannerlator
- Upstream Winlator uses **GLSurfaceView + GLRenderer**, not an Android software Canvas, for window compositor and cursor (see `brunodev85/winlator-app/app/src/main/java/com/winlator/widget/XServerView.java`, `renderer/GLRenderer.java`).
- Crucially, Winlator's **DRI3Extension + PresentExtension + GPUImage** use ancillary FDs, shared graphic buffers, EGLImage/AHardwareBuffer, plus native glue to present DXVK output. This is not provided by merely making Canvas hardware accelerated.
- Bannerlator extends that route with VulkanRenderer and NativeRenderer, but depends on the same core buffer exchange infrastructure.
- DRNAS legacy Java X11 has none of the above DRI3/Present/FD transport and must NOT advertise fake extensions.

## Implemented now (reversible)
1. Remove the forced `LAYER_TYPE_SOFTWARE` on X11 screen: Android HWUI may composite existing CPU bitmaps with the GPU. Diagnostics record actual `canvas.isHardwareAccelerated()`.
2. Move mouse cursor to its **own View**, avoiding full X11 bitmap redrawing on every pointer move. Preserve 1280x720 guest coordinates and letterboxing.
3. On-screen menu adds a CPU/GPU Canvas fallback, without rebuilding the prefix.
4. Prefer native DXVK `d3d9/dxgi/d3d11/d3d10core` DLL overrides; report both 32-bit and 64-bit staged DLL presence and enable private DXVK logs.
5. Probe native Vulkan even without explicit Turnip selection, never equating ICD env vars with GPU activation.

## NOT implemented
- X11 DRI3/Present server request handlers; proper ancillary-fd delivery and buffer lifecycle.
- EGLImage/AHardwareBuffer import, Winlator native GPUImage path, GPU game-frame display.
- Validation of Vulkan device use by DXVK in the Windows guest or non-empty Crysis adapter ID.

## Acceptance / rejection tests
A. Android GUI boots and Windows file manager works; drag cursor continuously and compare to v0.5.20. Test keyboard, touchpad, reset, 1280x720 aspect, orientation and CPU Canvas fallback.
B. In hidden GUI log, require `Android X11 Canvas hardwareAccelerated=true`. False means GPU compositing has not been activated.
C. Confirm **x86** `syswow64/d3d9.dll` and x64 `system32/d3d9.dll` exist. Inspect private DXVK logs for actual Vulkan adapters; missing device or missing DLL rejects Direct3D GPU support.
D. Run GPUInfo.exe and Crysis only after B and C. An empty adapter remains a failure; do **not** call this build hardware Vulkan WSI-ready.
E. Do not cherry-pick upstream XServer classes alone: their native fd transport and buffer manager must be ported together with license review and unit/instrumentation tests.

## Citations
- https://github.com/brunodev85/winlator-app/tree/main/app/src/main/java/com/winlator/renderer
- https://github.com/brunodev85/winlator-app/blob/main/app/src/main/java/com/winlator/xserver/extensions/DRI3Extension.java
- https://github.com/brunodev85/winlator-app/blob/main/app/src/main/java/com/winlator/xserver/extensions/PresentExtension.java
- https://github.com/The412Banner/Bannerlator

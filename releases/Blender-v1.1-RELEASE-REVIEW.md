# Blender v1.1 — private approval checklist

Do not publish without the maintainer's explicit approval.

- [x] Clean user-facing naming: Blender v1.1; APK/ZIP and release title contain no R or development revision.
- [x] Automatic route inspection: Adreno recognized Turnip candidates, selected Mali PanVK candidates, Xclipse system firmware Vulkan only, other hardware system fallback.
- [x] Cross-family unit tests included; candidate pinning and hardware version gates retained.
- [x] Quiet release default: no input stack watchdog, per-input state tracing, continuous X11 frame auditing or PanVK debug; essential failure reporting preserved.
- [ ] GitHub CI signed APK + test run successful.
- [ ] Install candidate on A840; test smooth zoom, fullscreen, popups, files, render, keyboard and Vulkan; assess frametime.
- [ ] Test an Xclipse device and Mali device where possible. Until hardware validated, describe support as experimental.
- [ ] Confirm every third-party binary license, notices and corresponding-source obligations are satisfied prior to redistribution (Blender GPL, Wine LGPL, Mesa, DXVK, FEX and others).
- [ ] Explicit written approval to publish draft.

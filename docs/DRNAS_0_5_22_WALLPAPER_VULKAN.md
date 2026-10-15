# DRNAS Console 0.5.22 — one-time Wine wallpaper and Vulkan GPU classification

User device feedback from v0.5.21: cursor is dramatically more fluid; Wine desktop wallpaper is absent; GPUInfo.exe still cannot enumerate the Windows video adapter.

**Architecture:** HWUI compositor improvements in v0.5.21 accelerate Android Canvas and independent cursor only. Guest Direct3D device enumeration still requires Wine Vulkan/Turnip plus X11 DRI3/Present and native FD / AHardwareBuffer handling. No fake GPU IDs or fake DRI3 extensions.

## This iteration

- Create an original 1280x720 24-bit BMP under `C:\users\xuser\Pictures` once.
- Import Wine registry HKCU/Control Panel/Desktop wallpaper settings through *guest* `reg.exe` once. Import is bounded; failure preserves session start. It is **not proof** that Wine's PaintDesktop draws a bitmap: some Wine virtual desktops paint solid background only.
- Native Vulkan device probe now returns success when any real native Vulkan physical device is enumerated. A non-Turnip Vulkan device is explicitly NOT reported as verified Turnip. Windows GPUInfo and DXVK device creation still require separate A/B testing.
- Preserve independent cursor HWUI logic and its fallbacks.

## Device acceptance

1. Verify the cursor remains fluid and desktop/menu still works. Check user profile and R drive have not regressed.
2. Inspect whether a blue abstract wallpaper appears. If only a solid color appears, treat as Wine PaintDesktop limitation, not a successful wallpaper image fix. Look for `[wallpaper]` in exported hidden log.
3. Inspect the `[Wine Vulkan]` physical GPU device line and `DRNAS_NATIVE_VULKAN_DEVICE_PRESENT__TURNIP_NOT_VERIFIED` vs `DRNAS_TURNIP_DEVICE_VERIFIED`.
4. Run GPUInfo; its error remains a legitimate failure, never mask it with spoofing.
5. Follow-up engineering track: integrate DRI3/Present+fd graphics buffer protocol with EGL/AHardwareBuffer from Winlator's tested architecture; independently enable Bionic Turnip with an actual supported Vulkan loader/driver injection path.

Sources: Wine's `programs/explorer/desktop.c` paints desktop by `PaintDesktop`; historical WineHQ reports virtual desktops supporting only background color.

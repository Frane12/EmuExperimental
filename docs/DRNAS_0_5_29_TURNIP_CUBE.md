# v0.5.29 Vulkan / Turnip integration experiment

Based on v0.5.28 (`dd104c92671e2b88d407d696fa8008d1d28de35a`). Picture/audio
were reported working; stable Direct3D presentation was not established.

The old build-time release filter excluded every A830/A840 driver because they
are published prereleases. Bundle an explicit SHA-256 pinned set instead:
A810 S Ultimate v1.0, A830 S1 Smart, A840 NeuroFlow H2.8 TESTING ONLY.
Only an exact detected GPU selects a bundled variant. This is not a claim that
the A840 experimental driver is stable. No drafts are fetched.

Wine Vulkan still uses the Khronos loader -> Mesa X11 wrapper -> AdrenoTools
backend. A raw Android Turnip ICD cannot replace the X11 wrapper. The native
enumeration probe now rejects a non-Turnip driver ID when Turnip was requested
(exit 12), rather than reporting system enumeration as success.

Winlator's upstream menu names `Z:/opt/apps/TestD3D.exe` as “Test Direct3D”:
https://github.com/brunodev85/winlator-app/blob/a030f552f452158a2db64fdb32b490fa19c0b48d/app/src/main/assets/wine_startmenu.json

DRNAS builds its own equivalent D3D9 colored cube from `tools/graphics-tests/TestD3D.c`
using MinGW. It uses HAL hardware vertex processing, a windowed 800x600 swapchain,
vsync, and no D3DX. It reports initialization/Present HRESULT failures, adapter and
FPS. The x86 PE intentionally exercises FEX WOW64 + guest DXVK d3d9.dll.
This is an independent source implementation, not a copied Winlator executable.
Its SHA-256 is checked before installation into C:\DRNAS\Graphics. A standard
MS-SHLLINK places it under Start > Programs > System Tools.

The Android desktop ⋮ menu also offers “Vulkan: test prikaza”. It explicitly
tests native X11 surface/device/swapchain/clear/present using the same driver
environment, off the UI thread, with a 15 second process limit. Desktop startup
still enumerates only. Passing native presentation does not prove guest D3D.

X11 replies now flush directly on their owning client thread (instead of creating
and joining a new thread for every flush). Client cleanup executes in finally;
protocol exceptions are recorded in Android logs. These changes do not claim to
resolve the prior XIO failure. DRI3/Present fd passing remains unimplemented;
the current wrapper uses GPU rendering plus CPU readback / X11 PutImage.

## Device acceptance

1. Verify log selects the right GPU package and prints DRNAS_TURNIP_DEVICE_VERIFIED.
2. Run the explicit Vulkan presentation test; export the log on failure.
3. Start the D3D cube; confirm visible rotating geometry, adapter and stable FPS.
4. Export logs, including TestD3D_d3d9.log, if it fails or disappears.
5. Only after the cube works, test Crysis windowed DX9, then DX10.

CI build/test success alone cannot establish GPU rendering on an Android device.

# DRNAS Console v0.5.6 — embedded X11 Wine GUI experiment

Device v0.5.5 session 2026-10-14 17:54 confirms Wineboot completion, independent Windows CMD exit 0 and marker, and all three valid registries with changed content. GUI was not connected in that build.

## Implementation

Embedded Java X11 server from nwrkbiz/android-xserver at a pinned commit; original MIT notice included in APK assets/licenses/android-xserver-MIT.txt. Source origin: https://github.com/nwrkbiz/android-xserver . Our adaptations: loopback-only TCP 6012, application R import, 24-bit TrueColor with 32-bit XRGB transport, depth-1 pixmap format, matching PutImage/GetImage serialization, bounded mapping and Canvas draw diagnostics.

The new landscape Wine GUI activity starts the X server after its Android View is sized, verifies a complete X11 protocol 11 handshake, and reuses the proven disposable Wineboot prefix. Existing v0.5.5 boot-plan files are supported. It prestarts the verified Bionic wineserver with the same NLS shim and launches explorer.exe /desktop=DRNAS,1024x768 winecfg.exe through the scoped Wine child-exec relay. Wine X11 trace and component inventory are captured in separate latest-wine-gui-raw.txt; GUI TXT export captures a consistent snapshot while running. Touch clicks and keyboard input are handled by the embedded X11 View.

Session lasts at most five minutes and can be stopped with Stop/Back. Activity destruction closes its X server and app-owned Wine processes. No additional app, root, external storage permission or custom Vulkan loader is required for the Canvas display. Internet permission is used only for this local TCP socket.

## Device acceptance

Install with the same permanent key. If boot has not already passed, run isolated Wineboot. Tap Open Wine GUI (embedded X11). Pass requires protocol handshake, mapped client window and Canvas draw plus visible usable Wine configuration dialog. Launcher exit 0 alone is not pass. If blank, Export GUI TXT while the session is open; inspect missing winex11.so/libX11 dependencies or X11 protocol errors. XRENDER/MIT-SHM/GLX/Vulkan are not implemented by this small server. Actual GUI compatibility must be tested on device; this is a first GDI display experiment, not a gaming display backend.

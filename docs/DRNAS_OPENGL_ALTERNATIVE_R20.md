# DRNAS preAlpha 0.1 r20 — independent OpenGL ES and Wine EGL experiment

## Goal

Explore OpenGL without waiting for GLX/DRI3 implementation in the
custom TCP/Canvas X server, while preserving the successful Vulkan/DXVK
desktop. **This does not yet implement Windows OpenGL.**

## Implemented

1. Standalone isolated native Android EGL + GLES 3.0 probe built by the
   Android NDK into `libdrnas_gles_probe.so`. It creates a real ES3 pbuffer
   context, executes a GPU clear and reads back a pixel, checks the
   result, prints the actual GL_RENDERER / version and cleans up.
2. `Wine desktop → Options → OpenGL ES 3 GPU TEST`: performs that probe
   independently of Wine, GTK, X11, DXVK or any imported WCP. Output goes
   to the usual shareable Wine GUI TXT log.
3. Attempt atomic dependency staging of *real* ARM64 Bionic `libEGL.so.1`
   and `libOpenGL.so.0` with their complete ELF dependencies from the same
   pinned GameNative rootfs. If either is unavailable, do not spoof the
   missing library or make the existing Wine desktop unusable.
4. Startup logs `DRNAS_WINE_EGL_CANDIDATE` plus missing/experimental status.
   Wine 11 uses EGL by default when appropriate OpenGL libraries load.

## What is **not** completed

- A real OpenGL (WGL) context in Windows/Wine. OpenGL ES is a distinct API:
  mapping GLES3 symbols to `libGL.so.1` is invalid, especially for Blender.
- The embedded X11 server lacks GLX and native EGL-compatible X11
  presentation; staging `libEGL.so.1` doesn't automatically implement these.
- End-to-end Blender OpenGL 4.3, Wine EGL window surface, swap/present or
  first-frame GPU display.
- Portable GL core/Zink support for Wine is still conditional on obtaining
  compatible, licensed Bionic Mesa EGL/OpenGL and window presentation.

## Practical device check

Open Options and launch `OpenGL ES 3 GPU TEST`. Export TXT and verify
`DRNAS_GLES_DEVICE`, `DRNAS_GLES_READBACK`, `DRNAS_GLES_RESULT PASS`.
A PASS establishes native Android GLES (GPU renderer shown) **only**.
Next inspect `DRNAS_WINE_EGL_CANDIDATE` and the Wine-native probe.
Do not interpret the Android GPU test as evidence Blender now supports WGL.

No user settings, Wine DLLs, existing prefix data or upstream driver are
replaced by this experiment.

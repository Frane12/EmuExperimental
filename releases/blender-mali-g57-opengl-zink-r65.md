# Blender Mali-G57 MC2 · Xiaomi 24117RN76E · OpenGL/Zink LAB r65 (PRIVATE)

**Scope:** isolated diagnostic APK, not a public release, not a verified Blender GPU fix.

## User device evidence
The DRNAS Blender v1.1 (versionCode 129) log for 24117RN76E showed:
- Blender installed 100%, the guest main window mapped successfully.
- Default requested Vulkan/ARM64 PE path did not verify any pixels.
- Wine emitted `Failed to load libGL: libGL.so.1 not found` when it tried the GLX fallback.
- The app showed `Blender - Unsupported Graphics Card Configuration`.

## Experiment
Reuse the pinned, SHA checked **Bionic Mesa libEGL / libGL / libgallium Zink** runtime already in the DRNAS source (see `tools/package_opengl_runtime.py`), and launch the stock `auto` Blender profile with `--gpu-backend opengl` and Wine 11 EGL. This does **not** enable GLX on our Java X server. Use only the Android native system Mali Vulkan ICD; **never inject Qualcomm Turnip** or G52-specific PanVK.

Only Xiaomi 24117RN76E with Mali-G57 (or temporarily unavailable Vulkan renderer name) is rerouted. An explicit profile/backend argument wins. Keep the main installed Blender v1.1 unmodified: this experiment uses `com.drnas.console.g57lab`, versionCode 132, separate storage and signing-verified draft APK.

## Minimum acceptance checks
1. Record markers `DRNAS_G57_OPENGL_LAB`, `DRNAS_OPENGL_MESA_BUNDLE_VERIFIED`, `DRNAS_OPENGL_EGL_ROUTE_REQUESTED`.
2. Distinguish `dlopen(libEGL.so.1)` succeeding from `eglInitialize` succeeding: neither guarantees the latter. Check EGL display/window/native WSI integration.
3. Query actual **desktop OpenGL >= 4.3**, `GL_ARB_shader_draw_parameters` and `GL_ARB_clip_control` (not just OpenGL ES 3.2).
4. Render a visible Blender default viewport and rotate the cube; create and save .blend project.
5. Record the first successful presented frame, frame time and on-device crash/export logs. A compiled APK or mapped guest window alone is NOT a pass.
6. If OpenGL fails, test explicit `--gpu-backend vulkan` to recover the original diagnostic path; never overwrite the installed public version.

## Remaining engineering risk
Java X server does not yet implement GLX/DRI3/Present acceleration. Wine 11's EGL path and Mesa Zink must still interoperate with our custom X11 windowing and Mali Vulkan WSI. Zink GL version and extensions depend on selected Vulkan ICD. The experimental open PanVK Android Mali-G57 projects are not considered drop-in GPU fixes without independent device tests and hash-verified package integration.

Sources:
- Mesa Zink: https://docs.mesa3d.org/drivers/zink.html
- Blender 4.5 hardware requirements: https://www.blender.org/download/requirements/
- Existing pinned Mesa bundle in DRNAS: tools/package_opengl_runtime.py

**Do not promote or describe as Mali-G57 support until the first real rendered frame is proven.**

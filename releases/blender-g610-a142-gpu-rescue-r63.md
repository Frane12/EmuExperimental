# Nothing Phone (2a) A142 · Mali-G610 MC4 · GPU rescue r63 (PRIVATE DRAFT)

**Experiment only. No public release, no verified GPU success, no new driver claimed.**

## Observed user evidence
- Blender v1.1 (versionCode 129) on **Nothing A142 / Android 16**.
- Wine log shows `Blender - Unsupported Graphics Card Configuration`; unrelated missing `libGL.so.1` is from the default Vulkan-only launcher (host OpenGL is not staged there).
- A142 is the Nothing Phone (2a); published device data identifies **MediaTek Dimensity 7200 Pro / Mali-G610 MC4**. Mali-G610 must never get Adreno Turnip or the older Mali-G52/G72/G76 PanVK experiments.
- Existing first-Present preflight checks the **native** Vulkan/X11 path, which is not a substitute for Blender's Windows PE / Wine Vulkan adapter and feature check.

## Isolated experiment
- On this *specific* phone, only the stock `auto` Blender profile requests `--gpu-backend opengl`, backed by the existing separately SHA-verified Mesa EGL/Zink runtime. Wine uses `WINE_USE_EGL=1`; Mesa remains on the device system Vulkan ICD.
- Explicit `--gpu-backend` launch flags or graphics profile overrides always win.
- Adreno and all other devices remain on the unchanged Vulkan path.
- The APK gets a distinct application ID `com.drnas.console.g610lab`, label `Blender G610 LAB`, and versionCode 131. It can coexist with the regular Blender app without overwriting its private prefix/project configuration.
- Exports log marker `DRNAS_A142_GPU_RESCUE`. Ask tester to send the complete log and whether a real Blender viewport appears.

## Validation
1. Preserve the original Blender app; install LAB alongside it and select a test D: directory.
2. Verify log shows `DRNAS_A142_GPU_RESCUE selected=EGL_ZINK_OPENGL`, `DRNAS_OPENGL_MESA_BUNDLE_VERIFIED`, `DRNAS_OPENGL_EGL_ROUTE_REQUESTED`.
3. Check whether Wine successfully loads EGL libraries, creates an OpenGL 4.3+ context, displays a real Blender viewport and can render a test cube. These results cannot be inferred from a passed build or Vulkan enumeration.
4. On this device, a launcher override `--gpu-backend vulkan` should suppress rescue and keep the previous Vulkan route (for A/B troubleshooting).
5. If EGL fails or hangs, export full logs. Do **not** mislabel build completion as a GPU fix.

## Known limitation
The DRNAS Java X11 server does not implement GLX/DRI3. The separate Wine EGL route may still fail on this model; a true native GPU compatibility fix may need additional Wine/Mesa WSI and Vulkan feature work. No universal Mali-G610 PanVK driver is bundled by this experiment.

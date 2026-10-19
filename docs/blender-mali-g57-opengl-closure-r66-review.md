# Review r66 — Xiaomi Mali-G57 / OpenGL EGL-Zink dependency closure (PRIVATE)

**Boundary:** source review and no-build Linux preflight only. No Android APK compile, signing, public release or private APK draft was requested or started for r66. Preserve the existing r65 LAB and public Blender.

## Actual r65 evidence

User's screenshot (22:43, Xiaomi model 24117RN76E) shows:
- `DRNAS_BLENDER_VULKAN_BRIDGE_MODE requested=false selectedOpenGL=true`: the r65 switch to OpenGL executed.
- Wine `egl_init` cannot load `libEGL.so.1`, because a transitive `libxcb-xfixes.so` library is missing in the runtime namespace.
- Wine `X11DRV_OpenGLInit` cannot load `libGL.so.1`, because transitive `libxcb-glx.so` is missing.
- Then Blender opens an **Unsupported Graphics Card Configuration** dialog, rather than a usable viewport.
- These are dynamic-loader errors; they are *not* proof the Mali GPU lacks necessary Vulkan/OpenGL capability.

## r66 proposed changes

1. `tools/mesa_bionic_closure.py`: pin and verify the same 175 MiB GameNative Bionic rootfs used by the GUI runtime, enumerate top-level native ELF files/safe symlink aliases, and recursively resolve DT_NEEDED; reject unavailable/unsafe/oversized required libraries.
2. `tools/package_opengl_runtime.py`: continue checking the six immutable upstream Bannerlator Git blobs, then add all non-Android-platform dependencies from the verified Bionic rootfs. Include deterministic manifest with each file's SHA-256, provenance and DT_NEEDED. Missing libraries fail the package step, never masquerade as success.
3. `WineOpenGlRuntime.java`: install `opengl-runtime-v2.zip`, require manifest closure proof and `libxcb-glx.so`, `libxcb-xfixes.so` and `libX11-xcb.so`; fail before Wine if any file/hash is inconsistent.
4. `android-app/app/build.gradle.kts`: keep `com.drnas.console.g57lab` with versionCode 133. Avoid touching `com.drnas.console` or user projects.
5. `.github/workflows/build-android.yml`: prepare a distinct **future** r66 private signed APK draft branch condition, without adding r66 to the push build trigger list. Every APK build still requires explicit user approval. 
6. `tests/test_mesa_bionic_closure.py`: test transitive closure, missing libraries, malicious names, symlinks/cycles and pin check. Preserve license notices for Mesa/XCB libraries.
7. `.github/workflows/preflight-r66-mesa-xcb.yml`: *no APK compile*; reconstruct/pin Mesa & Bionic library bundle, verify checksums and full dependency manifest, without uploading build artifacts.

## Verified no-build preflight result

[GitHub Actions preflight 39323547737](https://github.com/Frane12/EmuExperimental/actions/runs/39323547737): **SUCCESS**.

```text
DRNAS_OPENGL_R66_CLOSURE_OK 28 ELF libraries; zip bytes 8138731
DRNAS_OPENGL_R66_XCB_PRESENT libxcb-glx.so libxcb-xfixes.so libX11-xcb.so
R66_MESA_XCB_CLOSURE_PREFLIGHT_PASSED libs= 28
NO APK build, no signing, no release, no Android runtime/render validation
```

Two earlier *preflight-only* attempts failed because imagefs contains unrelated `libc.so` symlinks outside usr/lib and an oversized librsvg ELF. r66 now ignores unrelated entries and still fails closed if they become required dependencies.

## Risks still open / gates to acceptance

- Host Android linker may reject another indirect dependency or ABI mismatch on the actual user's device; the CI closure proves DT_NEEDED availability only.
- Wine 11 `WINE_USE_EGL=1` still needs a working EGL display/context, desktop OpenGL 4.3+ and Blender-required extensions.
- DRNAS Java X11 server does not fully implement GLX/DRI3/Present. Supplying libxcb-glx cannot magically implement these server extensions. A failed `eglInitialize`, bad WSI/presentation or GPU feature rejection would need a separate engineering change.
- A future **user-approved private r66 APK** must install alongside public Blender; confirm manifest identifies `com.drnas.console.g57lab` versionCode 133, certificate fingerprint, SHA-256 and README.
- An on-device PASS requires real first Blender viewport pixels, cube rotation, saving a .blend file, and basic render. Build success is not hardware compatibility success.

**Review status:** source and Linux dependency closure ready for user review. **Do not trigger the APK workflow or publish any release until the user explicitly approves after seeing this review.**

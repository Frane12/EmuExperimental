# DRNAS Console v0.4: first real Android NDK host

Status: native ARM64 **Bionic JNI library**, not a Wine emulator.

## What the APK can actually do

- Build and package `lib/arm64-v8a/libdrnas_host.so` using Android NDK r27c / CMake 3.22.1.
- Load that shared library in the app process through `System.loadLibrary("drnas_host")`.
- Run the NDK `uname/getpid/getuid` host preflight (not a shell impersonating Wine).
- Read, but **never execute**, the user's staged `bin/wine` ELF entry. Distinguishes 64-bit AArch64 ELF, 32-bit/other ELF and `#!` wrapper scripts. For AArch64 ELF it reads the `PT_INTERP` requested dynamic loader.
- `dlopen("libvulkan.so")` and query *Android system Vulkan*, distinctly from the *staged but inactive* Turnip Vulkan driver.
- Preserve the already working Proton/FEX/DXVK/Turnip content-addressed store, safe prefix assembly and machine-readable `boot-plan.json`.

## Explicitly *not implemented*

- Invoking the glibc ARM64 `ld-linux-aarch64.so.1` dynamic linker and finding matched glibc dependencies in a sandboxed rootfs.
- A working Wine `wineserver`, `wineboot --init`, Winlator-equivalent guest environment, X11/Wayland server, PulseAudio/PipeWire or camera/input bridges.
- Driver Vulkan ICD loading of third-party `libvulkan_freedreno.so` in the Wine guest.
- Executing third-party ELF from writable app data. Android 10+ forbids executing arbitrary files written into the app's writable sandbox under normal target SDK assumptions. Do not chmod +x or fork arbitrary archive content.
- Windows execution, gaming, FPS, driver compatibility or actual FEX translation. FEX `.dll` bridges are not independent executables.

## On-device test protocol

1. **Update the APK in place** to preserve existing private vault and Wine prefix. Do not uninstall just to update.
2. Press **Start container (experimental)**. Existing staged content should be reused; the private prefix should not be deleted.
3. Confirm that `[ndk] DRNAS_NATIVE_HOST_OK ... arch=aarch64` appears. The `getpid/uid` prove actual ARM64 Bionic code executed within the app.
4. Record `[ndk] Wine ELF64 AArch64 ... PT_INTERP=...`, or `Wine entry is a script`. This tells us which guest loader and script runtime a future implementation needs.
5. Record `[ndk] Android system Vulkan loader: API ... custom Turnip NOT loaded`.
6. The boot status must remain `NDK HOST READY / GLIBC GUEST LOADER MISSING`. Anything claiming Wine is running is a bug.

## Next integration milestone

Build a **separately packaged, pinned, auditable glibc guest rootfs/loader** compatible with the actual Proton WCP. Verify its ELF interpreter, architecture, dynamic libraries and security before attempting a minimal `wine --version` or `wineboot --init`. At this milestone only, add explicit guest-execution state and watchdog/cancellation, then X server and Vulkan ICD wiring. User-supplied third-party components remain independent and are not rehosted in this repository.

Build from `android-app/`:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep 'lib/arm64-v8a/libdrnas_host.so'
```

GitHub Actions builds with Java 17, NDK 27.2.12479018, CMake 3.22.1, AGP 8.7.3. JNI source intentionally does not link against glibc or Wine.

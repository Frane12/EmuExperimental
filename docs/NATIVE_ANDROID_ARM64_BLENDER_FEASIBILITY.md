# DRNAS Blender Native — Android ARM64 feasibility (phase 0)

Starting baseline: DRNAS EMU LAB v1, commit `73d3e4be734a39effad9ec22f8635ba2e3da5873`.
This branch is independent of the working reference branch and main.

## Precise target
Produce an **Android ARM64 ELF** executable or native library from Blender sources, runnable on an actual Android ARM64 device, **without Wine, FEX or a Windows PE executable**. Initially headless only.

## Acceptance gates
1. Record upstream Blender source revision, Android NDK version, host toolchain and third-party dependency versions.
2. Audit Blender CMake platform assumptions and dependencies for Android/Bionic. Identify blockers before attempting full compilation.
3. Compile a minimal Android/Bionic proof of concept with NDK clang; inspect with `file` and `readelf -h` to confirm AArch64 ELF.
4. Attempt a reproducible **headless** Blender build, disabling GUI and unsupported optional modules where possible. Do not call a generic ARM64 Linux build an Android build.
5. On the actual A840: start process, execute minimal Python scene script, save a .blend file and reopen it. Capture logs and exit status.
6. Only after gates 1–5, evaluate Vulkan GUI, input and window integration.

## Boundaries
- Do not modify `main`, the working `reference/drnas-emu-lab-v1-working-a840-20261020` branch or published APKs.
- No claims of a working native Blender before an Android-device test.
- Keep licensing and redistribution obligations for Blender and third-party libraries.
- Avoid unpinned downloads and avoid placing credentials or signing material in source control.
- Review technical changes and build plan before triggering a long CI compile.

## Expected first deliverable
A feasibility report listing each blocking dependency, needed patches, reproducible toolchain steps, and a minimal smoke-test plan. Native Blender GUI is explicitly out of scope for phase 0.

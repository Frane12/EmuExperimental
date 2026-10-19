# r64 Windows ARM64 CMake / Clang fix — private review, 19 Oct 2026

**Status:** proposed and statically reviewed; **NOT EXECUTED**. No new
compilation workflow or APK has been started by this review.

## Evidence (last failed compile)

GitHub run [39321211578](https://github.com/Frane12/EmuExperimental/actions/runs/39321211578):
- Exact Blender 4.5.14 source and our Vulkan C++ patch applied successfully.
- Blender `lib/windows_arm64` submodule and LFS payloads fetched successfully.
- Host VS2022 picked correct x64->ARM64 compiler environment, yet
  CMake's platform code selected `lib/windows_x64`.
- `configure_msbuild.cmd` already passed `-A ARM64` to Visual Studio,
  so `-A` alone did not suffice to select Blender's ARM64 library path.
- CMake resolved Visual Studio's **Clang 19.1.5** instead of the
  external **Clang 20.1.8** observed in toolchain preflight.
- Build failed **during CMake configure**, before compiling any C++ sources.
  Neither Vulkan patch correctness nor Mali rendering is validated.

## Proposed fix, deliberately *inert*

Prepared complete next-attempt draft at:

`.github/workflow-drafts/build-blender-4514-mali-arm64-r64-v2-REVIEW-ONLY.yml`

It is stored outside `.github/workflows` so that saving and discussing it
**does not trigger another build**. To attempt it later, copy reviewed changes
into `.github/workflows/build-blender-4514-mali-arm64-r64.yml` with explicit
user approval for the new compilation.

Main changes:
1. Keep the earlier VS2022 host correction:
   `vcvarsall x64_arm64` for an x64 runner targeting ARM64.
2. In the **temporary upstream checkout only**, modify the specific
   `configure_msbuild.cmd` ARM64 branch to supply
   `-DCMAKE_SYSTEM_PROCESSOR:STRING=ARM64` and
   `-DLIBDIR:PATH=".../lib/windows_arm64"`. `make.bat` resets
   `BUILD_CMAKE_ARGS`, so simply setting environment flags would be ignored.
3. Temporarily add **fail-closed CMake assertions** ahead of the Blender
   library selection: `CMAKE_VS_PLATFORM_NAME=ARM64`,
   `CMAKE_SYSTEM_PROCESSOR=ARM64`, and `LIBDIR` suffix
   `windows_arm64`. If Blender's platform logic overrides
   `CMAKE_SYSTEM_PROCESSOR`, terminate rather than silently link x64.
4. Pin the independent external `clang-cl.exe` version >=20 before VS
   changes PATH, using Blender's supported `LLVM_EXE` override. VS2022's
   bundled LLVM 19 may remain present but should not be used.
5. Check **actual** `CMakeCache.txt` generator platform and LIBDIR,
   `CMakeCXXCompiler.cmake` version >=20 and library submodule metadata,
   before compiling any source.

## Read-only review completed

Checked the **exact upstream v4.5.14** versions of
`build_files/windows/configure_msbuild.cmd`,
`build_files/cmake/platform/platform_win32.cmake` and
`build_files/windows/find_llvm.cmd`.

- Both batch replacement anchors occur **once** (ARM64 `-A` branch and
  final `%PYDEBUG_CMAKE_ARGS%`); CMake file anchor also once.
- Proposed `DRNAS_CMAKE_ARM64` appears in the assembled CMake command.
- Fail-closed guard is inserted before Blender's `LIBDIR` fallback.
- Blender `find_llvm.cmd` supports `LLVM_EXE` override.
- The active GitHub Actions workflow and existing Blender executable remain unchanged; only review files were added to the private r64 branch.
- **Not** proof of Windows PowerShell execution, CMake configuration
  success, compiled Windows ARM64 Blender, or GPU support.

## Known limitations and next gates

- The compiler/toolset interaction between CMake -T ClangCl and the external
  LLVM path must be tested in a real Windows runner on the next approved run.
- If CMake still reports a mismatched architecture, do not bypass the guard:
  investigate CMake's toolchain/project initialization, do not rename x64
  libraries or pretend that x64 is ARM64.
- Source Vulkan patch has independent correctness risks (provoking-vertex
  semantics, multi-viewport, dual-source blending and WineVulkan WSI).
- A successful Blender Windows ARM64 build does **not** automatically create
  an APK or prove support on Samsung A35 / Nothing A142.
- Nothing has been merged, released or changed in the public Blender APK.

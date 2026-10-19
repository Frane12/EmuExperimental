# Blender 4.5.14 / Windows ARM64 — Mali Vulkan native patch r64 (PRIVATE SOURCE REVIEW)

**Selected strategy B: modify the real Blender C++ Vulkan backend. No APK build requested or started.**

## Why

Blender 4.5.14's stock Windows ARM64 binary rejects devices lacking `logicOp`,
`dualSrcBlend`, `multiViewport`, `shaderClipDistance`, `multiDrawIndirect`
or `VK_EXT_provoking_vertex`. Some native Android Mali Vulkan ICDs omit
these. DRNAS Wine's native Vulkan enumeration / first-present success does
**not** mean Blender can create its required Vulkan device. The user's
Galaxy A35 (SM-A356B, Mali-G68 MP5) and Nothing A142 (Mali-G610)
both logged `Blender - Unsupported Graphics Card Configuration`.

## Scope of source patch

Patch: `patches/blender-4.5.14-mali-vulkan-optional-features-r64.patch`

Source baseline: Blender upstream `v4.5.14`, GPL-2.0-or-later.

Changes across **four actual Blender C++ files**:

1. `intern/ghost/intern/GHOST_ContextVK.cc`
   - Query and enable optional `logicOp`, `dualSrcBlend`,
     `multiDrawIndirect`, `multiViewport`, and `shaderClipDistance`
     only when supported.
   - Keep geometry shader, image cube array, and fragment atomic
     requirements; match the Vulkan backend's selected device constraints.
   - Treat `VK_EXT_provoking_vertex` as optional and only chain its
     feature structure when the extension was enabled.
2. `source/blender/gpu/vulkan/vk_backend.cc`
   - No longer reject a GPU solely because the above optional features
     or `VK_EXT_provoking_vertex` are absent.
   - Continue rejecting devices missing core requirements:
     geometry shaders, image cube array, draw indirect first instance,
     fragment stores/atomics, timeline semaphore, buffer device address,
     or swapchain.
3. `source/blender/gpu/vulkan/render_graph/vk_command_buffer_wrapper.cc`
   - Fall back to one indirect draw per call when `multiDrawIndirect`
     is missing (Vulkan `drawCount=1` requirement).
4. `source/blender/gpu/vulkan/vk_pipeline_pool.cc`
   - Remove unsupported provoking-vertex pNext state from pipelines.

## Correctness limits (block release until evaluated)

- Vulkan defaults to first provoking vertex when the extension is absent.
  Blender requests last-vertex flat shading in some pipelines. This
  source patch permits device creation but does **not** fully emulate
  last-vertex behavior; inspect flat-shaded faces, selection and overlays.
- Rendering pipelines with multiple viewports or shader clipping may
  require extra fallbacks; removing a capability gate is not itself a
  guarantee of correct rendering.
- `GPU_BLEND_CUSTOM` can use dual-source blending; audit and guard any
  actual usage on drivers without that feature.
- Windows ARM64 WineVulkan + X11 WSI must still correctly enumerate the
  device, create swapchain, present frames, and manage lifecycle.
- CPU/device performance, render quality and full feature parity unverified.
  GPU model alone does not prove feature bit availability.

## Review/build boundary

The current `tools/package_blender.py` embeds the **official pinned
Windows ARM64 Blender 4.5.14 ZIP**, whose SHA-256 is
`0153ecefc96a0e23985e6edcdbe04e909ba2078258f4ff6a05c21d4b7c756911`.
Therefore **this source patch does not alter the bundled Blender executable**.

A functioning B-route APK requires separately:

1. Obtain/verify clean Blender `v4.5.14` source and review
   `python3 tools/check_blender_mali_patch.py /path/to/blender`.
2. Apply the patch to a dedicated local Blender source checkout.
3. Produce a proper **Windows ARM64** `blender.exe` with a compatible,
   license-compliant toolchain. This is not the Android app's Java/NDK
   build. Record compiler, build dependencies and all linker options.
4. QA the patched Windows executable separately, package the **exact**
   distribution ZIP, record new SHA-256/PE machine type and separate
   source-code availability. Never replace or disable the official
   release checksum by guesswork.
5. Only after explicit user approval: wire the custom distribution into
   an isolated private Android LAB build that cannot overwrite public
   Blender data. Require real on-device logs from Mali-G68 and Mali-G610.
6. Keep the public release and unmodified emulator untouched until
   Blender viewport rendering and proper frame presentation are proved.

## Gate for on-device test

PASS requires `vkCreateDevice` + pipeline setup + visible Blender viewport
+ cube rotation + file open + Eevee basic render on **actual Mali** device.
Collect `--debug-gpu` logs (ideally GPU feature list and exact Vulkan
error codes) and check for `VK_ERROR_FEATURE_NOT_PRESENT`, validation
errors, geometry/overlay corruption or a stalled swapchain.

**Do not say the GPU is fixed based on a successful patch application.**

## Windows ARM64 toolchain preparation (review-only)

The actual build is **Blender for Windows ARM64**, not Android ARM64.
The official Blender 4.5 Windows build scripts recognize `arm64` and
default to Clang when ARM64 is selected. `build_files/windows/configure_msbuild.cmd`
uses the Visual Studio 2022 generator with `-A ARM64 -T ClangCl` and installs
through the `INSTALL` target. The checked 4.5.14 CMake configuration sets
the minimum Visual Studio ARM64 developer-shell version to 17.12.3.
Our desired compiler baseline is LLVM/ClangCl 20.1+ per Blender's documented
Windows Clang support guidance. Modern Blender documentation may demand a
newer VS minor version than the 4.5.14 branch: prefer the newer toolchain.

Prerequisites for an eventual build host (not installed by this PR):

- **Windows 11 x64** or compatible Windows x64 build machine/VM.
- Visual Studio **2022** with Desktop development with C++, **MSVC
  ARM64/ARM64EC target tools**, Windows SDK ARM64 library components.
- LLVM **ClangCl 20.1+**, CMake, Git/Git LFS, Python, adequate free disk and RAM.
- Official `lib/windows_arm64` Blender dependency submodule. This is
  several gigabytes; the *read-only* CI preflight intentionally omits it.

**Prepared, not executed:** `tools/preflight_blender_win_arm64_r64.ps1`
checks the VS2022 / Clang / Windows SDK / CMake / Git / source-tag and
patch application read-only. Run on a Windows ARM64-capable build host:

```powershell
# Clean upstream v4.5.14 (before applying anything).
git clone https://github.com/blender/blender.git C:\blender-r64-src
git -C C:\blender-r64-src checkout v4.5.14

# From this DRNAS repo clone, with source path above.
powershell -ExecutionPolicy Bypass -File tools\preflight_blender_win_arm64_r64.ps1 -BlenderSource C:\blender-r64-src
```

Automated no-build inventory:
`.github/workflows/preflight-blender-4514-arm64.yml` on
`windows-2022` verifies the *upstream 4.5.14 tag* using a sparse source
checkout, calls `tools/check_blender_mali_patch.py` and inventories
preinstalled VS/Clang/SDK tools. It is not a Blender build and produces
no executable or APK. It can fail on GitHub-hosted Windows if Clang 20.1+
or ARM64 workloads are missing; this is a useful readiness finding,
not proof the patch is invalid.

**For a future explicitly approved build only:**

```cmd
REM Run from VS2022 x64 developer command prompt, after prerequisites
REM and verified windows_arm64 dependencies. This is a FUTURE procedure.
cd C:\blender-r64-src
git apply C:\path\to\EmuExperimental\patches\blender-4.5.14-mali-vulkan-optional-features-r64.patch
make.bat arm64 2022 clang full nobuild
make.bat arm64 2022 clang full
```

The first `nobuild` only configures CMake; the second performs a full
Windows ARM64 Blender **compile and install**. Both remain outside the
current work and require confirmation first. Official build docs:
https://developer.blender.org/docs/handbook/building_blender/windows/

### Why we are not patching the APK yet

The Android workflow still downloads the signed-and-hashed official
Blender `4.5.14` Windows ARM64 ZIP. Modifying only the Android
launcher or an external GPU driver does **not** modify Vulkan feature
requirements compiled into `blender.exe`. The new r64 C++ patch is
source-only and will have no effect until a properly built Windows ARM64
Blender distribution replaces the *LAB-only* pinned payload.

**Review blockers before compiler work:** confirm Vulkan surface /
swapchain and `vkCreateDevice` feature bits on A35 & A142; determine
how Vulkan's first-vertex default impacts Blender's flat shading,
multiple viewports and shader clipping; never disable missing core
features such as `timelineSemaphore` or `bufferDeviceAddress` simply
to get a window.

**No release/compilation/installation has been authorized by this step.**

# DRNAS Blender Native — isolated Android ARM64 experiment

**Status (2026-10-19): source overlay prepared; NOT compiled, installed or tested.**
This is **not** a Windows/Wine/FEX/DXVK build. It is an integration overlay for
an existing, GPL-licensed, natively compiled Android ARM64 Blender port.

## Base and provenance (fixed for repeatability)

- Upstream: `simfeo/blender`, branch `android`
- **Pinned commit:** `4bd0075178bdf71c9aa3de139af6d07e9da61b4d`
- Upstream native backend: `intern/ghost/intern/GHOST_*Android.*`
- Upstream host: `build_files/android/apk/app/src/main/java/org/blender/blender/BlenderActivity.java`
- Build instructions: `build_files/android/BUILDING.md` in the upstream source.
- Minimum API **31 (Android 12)**, **arm64-v8a**, Vulkan backend.
- Upstream requires **NDK 30.0.14904198-beta1**, Android SDK 35 build-tools 35.0.1,
  prebuilt `lib/android_arm64` and separate native host code generators.
  Do not silently substitute our DRNAS EMU NDK 27 tooling.

This branch lives in the **private** `Frane12/EmuExperimental` repository.
All additions are confined to `native-blender/`. Existing Wine, DRNAS EMU,
driver and release branches are untouched. No GitHub Action or APK publication
is configured in this stage.

## First real source integration (v0.1)

- Adds a small **DRNAS** collapsible touch toolbar to upstream `BlenderActivity`.
  Key events go to upstream's **existing native JNI** `nativeOnKey` → GHOST
  input queue, not to Wine/X11.
- Keys: TAB / ESC / G / R / S / X. Uses Android key DOWN/UP pairs, releases
  held keys when focus is lost or the bar closes, and consumes touches **only
  on the visible controls**. The native Vulkan surface remains upstream's.
- **Side-by-side application id:** `com.drnas.blendernative`. Keeps the Java
  class `org.blender.blender.BlenderActivity` intact so native JNI names remain
  valid; the launch component is updated accordingly.
- Patches both Gradle and `package.sh` Java compilation paths.
- Pin/anchor check before any change; overlay application is idempotent.

### Source-only review first (DOES NOT BUILD)

Obtain the pinned upstream checkout separately, including its upstream-licensed
code and required submodules. In a local clone of `Frane12/EmuExperimental`:

```bash
# Check out the exact upstream Android revision:
git -C /path/to/simfeo-blender checkout 4bd0075178bdf71c9aa3de139af6d07e9da61b4d

# Read-only source validation (default — makes no changes):
python3 native-blender/tools/prepare_upstream.py /path/to/simfeo-blender

# Only after review, install the isolated source overlay in that checkout:
python3 native-blender/tools/prepare_upstream.py /path/to/simfeo-blender --apply

# Offline script unit tests (does not build Blender):
python3 -m unittest discover -s native-blender/tests -v
```

**Build is deliberately not started.** As agreed, first review the diff and
approve any actual compile, APK signing, device install or release. Do not run
`build_files/android/build.py` until that decision.

## What is NOT implemented yet

- No verified compiled DRNAS Native APK; no confirmed working FPS or render.
- No DRNAS editor, repositionable controls, zoom slider or keyboard overhaul.
- No native custom Turnip dispatch; upstream's vendor/Turnip variants are the
  only investigated options. Mali/Xclipse support must be tested, not assumed.
- No claim of Cycles GPU rendering: verify CPU vs GPU device/backend explicitly.
- Packaging permission minimization, SAF, lifecycle, crash recovery, shader
  caches, render modal escape and performance testing remain separate audits.

## Review gates before first APK

1. Confirm source pin/submodules and corresponding GPL sources/attributions.
2. Check source diff and ensure app ID + JNI stay compatible.
3. Run upstream lite **vendor Vulkan** first; no custom driver while debugging.
4. On A840 verify startup, touch, ESC/TAB, viewport, save/open, loss of focus.
5. Compare identical .blend scenes against current DRNAS Wine variant using
   median/1% low FPS, frame time, RAM, crash signatures and temperature.
6. Only then evaluate full/Cycles/Turnip and non-Adreno GPUs.

New sources here are GPL-2.0-or-later and derivative work must preserve
upstream license and redistributable source obligations. Do not upload an
upstream binary unchanged and call it a DRNAS native port.

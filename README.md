# DRNAS EMU — Android Windows Gaming Runtime

> **PROJECT ANNOUNCEMENT · PRE-ALPHA IN ACTIVE DEVELOPMENT · PUBLIC TESTING PLANNED SOON**
>
> DRNAS EMU (also developed under *EmuExperimental* / *Drnas Adaptive Console*) is an independent Android application project aiming to run Windows PC games on ARM-based mobile hardware, with a focus on smooth frame delivery, efficient GPU use, and transparent testing.
>
> **A first public pre-alpha test release is being actively prepared.** It is **not available for public testing yet**, and no fixed release date is promised. Early builds may be unstable, have rendering artifacts, or fail to launch some games.

<p align="center">
  <img src="https://cdn.uc.assets.prezly.com/497bc673-1170-4d96-a149-4c29576f731f/-/preview/-/format/auto/CrysisRemastered_Screenshot1.jpg" alt="Official Crytek Crysis Remastered gameplay image illustrating a demanding PC gaming target; not a DRNAS EMU screenshot" width="780" />
</p>

<p align="center"><sub><strong>Illustration:</strong> Official <em>Crysis Remastered</em> press screenshot, © Crytek. <a href="https://press.crytek.com/media/album/97c5c8d3-9e38-4f92-b713-74be40258620">Source: Crytek press media gallery</a>. This is <strong>not</strong> a screenshot captured from DRNAS EMU, and it is not evidence of emulator compatibility or performance.</sub></p>

## What we're building

DRNAS EMU is intended to combine a native Android interface with a modular Windows execution stack: **Wine/Proton, FEX, DXVK, Vulkan/Turnip, audio and input handling**. Third-party open-source projects retain their own licenses and attribution; the integration and DRNAS-specific code are developed separately.

The project has moved beyond early package-import experiments. **Internal builds now reach an on-device Wine GUI with X11 display and audio**, with input support and **Direct3D/DXVK/game rendering** under active investigation. Recent private tests include attempts to launch **Crysis**. These are engineering milestones—not general game-compatibility claims. Rendering performance, graphical correctness, installation flow and stability still need substantial work.

## Research goal: Qualcomm Hexagon NPU assisting the GPU

An important **future research goal** is to explore using compatible Qualcomm **Hexagon NPU / QNN-HTP** hardware for **asynchronous prediction and decision support** while the GPU renders games. Possible experiments include learning render-pass patterns or load trends and supplying **non-blocking hints** to the GPU driver's adaptive GMEM/SYSMEM, shader or texture policies.

**The NPU does not render graphics and is not currently implemented as a working GPU acceleration feature in DRNAS EMU.** Access, device compatibility, latency, overhead and measurable benefit must be verified experimentally. The render path must never wait on an NPU result: when a prediction is late, invalid or unhelpful, the GPU must continue using a known-safe native path. The goal is **frametime consistency and stable rendering**, not inflated FPS promises.

## Upcoming pre-alpha

The first external test builds will be labeled **PRE-ALPHA / TESTING ONLY**, with a clear list of known limitations, component credits, reproducible checksums and rollback guidance. We plan to invite feedback on device model, Android version, emulation stack, game launch behavior, visual artifacts, frame pacing and logs. **No public APK is linked here until a release is ready.**

**Author / development:** Frane12 · **Status:** active private development · **Announcement:** 17 October 2026

For a shareable announcement, see [Pre-alpha announcement](docs/ANNOUNCEMENT_PRE_ALPHA.md).

---

## Code layout

- `android-app/` — complete Android Gradle application (Java/Android UI; session process manager; local WCP vault; FEXCore TAR/XZ bridge verification).
- `contracts/` — session adapter interface, pointer-ownership router reference.
- `tools/` — Python reference WCP importer for Wine, Proton, FEXCore and DXVK, plus JavaScript syntax check.
- `tests/` — smoke tests and malicious-archive rejection tests for the reference importer.
- `profiles/` — Far Cry 3 example and JSON profile schema.
- `web-prototype/` — offline console-style UI concept.
- `docs/` — integration architecture, package investigations, Android device tests and constraints.
- `.github/workflows/build-android.yml` — debug APK build.
- `.github/workflows/test-contracts.yml` — Python importer + input/profile contracts + web syntax.
- `.github/workflows/package-source.yml` — ZIP of the source tree.

## Historical V0.3.1 build instructions (legacy)

The following documents an **early V0.3.1 development milestone**, not the latest DRNAS EMU pre-alpha build. For earlier experiments, use **Actions → EmuExperimental - Android APK V0.3.1 Prefix Compatibility** and download the artifact
`Drnas-Console-V0.3.1-Prefix-Link-Fix-debug-apk` from a successful run. The artifact
is a ZIP containing `app-debug.apk`.

To build with Gradle 8.9, Android SDK 35 and Java 17:

```bash
cd android-app
gradle :app:assembleDebug
# android-app/app/build/outputs/apk/debug/app-debug.apk
```

On the phone, **Components → Select local .wcp** to import a local
`FEXCore-2609-GameNative-O3.wcp`, then check whether the app reports
`FEX bridges VERIFIED (not activated)`. The FEX package must not be executed
as a generic Android binary: it includes two Windows PE bridge DLLs. A
matching Proton ARM64EC/Wine loader, compatible Bionic execution layer,
X11/audio/Vulkan integration and ABI checks are **future milestones**.

## Verified third-party package data

The following real third-party WCP formats were previously inspected in an
isolated CI runner; **no third-party binary packages are tracked in this repo**:

- Proton 10 ARM64EC controller fix: TAR/ZSTD, manifest `type: Proton`.
- DXVK 1.10.3: TAR/XZ, manifest with `system32`/`syswow64` DLL mappings.
- FEXCore 2609 GameNative O3: TAR/XZ, exact `libarm64ecfex.dll` and `libwow64fex.dll` mappings.

More details: `docs/REAL_WCP_SAMPLES.md`, `docs/FEXCORE_2609_GAMENATIVE.md`
and `docs/ANDROID_FEX_BRIDGE_V0_2.md`.

## Ground rules

- Each component version is immutable and independently replaceable.
- No automatic root requirement or system driver replacement.
- No unsafe execution of downloaded archives; untrusted WCP content must be validated.
- Per-game sessions and input pointer ownership are isolated.
- Claims of emulator compatibility require real on-device execution and test logs.
- Keep DXVK/Turnip/FEXCore/Proton third-party source and binary licensing clear.

### Provenance

The V0.2 source code was migrated from private experimental branch
`Frane12/Turnip-Neuro-Private:experiment/drnas-console-v0-concept` into
this standalone `Frane12/EmuExperimental` repository on 2026-10-14.
The Turnip repository was left unchanged.

Project by Frane12 · exploratory hobby and research.

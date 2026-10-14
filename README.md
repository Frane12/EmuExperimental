# EmuExperimental — Drnas Adaptive Console

Private experimental Android PC-gaming runtime project.

**Version:** Drnas Console Runtime Core V0.2 — FEX Bridge
**Project status:** Android APK compiles; native Android process smoke test has passed on an Android 16 phone. WCP importer/bridge staging is implemented, but **Wine/Proton/FEXCore game execution is not yet connected**.

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

## Build and install the Android APK

Use **Actions → EmuExperimental - Android APK V0.2** and download the artifact
`Drnas-Console-FEX-Bridge-V0.2-debug-apk` from a successful run. The artifact
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

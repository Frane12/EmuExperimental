# Android Runtime Core V0.2 — FEX bridge verification

## What changed from V0.1

Real Android APK now has **a format-aware FEXCore TAR/XZ import path** in `FexBridgePackage.java`.

When the user selects a local `FEXCore*.wcp` file, Android:
1. Copies the file to app-private WCP vault and computes SHA-256.
2. Detects XZ magic bytes; reads the TAR stream using XZ and Apache Commons Compress.
3. Applies strict entry-count/size/path checks and refuses archive symlinks/special entries.
4. Reads the **real** `profile.json`, verifies `type: FEXCore`, version and the exact two mappings:
   - `system32/libarm64ecfex.dll -> ${system32}/libarm64ecfex.dll`
   - `system32/libwow64fex.dll -> ${system32}/libwow64fex.dll`
5. Hashes and validates the PE headers/machine IDs of both DLLs, writing them only to an isolated private staging directory.
6. Records `fex_bridge_pair_verified_not_activated` and an explicit `runtimeCompatibility: NOT_TESTED` marker.

All other WCP archives continue to be **opaque-staged** (no manifest validation or DLL loading in the Android app yet).

**No binaries inside any WCP are executed.** Android PE libraries do not become usable by simply copying them into app-private storage. Actual Proton 10 ARM64EC boot, Wine prefix activation, loader/API availability, compatible FEX binding, X11, Vulkan, DXVK and sound remain separate future work.

## Physical device test

Install V0.2 APK artifact from GitHub Actions. In Components, select the user-provided
`FEXCore-2609-GameNative-O3.wcp`. A successful import shows:

`FEX bridges VERIFIED (not activated)`

Opening Components again should display:

`FEX BRIDGE PAIR VERIFIED — Proton link not active`

If anything fails, provide the exact on-screen message. This is a genuine file-parser/integrity test **on the Android device**, not a Wine launch.

## Source package fingerprint (from actual user upload)

`FEXCore-2609-GameNative-O3.wcp`

SHA-256: `e0bafa70aed144620eb8cc9fb81f7e75cdfa7f1679379b4cc2986653a113f0cf`

Do not publish third-party WCP binaries in the app source repository.

## Reason for the split

Ludashi applies a selected FEXCore content profile to the virtual Wine prefix's `system32`. Our adapter first proves the package is structured and maps to precisely the expected two PE files. A later `WinePrefixAdapter` can copy **only checked content** into a disposable per-game prefix with rollback and ABI/loader smoke tests. Never silently patch a shared/global prefix.

## Verified Android build

GitHub Actions run [38488871224](https://github.com/Frane12/Turnip-Neuro-Private/actions/runs/38488871224) **SUCCESS**. Artifact: `Drnas-Console-FEX-Bridge-V0.2-debug-apk`. APK size: `1,369,745` bytes; SHA-256: `56d44e3fa08eb3977d672ad07621c43789d3def726315ce844ba3983c25a7486`. Android compilation and APK validation passed; on-device FEX WCP verification remains pending a physical test.

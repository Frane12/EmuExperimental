# Drnas Console Runtime Core v0.1 — real Android APK

## Exactly what is implemented

The `android-app/` project is an **actual native Android application**, not a browser mockup. It builds a debug APK using Android Gradle Plugin 8.7.3, Java 17, SDK 35 and Gradle 8.9.

- Dark console-style portrait/landscape responsive UI, game profile cards, component vault, runtime diagnostics.
- **SessionManager** with `IDLE -> STARTING -> RUNNING -> STOPPING -> IDLE/FAILED`, async stdout and process exit logging, and explicit stop/cleanup.
- **An actual Android process smoke test**: starts the Android-provided `/system/bin/sh` executable, outputs heartbeat lines for 30 seconds, and permits stopping. It does **not** use `exec()` on files downloaded into application storage.
- **Android storage access framework WCP staging**: opens user-selected local `.wcp`, streams to private content store, checks size (up to 1 GiB), SHA-256 and compression magic; records metadata as `archive_staged_not_validated`.
- No network permission. No root permission. No external storage permission. No driver installation. No game files uploaded.
- Capability report: device model, supported ABIs, RAM, Android API, Vulkan feature. GPU chip identity intentionally **not fabricated**.

## Deliberately NOT implemented

- Proton/Wine runtime start and executable loading; FEXCore O3 ABI integration; native X server; audio bridge; DXVK and Turnip Vulkan driver loading.
- Full WCP TAR/manifest validation in Android. A separate **offline Python reference** importer does that; the Android vault is only a staging store.
- No genuine Far Cry 3/Crysis game execution in this APK.

## Build

```bash
cd projects/drnas-console/android-app
gradle --no-daemon :app:assembleDebug
# Debug APK: app/build/outputs/apk/debug/app-debug.apk
```

Or retrieve the CI **APK artifact** from `.github/workflows/drnas-console-android-v01.yml`.

## Test on Android

1. Install the debug APK from the GitHub CI artifact on a device you control. Debug builds have no auto-update signature stability guarantees.
2. Open `Console` and select `▶ Start native smoke test`.
3. Open `Diagnostics`. You should see `[native] DRNAS_NATIVE_SMOKE_OK`, heartbeat messages and session state RUNNING.
4. Tap `■ Stop process` and verify state returns to IDLE.
5. In `Components`, optionally select a WCP; its SHA-256 and compression signature are logged and it is stored locally as **not validated / not executable**.

## Next milestone: Wine/FEX real backend

Before invoking `winecfg`, implement a compatible Android/Bionic loader strategy (including Android API 29+ execution restrictions), compatible Proton ARM64EC + FEXCore bridge mapping, X11 and audio services, a controlled Wine prefix, and per-session DXVK/Turnip loading. Prefer bringing an existing working Bionic runtime under an adapter after reviewing dependencies and their licenses, rather than attempting to directly execute the downloaded WCP as a file from private storage.

`/system/bin/sh` is an OS executable used for process lifecycle proof only; its success cannot be used as evidence that Proton or Wine works.

## First real APK build verified (2026-10-14)

GitHub Actions run [38487849495](https://github.com/Frane12/Turnip-Neuro-Private/actions/runs/38487849495): **SUCCESS**. AGP compiled debug Android APK, CI checked its binary and packaged `AndroidManifest.xml` and `classes.dex`, then published artifact `Drnas-Console-RuntimeCore-V0.1-debug-apk` (artifact ID 11859254213; 14-day retention). APK length: **26,125 bytes**; SHA-256: `5d22ce97e6a1f63679c5dbd0ad8780296784df03fa8332de2b8cd7fe2c1a70d6`. **No physical Android device runtime testing performed yet**; process smoke behavior remains to be tested on user device. This build contains no game runtime or downloaded WCP binaries.

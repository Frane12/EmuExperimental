# DRNAS Console v0.4.1 — experimental Bionic Wine CLI probe

We verified that the current Proton 11 ARM64EC component is sourced from the **GameNative Android/Bionic** build, not a conventional glibc Linux Wine distribution.

Source: `GameNative/proton-wine`, `.github/workflows/build-proton.yml`, release `proton-11.0-2-20260928`. The workflow explicitly builds Bionic and emits `proton-11.0-2-arm64ec.wcp` with `wine.binPath=bin`.

The phone logs from v0.4 show:
- NDK JNI native Bionic code ran on AArch64.
- Existing Wine prefix, FEX pair and 13 DXVK DLLs staged successfully.
- The C probe of `bin/wine` said **"not ELF64 or a #! script"**, which is **not enough to infer the binary ABI**. It could be MZ/PE, a wrapper, a different binary, or an unusual header. Do not assume missing glibc.
- Android system Vulkan API 1.4 is available; custom Turnip is NOT loaded.

## v0.4.1 implementation

`WineLaunchProbe.java` scans only known `bin/wine`, `wine64`, `wine-preloader`, and `wine64-preloader` entrypoints within the **already validated and staged** user-imported Proton package. For each candidate it reports initial header bytes, ELF magic, ELF class and AArch64 machine, or PE/MZ and unknown bytes.

It parses PT_INTERP to select a 64-bit ARM **Bionic-linked** Wine executable. Non-Bionic ELF, PE or unknown files are **NOT executed**.

Only after a Bionic entrypoint is identified, v0.4.1 tries a real **`wine --version`** using `/system/bin/linker64 --library-path ... <wine> --version`. This is explicitly experimental: Android 16's writable-app-data execution and linker namespace restrictions may reject loading or mapping the guest binary. There is a 12-second watchdog, bounded output, explicit exit-code reporting and verification of a `wine-X.Y` version banner.

In addition, Wine gets `WINEPREFIX=<private-prefix/.wine>`, `LD_LIBRARY_PATH=<staged Proton lib paths>`, `WINEARCH=win64`, private `TMPDIR`, and `PROTON_NO_NTSYNC=1` for deterministic first startup.

A successful `wine --version` does not mean `wineboot --init` or any Windows game executed. No X11, audio, Vulkan ICD, CPU translation or GUI process is wired yet.

## Phone test / expected diagnostics

Tap **Start container (experimental)**, open Diagnostics. Look for:
- `Wine candidate wine: [PE/MZ|non-ELF|ELF64 AArch64]` and initial magic.
- `Wine candidate wine64: ...` (if present).
- `TRY REAL BIONIC WINE --version via Android linker64` only if a qualifying Bionic ELF was found.
- `Wine --version real process exit=N versionBannerSeen=true/false`.
- A truthful `WINE_VERSION_OK` or `NO_BIONIC_ELF_WINE_ENTRY` / `ANDROID_LINKER_EXEC_DENIED` / `WINE_VERSION_EXIT_N`.

Next step, based on the real log, is packaging a native executable through an approved Android app native-lib path (if the guest build supports it), plus required environment/system libs. Then test a bounded Wine CLI and `wineboot --init` before graphical/GPU integration. Do not bypass Android SELinux restrictions or claim a game runs without a real Windows guest process.

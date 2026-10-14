# DRNAS Console v0.4.4 — isolated Wineboot experiment

## Verified device baseline (v0.4.3)
On Android 16, the user's Proton 11 ARM64EC / GameNative Bionic Wine binary at `lib/wine/aarch64-unix/wine` **actually ran** under `/system/bin/linker64` and returned:

```
[wine stdout] wine-11.0
Wine --version real process exit=0 versionBannerSeen=true
WINE CLI VERIFIED / WINEBOOT NOT INITIALIZED
```

This is the first demonstrated native Wine CLI launch. It does **not** demonstrate a Windows guest program, X server, FEX translation, DXVK or Turnip activation.

## v0.4.4 changes
UI now offers two intentionally distinct actions:
- **Start container (Wine CLI)** — existing successful version probe, no prefix initialization.
- **Test wineboot --init (isolated)** — first confirms `wine --version`; if successful, invokes the actual Bionic wine loader using:

```
/system/bin/linker64 <checked game-native lib/wine/aarch64-unix/wine> wineboot.exe --init
```

`bin/wineboot` was previously logged as a zero-length package placeholder, **so we never try to execute it**; the `wineboot.exe` is requested through Wine's loader (defined in `GameNative/proton-wine/programs/wineboot/Makefile.in`).

Before launching, v0.4.4 **re-extracts** the user-imported Proton `prefixPack.txz` into `<app-data>/files/containers/default/wineboot-probe/template/.wine`, re-applies the staged and previously verified FEX PE bridge DLLs into its own `system32`, and sets `WINEPREFIX` to this *disposable* copy. It **never initializes the main prefix** or rewrites the source WCP. An existing bootprobe sandbox from a previous test is safely reset (symlinks not followed) before a new attempt.

Host uses the Bionic linker, matched Proton `LD_LIBRARY_PATH` and `WINEDLLPATH`, private `TMPDIR`, `WINEARCH=win64`, `WINELOADER`, `WINESERVER` and `PROTON_NO_NTSYNC=1`. The child has a **50 second watchdog**, asynchronous stderr/stdout capture and capped output.

The result is intentionally strict. A success result `WINEBOOT_PREFIX_INITIALIZED` requires:
1. The real child exits with code zero.
2. Two or more standard Wine registry files are nonempty and contain the `WINE REGISTRY Version` header.
3. The registry fingerprint *changes* compared with the fresh template before launch.

Other statuses, including `WINEBOOT_EXIT_N`, `WINEBOOT_TIMEOUT`, `WINEBOOT_EXIT_0_REGISTRY_UNCHANGED`, are not reported as initialized. These checks are provisional: they do not prove the entire Windows environment or FEX stack works. `boot-plan.json` records the status and registry evidence, but still keeps graphical and game capability flags false.

## Likely next observed failure

Running `wine --version` involves only the Wine CLI loader. `wineboot.exe` needs the Wine PE loader, built-in DLLs and a real `wineserver`. Android's SELinux / app data execution policy may reject Wine's internal spawn of its wineserver, even when explicit linker64 invocation succeeds. Alternatively, ARM64EC binary or library resolution may fail. **Observe the phone log before attempting a workaround**. Do not bypass platform restrictions.

## Next milestone
Only if wineboot has run and produced credible prefix mutation: attempt a headless Windows PE program such as `cmd.exe /c ver` in the isolated sandbox; then test X11 window creation, display, audio, Vulkan ICD, and DXVK/FEX separately. Keep optional tests as distinct A/B actions and record exact exit/status. No FPS or game claims until a real guest process runs.

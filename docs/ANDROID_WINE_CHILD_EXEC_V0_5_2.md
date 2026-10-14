# DRNAS v0.5.2 — first Wine child loader relay test

## Confirmed from phone v0.5.1

- Native Bionic Wine 11.0 and wineserver --version both work.
- NLS path shim lets the app-owned wineserver remain alive and serve client requests.
- Wine built-in ARM64 `start.exe`, `ntdll.dll`, `kernel32.dll` and `kernelbase.dll` are genuinely mapped and kernel32/kernelbase are reported as loaded.
- Native parent still prints `run_wineboot failed to start wineboot 1`, eventually exits code 1. Three registry fingerprints are unchanged. **No successful Wineboot or Windows PE guest process yet**.
- Prior standalone experiment demonstrated Android's EACCES (errno 13) when trying to exec the *user-writable* staged `wineserver` ELF directly; Android linker64 succeeds.

## Root-cause hypothesis from Wine source

In `GameNative/proton-wine/dlls/ntdll/unix/env.c`, `run_wineboot()` calls
`NtCreateUserProcess` to spawn an internal `wineboot.exe`. In
`dlls/ntdll/unix/process.c`, `spawn_process()` forks and invokes
`exec_wineloader()`. That routine uses `preloader_exec()` and then
`execv(wineloader, argv+1)` in `dlls/ntdll/unix/loader.c`. The requested
executable is a staged Wine ELF in writable app-private storage, where
Android 16 denied direct exec in an analogous wineserver test.

This strongly suggests the Wineboot spawn fails at direct exec. It is
**not yet confirmed** until the new relay's logs show it being invoked.

## Scoped experimental adapter

Add an NDK shared library, `libdrnas_wine_child_exec.so`. `LD_PRELOAD`
injects it ONLY in the disposable Wineboot parent. It interposes **execv**.
The original execv is used for every unrelated program. For the *exact
canonical verified Bionic Wine ELF path* passed by our launcher in
`DRNAS_WINE_CHILD_EXE`, it instead calls:

```
execv("/system/bin/linker64",
      ["/system/bin/linker64", VERIFIED_WINE_ELF, original_argv[1..]])
```

Wine's child arguments, inherited wineserver socket FD and
`WINELOADERNOEXEC` flag are preserved. Avoid using any data-file ELF directly
as a kernel-executed path. Do not modify Android system files, Wine libraries,
external game binaries, or the user's main Wine prefix. This shim remains
inactive outside the explicit test.

### Pass/reject signals

- Observe `[DRNAS child] execv Wine -> Android linker64`. If absent, the
  Wine child is using a different path or spawn mechanism.
- Observe Wine child / wineserver communication and `wineboot` registry
  modifications. The first message alone is NOT a passed Wineboot.
- Exit 0 plus registry changes and valid registry files are provisional
  initialization evidence. Failures, no registry change, or timeout are
  regression/failure; preserve exact messages and do not claim app/game
  execution.
- A real Windows guest is not confirmed until a separate headless PE command
  executes successfully. Graphics/Turnip/DXVK are not connected.

This build is developed and unit tested in CI, then signed **offline with the
same private user-owned DRNAS stable keystore**. Ephemeral CI-signed APKs must
never be installed as updates.

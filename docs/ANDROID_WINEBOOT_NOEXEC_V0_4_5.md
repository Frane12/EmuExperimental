# v0.4.5 — Wine internal loader reexec workaround (experimental)

## Confirmed on-phone behavior from v0.4.4

Wine CLI version works: `wine-11.0`, exit 0, version banner confirmed (v0.4.3 baseline).

The separate `wineboot.exe --init` probe unpacked 408 files in a disposable template; baseline registry files were already present in the GameNative prefix template. The process failed:

```
[wineboot] wine: could not exec the wine loader
Wineboot launcher exit=1
Registry content changed=false
Container status: WINE CLI VERIFIED / WINEBOOT_EXIT_1
```

The existing registry files alone are **not** evidence of successful prefix initialization.

## Root cause and evidence

We inspected GameNative/proton-wine source `dlls/ntdll/unix/loader.c`:

- `check_command_line` calls `reexec_loader(...)` for non-`--version` programs.
- `reexec_loader` calls `loader_exec` to exec Wine ELF again, which returns the precise error string seen on the device when that second `execv` fails.
- `__wine_main` skips `check_command_line` if `WINELOADERNOEXEC` is already set (and argc >1), then unsets it. Wine itself sets the flag before a successful internal handoff.

Android 16 can run the initially selected Bionic ELF through `/system/bin/linker64 <WineELF>`, but internal `execv` from writable app storage is restricted. This is our **leading diagnosis**, not yet proof of the precise kernel errno.

## v0.4.5 isolated A/B test

- **Start container (Wine CLI)** remains unchanged and continues to validate `wine-11.0`.
- **Test wineboot --init (isolated)** now adds `WINELOADERNOEXEC=1` only to that child environment so Wine executes its second-stage initialization directly rather than attempting a second `execv`.
- Keeps the previously successful Bionic linker64 + absolute staged Wine ELF path, matched Wine library paths, private prefix and watchdog.
- Enables targeted process/server diagnostics (`WINEDEBUG=+process,+server`) with a log line cap.
- No changes to original WCP archives, main prefix, or graphical/GPU layer.
- Regression test checks that the second-stage flag is set on request.

### Success gates
Do **not** claim wineboot has successfully initialized unless:
1. The real process exits code 0.
2. Its test prefix's registry files have valid headers.
3. The registry contents change relative to template baseline.

In particular, the template already contains `system.reg`, `user.reg`, and `userdef.reg`; file existence is not proof.

### Likely next blocker

Even after bypassing this initial reexec, Wine may need to spawn its separate `wineserver` ELF, which can hit the same Android executable-storage restriction. If the next log says `could not exec wineserver`, the correct next engineering step is a safe, bundled native host launcher/IPC adapter for spawning appropriate Bionic programs. Do **not** use chmod or arbitrary executable-storage bypass tricks.

No completed Wineboot, Windows PE process, X server or Turnip ICD execution is claimed until confirmed on device.

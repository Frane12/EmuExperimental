# DRNAS Console v0.5.1: isolated Wine bootstrap mode

## Evidence from the Android 16 v0.5.0 log

Wine 11 CLI and wineserver --version work via Android linker64. With the
isolated NLS path shim, wineserver now remains alive and answers Wine client
requests. The Wine PE loader mapped ARM64 start.exe, ntdll.dll, and
apisetschema.dll, but failed:

```
[pe inventory] aarch64-windows/kernel32.dll = PE ARM64 machine=0xaa64 size=1048576
[pe inventory] aarch64-windows/kernelbase.dll = PE ARM64 machine=0xaa64 size=2293760
[pe inventory] prefix/system32/kernel32.dll = MISSING
[pe inventory] prefix/system32/kernelbase.dll = MISSING
[pe inventory] prefix/system32/ntdll.dll = MISSING
[wineboot] warn:module:load_dll Failed to load module L"kernel32.dll"; status=c0000135
[wineboot] wine: could not load kernel32.dll, status c0000135
```

The binaries are **present in the staged Proton's builtin PE directory**, but
the prefix's system32 has no files for them.

## Upstream Wine logic, verified from GameNative/proton-wine

`dlls/ntdll/loader.c`:

- `load_global_options` treats the **presence** of
  `WINEBOOTSTRAPMODE` as permission to load builtins without their
  corresponding `system32` files.
- `find_builtin_without_file` returns `STATUS_DLL_NOT_FOUND`
  for normal DLL names unless `is_prefix_bootstrap` is true.
- When bootstrapping, it checks `WINEDLLDIR0` and related paths;
  `dlls/ntdll/unix/env.c` constructs those from native `WINEDLLPATH`.
- During initial process creation, `run_wineboot` tries to launch
  `wineboot.exe` with bootstrap mode, then restores the original bootstrap
  state. Our device log showed the auto-launched Wineboot failing early before
  the parent continued. Without an external bootstrap variable, the subsequent
  kernel32 lookup is non-bootstrap and fails.

## Narrow change in v0.5.1

Add only `WINEBOOTSTRAPMODE=1` to the child environment of the
**disposable** Wineboot test (not Wine CLI, global app, or main prefix).
Preserve `WINEDLLPATH=<staged-Proton>/lib/wine`,
`WINELOADERNOEXEC=1`, native linker64, NLS shim,
wineserver A/B prestart, 50-second watchdog and strict registry validation.

This is a testable mechanism, not a guaranteed fix.
The next run should show whether it can load `kernel32.dll`.
Even when kernel32 works, Wine could fail spawning another loader or
initializing registry; report status rather than claiming Windows execution.

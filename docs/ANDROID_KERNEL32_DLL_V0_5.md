# DRNAS v0.5.0 — first Windows PE initialization diagnostics

## What the Android 16 log of v0.4.9 *actually establishes*

- Android/Bionic native Wine CLI returned `wine-11.0`, exit 0.
- Android/Bionic native wineserver `--version` returned 0.
- Direct execution of the staged app-data wineserver still fails with errno 13 (Permission denied); the Android `linker64` path is required.
- Narrowly scoped NLS source-path compatibility library fixed the previous
  `failed to load l_intl.nls` error. Foreground wineserver now remains alive,
  negotiates `init_first_thread`, opens mappings, and serves Wine registry queries.
- **A Windows PE process launch was ATTEMPTED**: trace includes
  `NtCreateUserProcess C:\\windows\\system32\\wineboot.exe`.
  This is not proof that the PE program actually started.
- Wineboot failed with `wine: could not load kernel32.dll, status c0000135`;
  launcher exited 53 and no registry files were changed.
- Wineupstream's `0xC0000135` is `STATUS_DLL_NOT_FOUND`. It can reflect a
  missing **dependent module**, not necessarily an absent kernel32.dll file.

## What v0.5.0 changes

Preserve the previous working NLS / wineserver behavior. Do not copy arbitrary
DLLs into Windows system32 to hide the error.

Add read-only inventory of the actual staged Proton distribution:
- `lib/wine/aarch64-windows`, `arm64ec-windows`, `x86_64-windows`:
  `wineboot.exe`, `ntdll.dll`, `kernel32.dll`, `kernelbase.dll`,
  `wow64.dll`, `wow64win.dll`, `ucrtbase.dll`, `advapi32.dll`.
- Check DOS/PE signatures and the PE COFF machine type
  (AArch64, ARM64EC, AMD64 or I386), plus the private prefix versions.
- Log `WINEDLLPATH` and presence of the packaged Wine data directory.
- Change the Wine CLI child debug flags to
  `WINEDEBUG=+process,+module,+loaddll`. Prioritize kernel32/kernelbase,
  error/warning, NTDLL and status lines even when tracing is verbose.
- Preserve the disposable prefix, the 50s watchdog and registry fingerprint
  criteria. No guest success is claimed unless processes initialize cleanly
  and registry mutations confirm it.

## Expected next diagnosis

- A missing or wrong-architecture `aarch64-windows/kernel32.dll` is a
  **package layout issue** (though Wine can also load dependencies elsewhere).
- If kernel32 and kernelbase exist as valid ARM64 PE, investigate dependent
  modules and loader search/mapping errors from `+module,+loaddll` trace,
  including `WINEDLLPATH`.
- If the log still says `STATUS_DLL_NOT_FOUND` with valid PE candidates and
  no extra details, consider a bounded isolated A/B experiment on module
  resolution, rather than declaring the package broken.

Next confirmed milestone is successful Wineboot registry mutation; graphical
Wine execution, FEX execution, custom Turnip ICD and DXVK are still untested.

## Signing safety

CI's default build is not a persistent update signature unless the private
signing key is supplied as GitHub Actions secrets. A separate offline signing
workflow can produce a transient APK solely as input to re-signing with the
user's existing key. Never install the transient debug-signed APK over the
permanently signed app: Android will reject it.

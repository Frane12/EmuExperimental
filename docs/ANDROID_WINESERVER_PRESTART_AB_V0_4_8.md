# DRNAS v0.4.8 — isolated wineserver prestart A/B

## What the v0.4.6 phone log proves

The on-device data confirms:
- GameNative Proton Wine 11.0 Bionic `wine --version`: **exit 0**, banner present.
- Bionic wineserver `--version` via Android `linker64`: **exit 0**, banner present.
- Proton `lib/wine/aarch64-windows/wineboot.exe`: real PE/MZ (524288 bytes).
- Proton `lib/wine/aarch64-windows/ntdll.dll`: real PE/MZ (1769472 bytes).
- Proton `lib/wine/aarch64-unix/ntdll.so`: real ELF (1816992 bytes).
- Wineboot in disposable prefix with `WINELOADERNOEXEC=1`: exits **127 immediately**, writes no registry modifications.
- The three registries existed *before* the run; their presence is not a success signal.
- Turnip Vulkan ICD, X11 and audio are still not activated.

**Exit 127 is not a diagnosis.** Missing Wineboot and missing NTDLL are less
plausible because actual PE modules were found. The fact that wineserver
`--version` works does not show Wine can spawn/connect to its server.

Upstream evidence from `GameNative/proton-wine/dlls/ntdll/unix/loader.c`:
`start_server()` starts a new wineserver with `exec_wineserver()` if there is
no existing server socket. That exec path is different from our direct
`linker64 wineserver --version` test.

## New test steps (in *only* the disposable test prefix)

1. Existing verified `wine --version` and `linker64 wineserver --version`.
2. New **A**: invoke the same wineserver with ordinary
   `ProcessBuilder(<staged app-private wineserver>, --version)`. Record exact
   Android IOException or exit code. No permission bypass.
3. New **B**: invoke `/system/bin/linker64 <verified wineserver ELF>
   --foreground --persistent=60` using the **same WINEPREFIX** and libraries
   as wineboot. Report early exit; wait a short startup period if alive.
4. If B remains alive, execute existing `wineboot.exe --init` from
   `linker64 <Wine ELF>` with `WINELOADERNOEXEC=1`, bounded to 50 seconds,
   and report return code, registry fingerprint changes and server output.
5. Always terminate the **app-owned** B server in a `finally` block.

If wineboot still exits 127 while an app-owned wineserver remains up, the
failure is not proven to be server spawning: inspect server socket,
loader/PE transition and ARM64EC dependencies. If Wineboot succeeds, confirm
registry changes; do not infer GUI or Windows game support from the result.

Source: GameNative/proton-wine `server/main.c` documents foreground
(`--foreground`) and persistent (`--persistent[=n]`) options.

## Signing

GitHub CI requires permanent signing secrets; workflow will **not publish**
another throwaway-key APK. Until the same persistent key used by the already
installed DRNAS APK is configured in repository secrets, this source
revision cannot generate an update-compatible signed APK via Actions. Do
not upload signing keys to repository source files.

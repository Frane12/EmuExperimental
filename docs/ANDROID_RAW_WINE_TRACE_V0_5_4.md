# DRNAS Console v0.5.4 — native RAW Wine trace export

User's DRNAS v0.5.3 session TXT (2026-10-14 17:10) confirms:
- Wine 11 CLI exits 0.
- Wineserver starts using linker64 + targeted NLS shim.
- Wineboot second-stage child is started via scoped `execv` relay;
  `NtCreateUserProcess` reports a PID and thread for wineboot.exe.
- ARM64 builtin `ntdll.dll`, `kernel32.dll` and `kernelbase.dll`
  are loaded in the *child* Wine process.
- Launcher exits **0**, but all preexisting .reg hashes remain unchanged.
  No completed Wineboot initialization has yet been established.
- The UI preserved only 81 of 23,710 stdout lines. Critically, detailed
  error, exception, and runtime shutdown information may be near the tail
  of those omitted messages.

## Changes

- New `RawWineTrace` synchronously captures all lines emitted from the
  disposable Wineboot process's merged stdout+stderr pipe and from its
  app-prestarted foreground wineserver pipe.
- The raw file resides only in app-private storage as
  `files/logs/latest-wineboot-raw.txt`, overwritten at the start of each
  new isolated Wineboot probe. Its total size is bounded to **24 MiB**;
  reaching the cap writes a clear truncation marker.
- The previous concise `SessionLogStore` and console's 80-line
  highlights remain intact, avoiding huge UI allocations.
- A new **Export RAW Wine trace (.txt)** button appears beside the existing
  full console-event TXT export in Console and Diagnostics.
- Uses `ACTION_CREATE_DOCUMENT` and a background stream copy to let the
  user choose Downloads or another Android document provider. No broad
  storage permissions or desktop computer are needed.
- Export blocks while the container test is busy, to avoid saving a
  partially recorded trace. It does not expose app-private storage directly.
- The raw trace captures exactly what Wine and wineserver send over pipes.
  If Wine writes to a different file descriptor not inherited by these pipes,
  that output is outside this test's scope.

## Test and privacy

Automated JVM tests verify 25k+ lines survive the old UI limit,
file size bounds, UTF-8, and truncation notices.
`WineBootProbe.run` is otherwise unchanged: no GUI, Vulkan driver, or
game execution is implied. Trace data can include absolute filesystem
paths and Windows registry values; share it only where appropriate.

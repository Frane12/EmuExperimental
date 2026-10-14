# DRNAS Console 0.5.5 — explicit Wineboot and guest evidence

The 17:10 session showed exit 0 and DLL loads, but only 81 of 23,710 lines were retained. It did not establish Wineboot completion. Version 0.5.4 added full RAW export; this change adds bounded evidence before the UI filter.

- Enable Wineboot and SEH trace channels; preserve the last 12 lines and error count in session TXT.
- Require the exact Wineboot main `Operation done` trace, process exit 0, and complete pipe drain.
- After completed boot, run Windows `cmd.exe /d /c echo <random marker>` with a 12s watchdog in the same disposable prefix. Only an exact output line and exit 0 count, never a trace containing the command.
- Inspect and reset the imported `.update-timestamp` only in the disposable copy. GameNative Wineboot skips wine.inf update for a matching timestamp or `disable` marker; whether this explains the device result is a hypothesis.
- Close the app-owned persistent wineserver before checking registry fingerprints. Forced server termination may still fail to flush: persisted registry mutation is evidence, not the sole success criterion.
- A completed boot plus CMD proof with unchanged valid registries is reported separately as `WINEBOOT_COMPLETED_GUEST_VERIFIED`. It does not claim persisted registry changes, x86/FEX execution, graphics, or game support.

Reference inspected: GameNative/proton-wine tag proton-11.0-2-20260928, programs/wineboot/wineboot.c and server/registry.c.

Device test: update in place with the SAME permanent signing key, run isolated Wineboot, export session TXT and RAW TXT. Pass requires completion marker + Windows CMD verified=true. Any timeout, missing marker, nonzero exit, or incomplete pipe is inconclusive/failure; use the RAW tail to diagnose the next stage. No Android-device execution has been performed in the development environment.

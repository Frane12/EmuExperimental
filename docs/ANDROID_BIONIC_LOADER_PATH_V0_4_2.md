# v0.4.2 GameNative Proton 11 loader-path correction

## Why we changed the Wine search

Real v0.4.1 Android 16 logs:

```
Wine candidate wine: size=0 (outside ELF probe range)
Wine candidate wine-preloader: size=0 (outside ELF probe range)
Supporting wineserver: ELF64 AArch64 type=3 interp=/system/bin/linker64 [Android/Bionic]
Supporting wineboot: size=0 (outside ELF probe range)
NO_BIONIC_ELF_WINE_ENTRY
```

A follow-up source review of **GameNative/proton-wine**, `build-scripts/build-step-arm64ec.sh`, confirmed the producer creates:

```
ln -sf ../lib/wine/aarch64-unix/wine $OUTPUT_DIR/bin/wine
ln -sf ../lib/wine/aarch64-unix/wine-preloader $OUTPUT_DIR/bin/wine-preloader
```

Therefore the executable may live at `lib/wine/aarch64-unix/wine`, while the archive's `bin/wine` may be a zero-sized placeholder for a symlink in the current import. This is a **candidate explanation**, not proof of the exact payload until the phone inspects it.

## v0.4.2 behavior

- Inspects only known, fixed `bin/wine`, `bin/wine64`, `lib/wine/aarch64-unix/wine`, and `lib/wine/aarch64-unix/wine64` paths from the staged, user-selected WCP. Scans wine-preloader and wineserver for diagnostics only.
- Logs absent paths, empty placeholders, and actual executable ELF/PT_INTERP, selecting the first **Bionic AArch64** Wine executable, never mistaking wineserver for Wine.
- If found, attempts only `wine --version` via Android `/system/bin/linker64`. Existing 12-second watchdog, sanitized fixed command structure, private prefix and bounded logs remain.
- On-device log will reveal either Wine CLI success, linker namespace/SELinux errors, missing dependencies or version mismatch. None of those should be disguised as Windows game execution.
- Adds a synthetic regression test: zero-byte `bin/wine` plus real Bionic ELF at `lib/wine/aarch64-unix/wine`.
- Existing private WCP vault and Wine prefix are preserved on APK upgrade. No reimport required.

**Boundaries:** No actual Wine execution has been verified by v0.4.2 until device test. `wineboot --init`, X11, audio and Turnip ICD activation still need separate integration.

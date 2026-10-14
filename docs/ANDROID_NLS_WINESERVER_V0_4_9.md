# DRNAS Console v0.4.9 — wineserver NLS compatibility experiment

## Confirmed Android 16 results (prior v0.4.8)

- Wine 11 Bionic CLI: real process exit=0, version banner confirmed.
- Wineserver Bionic `--version` through linker64: real process exit=0.
- Direct wineserver ELF execution from app-private writable files: `error=13 Permission denied`.
- Wineserver foreground via `/system/bin/linker64`: output `wineserver: failed to load l_intl.nls`, exits 1.
- No successful Wineboot. PE wineboot and ntdll components are present.
- DXVK/FEX DLLs are staged; no guest window, Turnip Vulkan ICD, or audio.

## Why missing NLS happens

GameNative Wine 11 source, `server/unicode.c`, `get_nls_dir()`:

```c
#if defined(__linux__) ...
    dir = realpath("/proc/self/exe", NULL);
...
    ret = build_relative_path(dir, BINDIR, DATADIR "/wine/nls");
```

`load_intl_file()` then attempts `<derived-dir>/l_intl.nls`.
With Android `linker64 <staged wineserver>`, the executable recorded by the
kernel may be Android's `linker64`, not Wine's wineserver. That can derive
an incorrect NLS directory even though GameNative packages `share/wine/nls`.

This is the leading explanation, not yet proven until an on-device A/B test.

## New experimental change

Add a small, narrowly scoped Bionic native library,
`libdrnas_wine_nls.so`:

- Loaded with `LD_PRELOAD` **only** by the explicitly prestarted wineserver
  child. Never added to the DRNAS app, Wine CLI or Windows guest environment.
- Overrides just `realpath("/proc/self/exe", ...)`, returning the already
  validated absolute path to the user's staged `bin/wineserver`. All other
  realpath calls use Bionic normally through `RTLD_NEXT`.
- The Wine server can then derive `<staged-WCP>/share/wine/nls`, its normal
  relative data directory.
- Refuse prestart if the staged Proton `share/wine/nls/l_intl.nls` is missing,
  unexpectedly small/large, or the shim is not available.
- Force extracted APK native libraries so the shim is accessible by an
  absolute path under the app's nativeLibraryDir.
- Preserve the direct-exec denial diagnostic, wineserver version test,
  disposable Wine prefix and shutdown/timeout protections.

This does **not** elevate privileges or bypass Android's executable-storage
restrictions: the user-provided Wine server ELF is still interpreted as data
by the already verified Android Bionic linker. It modifies the reported
source executable pathname only to compensate for that extra linker stage.

## Success criteria

- `l_intl.nls exists` should appear.
- Wine foreground wineserver remains running long enough for boot probing.
- If Wineboot continues to fail, show exact status; do not claim a Windows
  process. If it exits 0, registry mutation is still required as evidence.

### Build/signing note

Source changes can be built in CI with a temporary debug certificate, but
that certificate **cannot** update an already installed permanently signed
DRNAS APK. Use the *same offline key*, never put the key in Git history.

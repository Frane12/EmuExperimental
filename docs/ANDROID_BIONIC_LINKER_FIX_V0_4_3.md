# v0.4.3 — Android/Bionic `linker64` argument correction

## Real phone result (v0.4.2)

```
Selected actual Bionic Wine entry: lib/wine/aarch64-unix/wine
TRY REAL BIONIC WINE --version via Android linker64
[wine stdout] error: expected absolute path: "--library-path"
Wine --version real process exit=1
```

**Root cause in our wrapper:** We incorrectly treated Android's Bionic linker like GNU's `ld-linux-aarch64.so.1`, passing `--library-path` as argv[1].

Android Open Source Project: `platform/bionic/linker/linker_main.cpp`, direct linker invocation accepts `linker64 [--list] PROGRAM [ARGS...]`. For a normal run argv[1] is expected to be the absolute ELF executable path. This matches the observed error; Wine itself did **not** get control.

See: https://android.googlesource.com/platform/bionic/+/main/linker/linker_main.cpp

## v0.4.3 change

Correct launch command:

```
/system/bin/linker64 /data/user/0/com.drnas.console/files/.../lib/wine/aarch64-unix/wine --version
```

Use the child environment variable `LD_LIBRARY_PATH` for Proton `lib`, `lib/wine`, `lib/wine/aarch64-unix`. This is how the Bionic linker sources path lookup. All paths are constructed from previously staged/checked components.

The 12-second watchdog, stdout/exit capture and `wine-X.Y` banner verification remain. Added unit regressions to prevent GNU-style `--library-path` returning.

**Expected next failure, if any:** Android 16 SELinux may forbid execution/mapping of ELFs from writable app data. Next step if so is a developer-bundled, pinned, approved native executable in app's native library directory (not chmod or an executable from user-writable data). Another possibility is missing Android native dependencies or namespace isolation. These are hypotheses, pending device log.

**Security boundary:** The code will attempt only `wine --version` with a verified Bionic AArch64 entrypoint and fixed arguments. No game launch, no Wine desktop, no X11/Turnip ICD, and no workaround of Android platform execution restrictions is included.

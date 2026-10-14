# DRNAS v0.4.6: Wineboot early exit diagnostics

## Device-confirmed facts (2026-10-14)

- GameNative Proton Wine ARM64/Bionic binary works with Android linker64: `wine-11.0`, exit 0.
- `wineboot.exe --init` in disposable prefix under v0.4.4 reported `wine: could not exec the wine loader`, exit 1.
- Wine second-stage environment flag `WINELOADERNOEXEC=1` in v0.4.5 got past that particular printed error but the process exited **127 without a diagnostic message** and without registry changes. Registry files already existed in the prefix template: existence alone is not evidence.
- 127 alone does *not* identify a failed wineserver spawn, missing Wineboot PE, or loader fault. Do not claim it does.

## v0.4.6 changes

In the opt-in disposable-prefix Wineboot test, before any PE guest launch:
1. Log candidate built-in `wineboot.exe`, `ntdll.dll` modules in `lib/wine/aarch64-windows`, `lib/wine/arm64ec-windows`, `lib/wine/x86_64-windows`, plus the prefix `drive_c/windows/system32`; report absent, empty, PE/MZ and ELF with file length. A matching PE module anywhere is useful evidence but not proof Wine can load it.
2. Run a bounded **real Bionic wineserver --version** with `/system/bin/linker64` and a **7-second watchdog**. This is a nonpersistent CLI-only operation. It does not try to start Wine's background server.
3. Correct `WINEDLLPATH` to the base `lib/wine` directory, rather than `lib/wine/aarch64-unix`: in GameNative's `dlls/ntdll/unix/loader.c`, `get_pe_dir` appends machine-specific `/aarch64-windows` and `set_dll_path` treats `WINEDLLPATH` entries as bases.
4. Preserve the existing 50-second Wineboot watchdog, the `WINELOADERNOEXEC=1` experiment and strict registry mutation checks. An unchanged prefix and exit 127 are failure, not Wineboot success.
5. Report `WINEBOOT_EARLY_EXIT_127` and compare with the wineserver and PE inventory; avoid asserting an unverified root cause.

## Read the next log

- `wineserver --version exit=0`: independent Bionic server executable can run through linker64. This **does not imply** Wine can spawn wineserver automatically.
- `SERVER_VERSION_EXIT_127`: server fails before boot; investigate shared-library/ELF loader compatibility.
- `aarch64-windows/wineboot.exe: absent`: examine whether the package stores the built-in PE elsewhere or the template supplies it. Do not launch a substitute executable blindly.
- `Wineboot launcher exit=127`: use all preceding diagnostics before changing any loader logic.

**Still missing:** actual Windows PE guest success, graphical server, audio runtime and Turnip Vulkan ICD activation. No changes to the existing WCP vault or regular Wine prefix.

Source inspected in vendor project: `GameNative/proton-wine`, `dlls/ntdll/unix/loader.c` (`set_dll_path`, `get_pe_dir`) and `server/main.c` (`--version`).

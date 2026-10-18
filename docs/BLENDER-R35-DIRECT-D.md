# Blender DRNAS r35: direct Android project drive

At first launch select a **local folder** using Android's folder picker, for
example `Internal storage/Documents/Blender`. The same folder appears inside
Blender's Save/Open dialog as **D:\\**. Files, backups, textures and renders
written there live directly in that Android folder; there is no copy queue.

The choice and persisted read/write grant survive restarts. When Android does
not permit native filesystem access using the tree grant alone, the app explains
why Wine needs direct filesystem I/O and offers Android's **All files access**
setting. This is a broader permission than the selected folder; the D: mapping
itself points only to the selected folder. Denying it never starts a pretend
private D: mirror. Select another accessible local folder or close the launcher.

Cloud DocumentsProviders cannot be converted into a Unix directory. Continue
using the explicit Android export action for cloud destinations. A revoked grant,
removed card or unwritable folder returns to folder setup. Android 10 uses the
legacy storage permission path; Android 11+ uses the special settings permission
only after the actual directory access check fails.

Before Wine starts, test listing, create/read/write and rename using unique
temporary files, remove those files, then map prefix/dosdevices/d: to the chosen
directory. Do not overwrite an unowned existing D: mapping. Subsequent mapping
changes replace only the app-owned symlink and preserve all project files.
The Blender menu displays the real Android path and offers changing the folder
with an explicit restart notice. Save any work before changing it.

Blender starts with D: as working directory. For a new unsaved scene its first
file browser defaults to D: when it would otherwise default to C:. A saved
project's own location and subsequent navigation are preserved. Existing files
saved in Wine C: by r33 are left in place and remain available for export.

## GPU payload

Replace the older A740-only R8 package with one shared upstream-Mesa build:

- Author: StevenMXZ, `Turnip_v26.3.0-R6.zip`, release `v37`, 30 September 2026.
- Source: https://github.com/StevenMXZ/Adreno-Tools-Drivers/releases/tag/v37
- SHA-256: `5fa2305eb9eff64d9347a670e6ca5ef7c518f60e9e782a4db7ee5588c9852d81`.
- Actual ZIP metadata: minApi 28, libraryName `libvulkan_freedreno.so`.
- Release notes cover A6xx/A7xx and upstream A710/A720/A722 support.
- Known device-name allowlist includes A740; never apply this family driver to
  A8xx or Mali. Existing A810/A830/A840 and Mali-G52 payloads remain separate.
- Pinning and successful ELF validation do not establish Blender compatibility
  or driver activation on any target device. Confirm with on-device logs.

## Lean Blender session

Skip the game-specific Crysis guard, XInput native shim, D3D cube installation,
Paint staging, Mono installer, wallpaper setup and FEX tuning in Blender mode.
Remove the Mono MSI download from this Blender APK workflow. Preserve Wine's
ABI modules, DXGI architecture recovery, Vulkan/OpenGL, fonts, audio, keyboard,
diagnostics and the existing general DRNAS fallback. Do not delete user files,
installed programs or arbitrary Wine DLLs. A truly stripped Wine distribution
requires dependency traces and on-device regression testing; r35 does not claim
to be a minimal custom Wine build.

## Verification

JUnit exercises real direct write/reopen, owned remapping, refusal to replace
unowned drives, document-ID traversal rejection, Unicode paths and GPU family
isolation. Python tests check pinned payloads and validate ZIP/ELF metadata.
Android permission revocation, first-run/restart behavior, actual Blender
Save/Open, SD cards and GPU rendering require device tests.

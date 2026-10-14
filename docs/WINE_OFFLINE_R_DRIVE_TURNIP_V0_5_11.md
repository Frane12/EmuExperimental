# DRNAS 0.5.11 — offline engine, Windows R:, and Turnip preference

## What's implemented

- **At APK build time:** fetch and embed Proton 10 ARM64EC, FEXCore 2609 and
  DXVK 1.10.3 WCP archives as assets. Build prints package SHA-256. Device
  installation requires no file picker, account, network connection, or
  manual WCP import.
- **First launch:** install each asset in the private content-addressed vault,
  verify SHA-256, compression, and FEX PE bridge pair. Preserve existing vault
  archives, prefer reproducible rollback over destructive repair. Start
  isolated Wine initialization automatically. On a successful bootstrap
  open the embedded X11 GUI automatically.
- **Custom Drnas Turnip:** at build time, inspect only **published** releases
  of `Frane12/Testturnip` for per-GPU A810, A830, A840 AdrenoTools ZIPs.
  Bundle available variants after ARM64 ELF, ZIP and `meta.json` validation.
  On device, probe the **system Vulkan deviceName** to select an exact GPU
  family. Unknown devices use the system driver. Generate a private ICD JSON
  with the selected `libvulkan_freedreno.so` and request it in Wine's
  environment (`VK_DRIVER_FILES`, `VK_ICD_FILENAMES`). **Important: preference
  is not proof the actual Vulkan loader used Turnip**. A compatible loader or
  AdrenoTools hook and a real Vulkan physical-device probe in a Wine child
  process are still required to establish activation. Never report installed
  as running.
- **Winlator-style file explorer:** the original Winlator starts
  `C:\\windows\\wfm.exe` (Winlator File Manager). Its GNU
  GPL-3 licensed Banner File Manager derivative is also available.
  Our first implementation instead opens **Wine's included `winefile.exe`**
  inside the current working ARM64 Wine/X11 session. This avoids bundling a
  new x86-64 executable before its x86 translator is verified.
- **Windows R: drive:** Wine `dosdevices/r:` is a symlink to private
  `files/windows-drives/shared`. On first launch, Android asks whether to
  choose a SAF document tree. After explicit consent, a one-way import copies
  the chosen directory into that private mirror. Users can initiate import
  again via Components. It does not overwrite Wine-local files or upload
  modifications back to Android. The Wine folder remains usable even if
  permission is declined.

## Android Downloads limitations

On Android 11+, Android's official Storage Access Framework explicitly
blocks granting an app access to the **root Download directory** using
`ACTION_OPEN_DOCUMENT_TREE` even when the user wants to grant access.
Choose an individually selectable subfolder such as `Download/DRNAS`
or a folder under Documents instead. The resulting `content://` URI is
**not a POSIX path**; making a Wine `dosdevices` symlink directly to it
would be misleading and nonfunctional. Copying into private app storage
makes files visible to Windows in a non-root emulator and avoids requesting
`MANAGE_EXTERNAL_STORAGE`.

The importer enforces depth, file-count, total-byte and traversal guards
and intentionally does *not* delete local files. It is not a live mount.
A future read/write SAF bridge would need a proper filesystem implementation
and explicit conflict-resolution/permission handling.

## Sources / implementation precedent

- Winlator WineUtils — `createDosdevicesSymlinks`:
  https://github.com/brunodev85/winlator-app/blob/main/app/src/main/java/com/winlator/core/WineUtils.java
- Winlator file manager: https://github.com/brunodev85/wfm (MIT).
- Banner file manager: https://github.com/The412Banner/banner-file-manager
  (GPL-3.0-or-later; builds x64 `wfm.exe`).
- Android SAF restrictions:
  https://developer.android.com/training/data-storage/shared/documents-files
- AdrenoTools Vulkan loader API:
  https://github.com/bylaws/libadrenotools

## Outstanding verification

- CI must download and inspect the complete WCP set and assemble the APK;
  signing must use persistent user-owned GitHub Actions secrets.
- First-launch consent and 4 GiB bounded import on an Android 16 device.
- Confirm `winefile.exe` is included in the selected Proton package and R:
  appears in its drives; choose a known-good package if not.
- Verify `vkEnumeratePhysicalDevices` / deviceName **from Wine's child
  process** with requested ICD before marking Turnip enabled. A/B compare
  against system Vulkan and fail back to system on incorrect devices.
- Confirm true guest program launch and that imported file edits don't leak
  back to shared Android storage without consent.

This is experimental code: passing code review is not a successful APK build
or an Android on-device test.

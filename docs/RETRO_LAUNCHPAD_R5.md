# EmuDrnas preAlpha 0.1 r5: Retro Windows launchpad

Development branch: `feat/emudrnas-retro-shortcuts-tools-r5` (based on full-screen r4).

## Implemented

- Console: original drawn meadow illustration in the spirit of early-2000s desktops, neon glass panels, and a functioning **Shortcuts** tab; the old “Ready when you are” headline is removed.
- Default shortcut tiles: **Notepad**, **WordPad**, **DRNAS Paint**, **DRNAS Pad**, **Task Manager**, Calculator, Control Panel, CMD, Wine Settings and Wine File Manager.
- DRNAS Paint and DRNAS Pad are **source-owned Win32 applications**, compiled by MinGW in GitHub Actions and installed into `C:\DRNAS\Tools\` before Explorer starts.
  - Paint provides a GDI drawing canvas, brush/palette/eraser, and **Save BMP**.
  - Pad provides UTF-8 plain-text **New/Open/Save/Save As**, undo, cut/copy/paste. It is *not* a complete RTF implementation.
- Native Wine tools are launched with the same prefix/environment as Explorer (not Android stand-ins).
- The app enumerates existing `.lnk` files from each Wine user's Desktop and Start Menu, plus the machine Start Menu, with bounded traversal and canonical path validation. Tapping one requests Wine `start.exe /unix <validated .lnk>`; desktop launch has to be validated on device.
- AkelPad/Notepad++ and games are **not falsely labelled pre-installed**. Install them inside Wine and create a Desktop/Start Menu shortcut; refresh the Shortcuts tab.
- The relative camera's fresh-install default gain is increased from 5.0 to 7.5; an existing user's chosen gain is preserved.

## Test checklist (device validation still required)

1. Install signed r5 *over* compatible signed r4 without losing the Wine prefix.
2. Verify the meadow, shortcut icons, all three tabs and power/Explorer/DXVK/A840 controls.
3. Boot Wine, launch DRNAS Paint; draw and save BMP, reopen on C:/R:.
4. Launch DRNAS Pad, type/edit, save/reopen UTF-8 TXT.
5. Launch Notepad and Task Manager. WordPad and other legacy Wine tools may depend on the Proton/Wine build.
6. Install an independent Windows program, create a Desktop `.lnk`, return to Android, refresh Shortcuts, and verify it launches.
7. Test large and complex Start Menu trees for scan latency and file path safety.
8. Check full-screen rendering and camera response; use 5-second render log A/B if needed.

## Signature / upgrade requirement

APK publishing is blocked until all four repository Actions secrets are configured:
`DRNAS_KEYSTORE_BASE64`, `DRNAS_KEYSTORE_PASSWORD`,
`DRNAS_KEY_ALIAS`, `DRNAS_KEY_PASSWORD`.
The branch's CI deliberately **does not** publish a temporary-key APK.
Use the existing `tools/setup_permanent_signing.sh` on the owner's trusted computer,
or restore the exact original signing identity if the installed build used it.

Android in-place updates require the same application ID, the **same signing certificate**, and an increase in versionCode (r5 uses **70**). A new permanent key cannot update an already-installed APK signed with a different transient key; back up its private data before any migration. Do not uninstall to work around a signature conflict.

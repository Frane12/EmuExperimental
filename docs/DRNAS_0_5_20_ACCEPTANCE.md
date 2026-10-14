# DRNAS 0.5.20 — persistent Windows profile, prerequisite installers, SAF refresh

## Source branch
This change is based on `feat/desktop-audio-turnip-reset-v0.5.19`.

## Windows profile
On the first isolated Wineboot, provision `C:\\users\\xuser\\Documents` and
`Documents\\My Games`, Desktop, Downloads, Saved Games, AppData and common
Windows application folders. Re-run the check when Wine Explorer is opened.
Never remove existing saves or follow symlinked user directories.

## Persistent prefix
The previously disposable `wineboot-probe/template/.wine` is now preserved
after successful Wineboot + independent guest verification. Its marker is scoped
to the verified staged Proton package and prefix archive. A runtime mismatch or
corrupt registry blocks reuse rather than silently deleting user data. Only
unverified initial test prefixes can be reconstructed. This does not migrate
data between Android application IDs (ephemeral CI LAB builds have separate IDs).

## Wine Mono
The CI bundles the upstream **Wine Mono 11.3.0 ARM64 MSI** from
`wine-mono/wine-mono` (Aug 2026) and checks SHA-256
`b0c47d374efaf22cbff76cb10488ac2d1627b067e085aef7acec573884120e0f`.
On first GUI start, after wineserver startup, Wine invokes
`msiexec.exe /i C:\\DRNAS-Setup\\wine-mono.msi /qn /norestart`.
Only an exited installer (0 or 3010) records the component SHA in the
persistent prefix. **Installer exit alone is not functional validation**;
a real .NET application must still be tested on ARM64EC.

## Microsoft Visual C++ Redistributable
A separate Android picker accepts a user-provided official x64 EXE. The app
copies it into its private vault and invokes it on next Wine GUI session via
`/install /quiet /norestart`. Repeat sessions reuse its per-prefix SHA status,
except after a failed install or when the file changes. The VC Redist is NOT
bundled in the APK in this branch and must be user supplied.

## SAF / R: drive
The Android folder grant is persisted by `ACTION_OPEN_DOCUMENT_TREE`, not an
ordinary POSIX mount. The existing private `R:` mirror gains a serialized
background incremental scan every 20 seconds while the Wine desktop runs.
Newly added folders become visible without asking for permission again.
Winefile may need refresh to repaint its listing. The mirror remains import-only:
existing files are never overwritten, removed, or written back to the Android
source folder. Current safety limits remain 4 GiB newly copied per pass,
4,096 entries and nesting depth 8. These limits mean very large game folders
are NOT equivalent to a transparent live mount.

## Acceptance on device
1. Start Wine once and verify `C:\\users\\xuser\\Documents\\My Games` exists.
2. Create `Documents\\My Games\\test.txt`, stop/reopen Wine, check preservation.
3. In Android, create a new folder under the granted tree while desktop runs.
   After ~20 sec, refresh winefile `R:` and verify the folder appears without
   re-selecting the SAF tree.
4. Confirm Wine Mono install launches once and does not repeat after success.
5. Import official VC Redist x64, reopen Explorer; verify installer attempt,
   process exit and status persistence across a new Wine session.
6. Test game launching / save creating separately: Far Cry 2 reaching its splash
   screen does not by itself prove that missing Documents caused its stall.
7. Inspect CI unit tests, logs and transient/stable signature warning before
   using the APK. An unsigned or differently signed build cannot upgrade an
   installed package of the same Android ID.

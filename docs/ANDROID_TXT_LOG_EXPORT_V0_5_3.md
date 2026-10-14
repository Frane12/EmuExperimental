# DRNAS Console v0.5.3 — complete session TXT export on Android

## User request (October 14, 2026)

Export the console's diagnostic log as a real `.txt` file that the user can
save on their Android phone (e.g. Downloads) and attach in a ChatGPT message.

## Details

- `MainActivity.append` now appends all emitted session-event lines,
  timestamped, to an app-private UTF-8 text file under
  `getFilesDir()/logs/DRNAS-console-<timestamp>-<random>.txt`.
- Every line is flushed; the file is not pruned when the Diagnostics TextView
  trims its old lines after ~30 KB. This ensures later export includes the
  *whole session event history* since this version started.
- A new prominent `⬇ Export complete session log (.txt)` button exists in
  **Diagnostics** and a convenient button in the **Console** section.
- Uses Android `ACTION_CREATE_DOCUMENT`, MIME `text/plain`,
  suggested filename `DRNAS-Console-YYYY-MM-DD_HH-mm-ss.txt` and streams the
  private file to the document URI with `ContentResolver.openOutputStream`.
  The user can select Downloads, Documents, Drive or any writable provider.
- No `WRITE_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` permission.
- A canceled picker does nothing. A failed export is clearly logged and
  reported with a Toast. The private file is retained even if export fails.
- Recording is serialized on the UI thread and the log-copy operation is
  synchronized; existing worker callbacks already dispatch via runOnUiThread.
- The visible 'Clear visible log' button only clears the view, not persisted
  diagnostic history. This is intentional.
- On app process restart, a fresh private per-session log file is created.
  Previously installed app versions did not record full session files.
- **Important precision:** if a subsystem itself suppressed Wine debug lines
  (e.g. `[wineboot] output total=23733, retained=81`), those suppressed
  lines were never delivered to `MainActivity.append`, so the exported
  *event log* does not contain them. A separate Wine raw-trace capture would
  be needed for all native stdout lines.

## Current verified Wine progress

Phone v0.5.2 showed the child relay:
`[DRNAS child] execv Wine -> Android linker64`, followed by Wine's
`NtCreateUserProcess wineboot.exe pid/tid handles` and successful PE module
mapping. The Wine launcher exited **0**, but all three registry fingerprint
sizes were unchanged and the state remained
`WINEBOOT_EXIT_0_REGISTRY_UNCHANGED`. That is *not* a completed prefix
initialization, and we deliberately don't change this interpretation in
v0.5.3. Wine PE trace logging remains bounded by the existing filter.

## Regression checks

`SessionLogStoreTest` verifies UTF-8 and complete export from a stream
larger than 30KB, zero-length export, and safe close.

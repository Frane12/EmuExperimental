# DRNAS Console v0.5.8 — auto GUI and reliable log export

The device screenshot shows a verified X11 handshake and root cursor, with toolbar controls underneath system status icons. A visible Winecfg window is not confirmed by that screenshot.

Changes:
- Apply status/navigation/cutout insets to Main and GUI roots; keep controls outside Android system bars. Horizontal toolbar scroll prevents button overflow.
- Save GUI TXT directly to Downloads/DRNAS using app-owned MediaStore.Downloads rows on Android 10+. Pending rows are published only after a complete locked trace snapshot; failed rows are removed. Show immediate progress and success/failure messages. Long-press Save GUI TXT retains Android Save as fallback; older Android uses that fallback.
- On successful Wineboot verification, open GUI automatically after initialization completes. If Main is backgrounded, defer until onResume; consume the pending launch once, so Back does not relaunch.
- Keep v0.5.7 fixed log panels and manual Latest controls.

Validation: JVM trace tests cover exports while recording and after close; full Android build runs in CI. Device acceptance: initialize once, verify automatic GUI open, tap Save GUI TXT, locate a non-empty Downloads/DRNAS/DRNAS-Wine-GUI-*.txt. Return to Main and check no automatic relaunch. Visible Winecfg remains a separate on-device milestone from the X11 root/cursor.

# Blender r54 — private input/focus diagnostic build

Based on r53, versionCode 122, same package and permanent signing identity.

- Fix quiet-mode filtering that discarded INPUT and BUTTON evidence in r53.
- Record pointer target, delivered window, focus requests/applied focus, grabs and queued events.
- Record Android focus changes and lifecycle events.
- Sample input worker, UI responsiveness, X11 request progress, PutImage and Canvas counts every 2 seconds for the first 6 minutes. Snapshots do not acquire the global XServer lock.
- Include a current snapshot in exported logs. No forced focus, repaint or recovery changes.
- Retain Vulkan, existing GPU paths and Xclipse 940 firmware Vulkan selection.

This is diagnostic instrumentation, not a confirmed freeze fix. Canvas counts are not proof of new Vulkan frames. No device runtime test has been performed.

Test: launch Blender, wait for the main view, click once, wait 10 seconds after the freeze, export the complete log before resetting or closing the app.

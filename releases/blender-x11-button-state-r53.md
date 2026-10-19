# Blender r53 — PRIVATE X11 button-state test

Experimental follow-up to r52, versionCode 121. Device verification required before publication.

The 09:51 log confirms the first queued touch events complete in 0–1 ms. It does not establish a native Blender crash, a GPU hang or successful Blender Vulkan presentation. Moving input off Android UI alone did not resolve the reported freeze.

## Changes
- X11 ButtonPress/ButtonRelease now report the logical button/modifier state immediately before the event, as required by the X11 core protocol. Previously the state after the transition was sent.
- Deferred grabbed button events capture their own state instead of consulting the current state when the queue drains.
- A passive pointer grab now delivers its activating press, including synchronous grabs, before freezing subsequent pointer delivery.
- Absolute touchscreen ACTION_CANCEL releases held buttons to avoid leaving a drag pressed after Android cancels a gesture.
- Expand queued/done/stall coverage to the first 24 input tasks and record the first 32 real button transitions, including grab ID, frozen state and pending count.

Keep r52 asynchronous input, Vulkan renderer, A840 H2.9 selection, Xclipse 940 firmware Vulkan routing, main-only fullscreen and auxiliary window frames. No GPU compatibility claims added.

## Verification
JVM regression tests exercise pre-event button state with modifier keys, chords, tap press/release and captured deferred state. Existing Blender geometry tests remain enabled. CI must pass and the same permanent signing certificate must be verified before sharing the APK. Root cause on the device remains unconfirmed.

## Device acceptance
Update in place; reset Wine, start Blender, toggle diagnostics before the first tap, try a normal click, drag, Preferences and Render window, then export the complete log. Confirm button press/release transitions and continuing Blender redraws. Do not publish this draft until Frane has tested it.

Protocol reference: https://xorg.freedesktop.org/releases/current/doc/xproto/x11protocol.html

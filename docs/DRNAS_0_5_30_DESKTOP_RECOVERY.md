# v0.5.30: desktop recovery and host crash evidence

The supplied v0.5.29 device log confirms Adreno 840 using Mesa Turnip,
driverID=18, and DRNAS_TURNIP_DEVICE_VERIFIED. Wine Explorer creates
winefile.exe and maps four X11 windows. The exported log ends without a
terminal Wine exception, fatal X11 error, or Android host stack trace.
It cannot establish the cause of the reported immediate application exit.

Correct two concrete X server lifetime defects:
- Window.destroy must not recycle bitmap storage while Android HWUI may still
  retain a display-list reference. Remove the destroyed window from client
  bookkeeping so GC can release it after HWUI stops referencing it.
- XServer.reset must remove entries through its iterator rather than mutating
  the Hashtable during iteration (which can throw ConcurrentModificationException).

Neither defect is claimed as the confirmed cause of this device failure.

Guard Java Canvas draw failures: preserve the original exception, restore the
Canvas state and retry CPU desktop composition once. If that also fails, keep
Android controls available. Vulkan/Turnip selection is unchanged. Native
RenderThread/GPU process crashes cannot be caught by this Java draw guard.

An Application-level handler retains Java host crashes across restarts and
delegates to Android's default handler. Android process-exit history adds native
crash, low-memory and ANR reason/status evidence on Android 11+. The Console
button “Pošalji zapis pada / zadnjeg desktopa” works without reopening Explorer
and exports retained host diagnostics plus the previous GUI trace. Desktop log
exports include the same saved host reports. No existing reports are erased on
startup; Wine startup may create its usual new GUI trace.

Validation: JVM report-preservation/bounds tests plus full CI APK compilation.
On-device confirmation remains required. Start Explorer; if it exits, restart
only the app and use the Console crash-report button before reopening Explorer.

## Additional reproduction detail: first mouse movement triggers exit

The user narrowed the trigger to the first pointer movement after Explorer opens.
Window.getCursor() previously recursed to _parent.getCursor() whenever a cursor
was None, including the root whose parent is null. Root Cursor=None is legal
and must resolve to the server default. Resolve inheritance iteratively with
that fallback, and allow movePointer to hide an absent bitmap rather than
null-dereferencing it. Guard pointer event dispatch and retain its original
exception separately; restore a valid motion origin after a failed event.
This concrete bug matches the trigger; on-device confirmation is still required.

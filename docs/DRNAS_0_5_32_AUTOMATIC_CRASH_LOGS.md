# v0.5.32 automatic failure reports

Detected Java host crashes, renderer/pointer exceptions, X11 request errors,
fatal XIO/disconnect resets, unexpected desktop termination and failed manual
Vulkan tests automatically save a diagnostic TXT to Downloads/DRNAS using the
same scoped-storage export as the manual log button. No share sheet opens.

Reports include saved host exceptions, Android process-exit history, Wine GUI
and Wineboot traces, game log snapshots and bounded DXVK logs. X11 runtime
exceptions now include their full Java stack in the GUI trace.

Persist a private fsynced report before publishing it to Downloads. A failed
export remains pending and is retried at startup. Retain at most eight pending
reports and throttle ordinary failure snapshots to one per ten seconds.
Unhandled Java crashes bypass the ordinary throttle and retain Android's
default crash handling. Successful automatic exports keep timestamped names.

A desktop session marker survives abrupt process termination. On next app
startup, snapshot the previous trace before any new desktop can truncate it,
then publish in the background. An unfinished session is labeled as such;
it may represent a native crash, force stop, low-memory kill or power loss,
not necessarily an application defect. Normal desktop destruction clears the
marker. RAW output is flushed during activity at least once per second and
on an explicit failure trigger; abrupt death can still lose buffered bytes.

Device checks: trigger a failed Vulkan test and inspect Downloads/DRNAS;
test a Java host failure; force-stop an active desktop and reopen the app to
verify recovery; confirm ordinary X11 enumeration probes do not flood files.
Build/unit-test success does not establish these Android device behaviors.

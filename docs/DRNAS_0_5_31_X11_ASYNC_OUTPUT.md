# v0.5.31: confirmed mouse/click crash fix

The v0.5.30 host report confirms NetworkOnMainThreadException on Android's
main thread, at SocketOutputStream.write -> BufferedOutputStream.flush ->
InputOutput.flush. Both EventCode.sendMotionNotify and sendButtonPress reach
this path from ScreenView's Android touch callback. The v0.5.29 direct-flush
change introduced the regression; the v0.5.30 cursor/lifetime fixes did not
address this confirmed cause.

Wrap the socket output under BufferedOutputStream in X11OutputQueue. One
dedicated writer per connection owns every socket write/flush, including
buffer spills before explicit flush(). Producers copy/enqueue bytes and never
join or wait for socket delivery. Preserve wire order and prevent callers'
buffer reuse from mutating pending events. Keep Android StrictMode enabled.

Bound pending/in-flight data at 8 MiB and 512 queued chunks per connection.
Disconnect a backlogged client rather than block input, drop partial protocol
messages, or grow without bound. Socket close cancels blocked output; no
thread join occurs on close. Writer I/O errors close the connection and are
reported by subsequent operations. The underlying InputOutput and EventCode
locking still protects message assembly.

Local JVM tests exercise click/motion wire order and buffer ownership, verify
that actual output runs off the caller thread on a single writer, simulate a
blocked peer without blocking producer flush, validate bounded failure, and
verify close cancels a blocked writer. CI runs the full JVM suite and APK build.

Device acceptance: open Explorer, move the pointer and click Start repeatedly.
Then run the D3D cube. If the app exits, export the retained Android report
from the Console before reopening Explorer. Device confirmation is required;
the confirmed stack establishes the old failure, not automatic proof of the
new build's stability.

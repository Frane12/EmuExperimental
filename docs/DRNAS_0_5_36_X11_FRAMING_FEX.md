# v0.5.36: X11 stream framing and FEX Performance

The supplied v0.5.35 device trace verifies Adreno 840 / Mesa Turnip enumeration,
DXVK 1.10.3 adapter/device creation, D3D11 swapchain creation, and the cube's
`DRNAS_CUBE: D3D9 device and swapchain created` marker. Both tests subsequently
lose their X connection; no successful cube Present marker follows. Android
process exit history is empty. This is not proof of an Android crash, resolution
mismatch, or GPU execution fault. The many 0x40010006 exceptions are debug output
notifications, not themselves unhandled crashes.

## Concrete transport defect and repair

`InputOutput.readSkip` previously limited requested skips to `available()`.
TCP has no message boundaries: padding/body bytes may arrive later. Leaving
those bytes unread can shift every subsequent request header. This defect is
confirmed in source; its role in this particular device failure is a hypothesis.

Receive complete bounded request bodies before acquiring the XServer monitor.
Handlers consume only that request's body, so overruns cannot eat the next
header and unconsumed bytes are logged and discarded. Skipping now consumes
exactly the requested bytes and reports truncation. BIG-REQUESTS advertises a
16 MiB limit matching the parser, with unsigned length/overflow validation.
The existing ordered output worker remains in use. Diagnostics identify the
request phase, writer failure, and relevant extension queries. Receiving the
body outside the server monitor also avoids holding the whole display lock
while waiting for a slow client's request data.

## Comparison with other Winlator implementations

Bannerlator's XInputStream reads from a buffered active request view, and XClient
tracks bytes remaining in a request. The repair applies the same framing
principle to our existing TCP server; it does not import Bannerlator code.

- https://github.com/The412Banner/Bannerlator/blob/main/app/src/main/java/com/winlator/star/xconnector/XInputStream.java
- https://github.com/The412Banner/Bannerlator/blob/main/app/src/main/java/com/winlator/star/xserver/XClient.java

The six FEX values match Bannerlator's Performance preset: TSO, vector TSO,
memcpy TSO and half-barrier TSO are 0; reduced x87 precision and multiblock are 1.
Apply after dependency installation, before explorer/game launch. These are
requested bridge settings; this patch does not prove they are honored by the
bundled FEX bridge or improve FPS. This profile relaxes memory ordering and x87
precision, so game compatibility must be checked.

- https://github.com/The412Banner/Bannerlator/blob/main/app/src/main/java/com/winlator/star/fexcore/FEXCorePresetManager.java

## Validation and device acceptance

New socket tests exercise delayed TCP padding, request-boundary overreads,
unconsumed body bytes, and truncated bodies. The Android CI workflow runs JVM
tests and builds/verifies/signs the APK before creating a private draft release.

Install v0.5.36 LAB alongside existing versions. Open Test Direct3D, then the
external D3D11 test. Confirm visible animated frames and the first successful
cube Present marker; export the log whether successful or unsuccessful. A
remaining failure should now show named WSI extension queries and output-worker
errors. Stable device rendering is unverified until that run. Full DRI3/Present
and an accelerated compositor are not implemented by this transport repair;
the existing Mesa GPU-readback / X11 PutImage path remains experimental.

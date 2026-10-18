# DRNAS EMU r24: code and performance review

Base: `fix/r23-compositor-window-stability`, tree `be72dfd4f0e536083506a1c88ae10c983e2a6e12`.

This review inventories the complete repository and examines the Android Java, native bridges, runtime provisioning, launch, input, rendering, and packaging paths. It is a static source review plus host regression testing, not an Android profiler trace or proof that every possible optimization has been exhausted. The base has approximately 28,000 Java source lines across 123 pre-existing Java files, six native C files, Python packaging/import tools, Windows guest tools, contracts, and a legacy web prototype. Binary dependencies are inspected by their manifests/PE architecture and integrity checks; their third-party source trees are not contained in this repository.

## Implemented changes

| Area | Finding | r24 behavior |
| --- | --- | --- |
| X11 receive | Every request allocated a new full request body; ByteArrayInputStream synchronized access remained inside request handling | Reuse a bounded per-client request buffer, with unsynchronized single-reader access; discard retained buffers over 8 MiB after handling |
| X11 pixels | Already-framed BGRX data copied again into per-drawable byte staging before conversion | Decode directly at the current request offset into retained ARGB pixels; remove staging bytes and preserve scalar/bulk decoder choices |
| Composition scheduling | Every tile could enqueue another Android invalidation | One pending hardware redraw until onDraw begins; retain all X11 commands and bitmap writes, preserve software fallback rectangle invalidation |
| HUD | /proc, KGSL and battery Binder calls executed on UI thread | Perform sampling on one bounded-in-flight background job per HUD; publish only the finished snapshot on UI thread |
| HUD CPU | Linux guest CPU fields counted a second time | Count user through steal; exclude guest and guest_nice already included in user/nice |
| Warm DXVK setup | Target DLLs read fully into temporary byte arrays just to hash them | Stream target hashes with a 64 KiB buffer |
| PutImage validation | Dimension products used int arithmetic | Reject overflowing or oversized 24-bit pixel dimensions before allocation; treat empty updates as no-op |
| Diagnostics | PutImage read timing incorrectly described socket receive time | Label framed payload access correctly; actual transport reception happens outside the server lock |
| APK | Archives could be compressed again inside APK | Store ZIP/WCP/MSI assets without outer compression; build asserts Blender ZIP is stored and hashes correctly |
| Blender | Manual R: import and manual executable/profile setup | Pinned offline ARM64 payload, verified transactional extraction, builtin shortcut, native Vulkan default, portable preferences |

For a 1280×720 BGRX request, r24 removes a 3,686,400-byte allocation per request after warmup and a second 3,686,400-byte raw-pixel copy. At 40 such requests/s this avoids approximately 140.6 MiB/s of allocation and 140.6 MiB/s of copied payload (about twice that copy volume in memory reads+writes). These are byte-accounting estimates, not measured Android throughput or FPS gains. At 800×600 the corresponding figure is about 73.2 MiB/s at 40 requests/s. ARGB conversion and Bitmap.setPixels remain necessary in this path.

## GPU to display path

Current path remains application Vulkan → Wine/Mesa X11 readback → TCP PutImage → Java ARGB → Android Bitmap → HWUI composition → Android display. Vulkan rendering succeeding does not imply direct GPU presentation. The embedded server still lacks the complete DRI3/Present/shared-buffer transport required for a real zero-copy route. r24 reduces allocation, copies, invalidation traffic and unrelated UI blocking; it does not claim to eliminate GPU readback.

Socket reception already occurs before the global X server lock, and ordered asynchronous socket output already exists in r23. These mechanisms are retained. Bitmap mutation, X11 resource lifetime and window tree updates still share the lock with composition. Removing that lock without immutable frame ownership could resurrect disappearing popups, torn frames and recycled-bitmap failures. X11 commands must retain protocol order; dispatching arbitrary drawing requests concurrently is unsafe.

The next larger architectural improvement is a fenced, reusable shared image/buffer path with explicit ownership and lifetime, negotiated between guest WSI, the embedded X server and the Android compositor. This requires a protocol and native transport implementation, not a renderer label or an environment variable. Snapshotting/double-buffering is an intermediate option but adds RAM and copy costs and needs device measurements before selection.

## Module assessment

- **Bootstrap/component vault:** hashes and staged manifests protect first-run packages. Existing serialized provisioning avoids races. Parallel extraction of large Proton/FEX/Blender archives would increase simultaneous memory/storage pressure on the 8 GiB tablet; Blender is extracted only when launched. Components remain independently versioned.
- **Wine/FEX/DXVK/Turnip launch:** maintain the established driver/runtime choices. Builtin Blender is translated into an explicit C: executable before launch argument construction and PE machine detection, so the native ARM64 DXGI policy applies. User-selected per-app profiles still take precedence after first use.
- **X11 protocol/resources:** preserve framed request boundaries, per-client ordering, asynchronous bounded output queues, map-state fixes and r23 clipping/stacking behavior. Raw request storage is not shared between clients. Only independent connections, input reception and non-rendering sampling overlap.
- **Compositor/window tree:** retain existing stable guest resolution and popup clipping. HWUI scheduling coalesces presentation requests, not guest state changes. Software Canvas fallback remains available. Visibility/occlusion shortcuts were not introduced without shape/alpha correctness tests.
- **Input/XInput:** existing shared-memory snapshots and separate cursor overlay avoid redundant desktop redraws. Retain existing seqlock protocol and no per-event disk flush. An unrelated memory-order/guest bridge rewrite is outside the verified performance changes.
- **Audio:** existing session-owned PulseAudio/AAudio is kept. Startup module verification is bounded (16 MiB); no audio-thread scheduling or buffer-size guesses were introduced without latency/underrun evidence.
- **Logs/diagnostics:** preserve optional verbose logging and the existing bounded RAW trace. Crash evidence and user exports remain available. HUD work is removed from UI sampling; no per-frame logging is added.
- **Shared R: folder/files:** retain explicit refresh and no startup directory-tree scan. Do not overwrite user-modified mirrored files. Blender lives in its versioned C: directory, separate from manual R: installs.
- **UI:** keep the established visual design and controls. No broad Activity refactor or removal of experiment flags merely to reduce line count.
- **Native bridges/guest tools:** preserve scoped exec/NLS/Present compatibility layers and source-built tools. None provides a finished shared-image presentation implementation. Tests exercise the Present guard and source checks.
- **Build/distribution:** extend existing private signed-build pipeline on an isolated branch. Preserve application ID and permanent signing identity; increment versionCode to 90. Blender and its bundled licenses remain intact. Generated ZIP payloads are excluded from Git source.

## Blender integration

Version 4.5.14, Windows ARM64 (`IMAGE_FILE_MACHINE_ARM64`, 0xaa64). Official ZIP SHA-256:
`0153ecefc96a0e23985e6edcdbe04e909ba2078258f4ff6a05c21d4b7c756911`.

The user's ZIP SHA-256 differs because the ZIP container was repacked. All 5,400 non-directory entries were compared with the official distribution by individual SHA-256 and match. The build downloads the pinned official package and verifies its exact hash and ARM64 PE header.

APK contains the complete 254,747,440-byte ZIP. First Blender launch extracts approximately 707 MB into `C:\DRNAS\Apps\Blender-4.5.14`. It needs at least 900 MiB free internal space at extraction time. Extraction runs on the Wine startup worker, reports progress and commits a verified version directory atomically. Traversal, excessive entry counts, expanded-size overflow, wrong hashes and non-ARM64 executables fail before commit.

Builtin `Blender 4.5 ARM64` defaults to Vulkan, passes the startup script explicitly, and uses Wine ARM64 builtin DLL selection. The portable `4.5/config` directory isolates preferences. First-use preferences hide the splash and enable numpad/three-button-mouse emulation for touch/keyboard use; an existing userpref.blend is preserved. A modified per-icon profile can override renderer choice. No Cycles GPU acceleration or Android device launch success is claimed by packaging tests.

Official distribution: https://download.blender.org/release/Blender4.5/blender-4.5.14-windows-arm64.zip
Source distribution location: https://download.blender.org/source/
Licenses and copyright notices remain inside the unmodified Blender ZIP.

## Validation and device acceptance

Host checks cover exact real-archive extraction, archive rollback/traversal, ARM64 PE selection, framing/EOF boundaries, nonzero pixel offsets, scalar/bulk pixel equality, buffer reuse without stale tails, output ordering, and CPU accounting. Full Android APK/JVM CI status is reported separately in the build result.

On-device acceptance still required: install as an update, launch builtin Blender offline, confirm UI and viewport, open/close Preferences repeatedly, resize/switch windows, test mouse and keyboard, verify user preference persistence, and compare equal scenes/resolution/thermal state with r23. Capture display FPS, app FPS, p50/p95 frame times, PutImage rate and Android frame stalls. Host microbenchmarks cannot substitute for these measurements.

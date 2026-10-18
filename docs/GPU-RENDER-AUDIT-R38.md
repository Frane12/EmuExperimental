# Blender DRNAS: GPU rendering and presentation audit (r38)

Audited baseline: r36, commit `b9d50aa181f3690c35f9444e332c496c059d366d`.
Scope: Wine/graphics runtime configuration, Vulkan wrapper packaging, X11 request
framing, PutImage conversion/upload, CopyArea, Android Canvas and FPS accounting.

## What actually renders and what presents

Blender Windows ARM64 uses Wine's native ARM64 graphics bridge. Vulkan rendering
and getting the resulting image onto the Android screen are separate operations.
The current configuration asks the packaged Mesa X11 wrapper to use
`MESA_VK_WSI_DEBUG=sw,noshm`. It transports pixels through X11 PutImage; it does
not pass a GPU image directly to the Android compositor. This does not mean that
Blender's shaders are necessarily running on the CPU. It means that the final
presentation path includes a CPU-visible image and transport.

Current path, schematically:

1. Blender -> WineVulkan -> Khronos loader / Mesa wrapper -> selected GPU driver.
2. Wrapper's software X11 WSI -> CPU-readable pixels -> X11 socket.
3. Fully framed X11 request -> BGRX decode -> Android mutable Bitmap.
4. Android hardware Canvas / HWUI -> screen composition.

The code logs GPU selection separately from verified driver activation. This
audit does not establish that any individual device successfully loaded its
requested driver, nor does it establish Cycles GPU-rendering support.

## Findings and changes

| Finding | r36 behavior | r38 behavior and limit |
|---|---|---|
| Request-body allocation | New byte array for every request, including every large PutImage | Reuse a client-owned buffer, grown as needed, with a 4 MiB retention limit; exceptional larger allocations are discarded at request end |
| Duplicate encoded image | Copy framed request bytes into another per-drawable byte array before conversion | Decode at the current offset directly inside the framed request buffer; no encoded-image array escapes to the drawable |
| Request isolation | Receive the full body before taking the global X server lock | Preserved; a handler still cannot consume the following request, and source storage is not reused until dispatch ends |
| Decoder views | Optional bulk IntBuffer view assumed source offset zero | Offset-aware scalar and bulk decoders; cached view follows the current backing array, with an unaligned scalar fallback |
| CopyArea | Allocate/copy a cropped Bitmap before every blit | Draw the source rectangle directly for distinct source/destination drawables with an unclipped opaque GXcopy; keep snapshots for self-copy/overlap and generic GC behavior |
| Image dimensions | Intermediate integer products could overflow; empty images could enter generic conversion | Use long size calculations, reject impossible allocations, and treat zero-area images as no-ops |
| Profiling | The field labeled socket read/wait was timed after the full request had already been received | Measure actual request-body receive time before dispatch, only while diagnostics or capture are enabled |
| FPS | Disabled for the Blender shell; the old estimate could remain stale while idle | Compact toggleable UI FPS display, enabled after Blender maps; sampling includes idle time and returns to zero |
| HUD overhead | General HUD polls host CPU/GPU/RAM/battery | Blender's compact mode updates only draw rate and average draw cadence every 650 ms |

At 1280 x 720 x 4 bytes, the removed encoded-image copy was **3,686,400 bytes
(about 3.52 MiB) per full-image update**. At 60 such updates/second this would be
about 211 MiB/s of avoided Java array copying, plus avoidance of the recurring
request-body allocation after warm-up. These are byte counts from the code, not
measured FPS gains. X11 can send partial/tiled updates, and update count is not
the same as Blender frame count.

The pool's 4 MiB bound is per connection, not global. Small connections grow
only as needed. Requests above the retention bound remain valid up to the
existing protocol maximum, but their storage is not retained. This keeps the
720p path reusable without permanently pinning exceptional 16 MiB requests.

## Correctness boundaries retained

- Network reads remain outside the global X server lock. Decoding, drawing and
  shared window state remain serialized; there is no unsynchronized concurrent
  mutation of a drawable introduced by this change.
- Full request boundaries are enforced before direct decode. A short receive
  cannot publish partial or stale pixels. Buffer views are not handed to HWUI.
- Source/destination aliasing keeps the original snapshot path in CopyArea.
- Shape-mask, clipped and non-opaque GC operations retain their generic paths.
- Driver selection, swapchain policy, Wine DXGI architecture recovery, direct D:
  mapping and the D-only browser volume list are preserved.
- Allocation failure while receiving a request is reported as an I/O failure
  for that client rather than an unhandled request-buffer allocation error.

## FPS and profiling interpretation

**UI FPS counts desktop onDraw callbacks**, not Blender engine frames, swapchain
presents, GPU completion or physical panel scanout. Cursor/overlay updates and
multiple partial window updates make these different measurements. The compact
HUD says `UI FPS`; its `ms` is mean UI-draw cadence, not GPU render duration.
It does not force continuous desktop redraws to manufacture a higher number.

The corrected request-read measurement includes receiving the complete X11
body and its transport wait, but excludes waiting to acquire the server lock.
Do not compare its numerical values directly with the mislabeled r36 read
field. Decode, Bitmap upload and UI interval samples remain separate, and none
claims to measure Vulkan Present completion.

## What is still expensive or unverified

The GPU readback, X11 socket transport, ARGB conversion, Bitmap write and HWUI
upload/composition remain. The global X11 lock can still serialize UI drawing
behind image processing. A robust asynchronous compositor would need explicit
buffer ownership, fences and bounded queues before those stages can overlap.
Removing that lock around mutable window state is not a valid optimization.

Mapped windows are composed recursively. More aggressive occlusion culling
would require correctness tests for shape regions, popups, stacking changes and
partial invalidation. It is not silently enabled here. No claim is made that
these CPU-side improvements solve all viewport or modal-window problems.

## A genuinely shorter GPU path

The next architectural target is **a shared GPU buffer -> Android compositor**,
with acquire/release fences and correct resize/window lifetime handling. Merely
renaming the display server or switching a preference to Wayland does not
remove the copies.

Two viable integration directions need separate experimental work:

1. Keep X11 for windows/input but add a local Unix-domain transport with file
   descriptor passing, DRI3/Present support, AHardwareBuffer import and a native
   Android compositor. This avoids replacing Blender's window protocol while
   replacing the pixel presentation path. Our current Java TCP transport has
   no ancillary-fd channel, so advertising DRI3 alone would be incorrect.
2. Add a parallel Wine Wayland runtime, an embedded compositor and a matching
   Mesa WSI. This needs `winewayland.drv`, its native dependencies, dmabuf or AHB
   import, Android surface presentation, input/IME, secondary windows and GPU
   fences. A compositor GPU blit can avoid CPU readback while still copying on
   the GPU; direct SurfaceControl buffer layers are a further step.

The pinned upstream Bannerlator source contains concrete work in these areas,
including `waylandcomp`, `vk_present`, and native surface renderers. Its research
notes distinguish raw dma-buf import from Android gralloc AHardwareBuffers;
one cannot assume every arbitrary dma-buf is an Android-compatible buffer.
The existing packaged wrapper is a pinned binary payload, so these changes
require rebuilding and integrating the corresponding source components.

**Neither new backend is implemented or enabled in r38.** No menu item claims
otherwise. Capability checks on the actual loaded driver, fence/lifetime tests,
resize/rotation, repeated open/close and modal-window tests are required before
switching away from the working fallback.

Pinned primary-source references:

- [Current wrapper packaging source](../tools/package_gui_runtime.py).
- [Bannerlator Wayland runtime](https://github.com/The412Banner/Bannerlator/blob/e91d93bb23b0f2dde953864484b6f620f72d9cad/app/src/main/cpp/waylandcomp/WAYLAND_RUNTIME.md).
- [AHardwareBuffer / WSI and zero-copy investigation](https://github.com/The412Banner/Bannerlator/blob/e91d93bb23b0f2dde953864484b6f620f72d9cad/app/src/main/cpp/waylandcomp/ZERO_COPY_SPIKE.md).
- [Native Android surface-renderer implementation](https://github.com/The412Banner/Bannerlator/tree/e91d93bb23b0f2dde953864484b6f620f72d9cad/app/src/main/cpp/asurfacerenderer).

## Validation and device test

Seventeen targeted host JVM tests pass: 720p scalar/bulk decode equivalence at
aligned and unaligned request offsets, buffer growth/reuse, exceptional-buffer
release, truncated bodies, stale-tail isolation, socket request framing, direct
pixel decode followed by another request, receive-timing reset and idle-correct
UI FPS. Full project JVM checks and APK assembly run in CI.

There is no connected Android GPU device in this environment. For an on-device
A/B test use the same phone, resolution, refresh rate, scene, view position,
renderer and thermal conditions; compare a warm r36 run with r38. Check colours,
popups, resizing, saving/loading, frame cadence, memory and the 20-second render
capture. Test the same rotation repeatedly and inspect glitches as well as
rates. Leave Max Frame Latency enabled wherever that setting applies. Do not
interpret this audit as a measured speedup or a validated new GPU backend.

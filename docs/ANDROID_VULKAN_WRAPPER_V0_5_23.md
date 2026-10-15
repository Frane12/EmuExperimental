# DRNAS 0.5.23: Android Vulkan backend behind X11 WSI

0.5.22 can paint a Wine desktop but has no Android-to-X11 Vulkan bridge.
Pointing the Khronos loader directly at an Android Turnip ICD does not
provide VK_KHR_xlib_surface to Wine's X11 driver.

This change keeps the existing HWUI desktop and uses the pinned upstream
Mesa Bionic wrapper distributed by Bannerlator. The loader's ICD is the
wrapper, while ADRENOTOOLS_DRIVER_PATH/NAME select the Drnas Turnip backend.
ADRENOTOOLS_HOOKS_PATH points at the bundled, hash-verified hooks.

Bootstrap previously applied DXVK only to containers/default/prefix, whereas
WineGuiSession launches wineboot-probe/template/.wine. DxvkRuntime now copies
the verified package DLLs for both architectures to that active prefix after
wineboot. It validates PE architecture, preserves builtin links as backups
without modifying their targets, and skips unchanged files on later boots.

The existing TCP X server cannot pass DRI3 buffer file descriptors.
MESA_VK_WSI_DEBUG=sw,noshm selects Mesa's CPU image presentation path:
GPU rendering, GPU-to-host copy, X11 PutImage. This incurs bandwidth and
copy overhead and is an intermediate bring-up path, not zero-copy WSI.
No PCI ID spoofing or software Vulkan renderer is introduced.

In 0.5.24, startup passes --enumerate-only to avoid mapping a transient X11
probe window before Explorer. The native helper uses the same loader and environment
as Wine. It queries real device/driver properties and rejects CPU devices.
When run explicitly without --enumerate-only it
creates an Xlib surface, selects a graphics/present queue, creates a logical
device and swapchain, acquires an image, clears it with GPU commands and
presents it. The helper is time-limited and cannot establish that a Windows
DXVK process or a particular game works.

Device acceptance:
1. Start Windows and export the raw log.
2. Look for a nonzero vendor/device and DRNAS_TURNIP_DEVICE_VERIFIED.
3. The normal startup probe verifies enumeration only; full presentation remains unverified.
4. Launch the Windows GPU probe and confirm a DXVK adapter and driver.
5. Start Crysis Bin32 and inspect its DXVK log. A native probe pass alone
   is insufficient to claim game support.

Reject if a CPU renderer is selected, device creation/presentation fails,
or the app hangs/crashes. A failure stage is logged; no success label is
inferred from an ICD path or DLL presence.

Packaging validation checks every ELF architecture, recursive DT_NEEDED
closure and content hash. Upstream archive and commit are pinned. Existing
dependency licenses plus the wrapper and AdrenoTools notices ship in assets.

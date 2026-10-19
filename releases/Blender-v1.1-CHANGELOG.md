# Blender v1.1 — ARM64 Android Preview

**PRIVATE RELEASE DRAFT — maintainer approval required.** This is an **unofficial** community preview of Blender 4.5.14 LTS for Windows ARM64 running on Android using DRNAS Wine/X11/Vulkan. It is not an official Blender Android port and is not endorsed by the Blender Foundation.

## Highlights

- **Smooth zoom** in Blender's 3D viewport: the floating ZOOM slider gradually and smoothly changes the viewport distance rather than jumping by mouse-wheel ticks. Use +/− for fine movement; long-press ZOOM for the original mouse-wheel fallback.
- **Safe Android fullscreen:** the main Blender editor fills the screen without making the native Wine fullscreen transition that previously froze the app. In the Blender menu choose *Window → Toggle Window Fullscreen (DRNAS)* to toggle the Android-only display. Preferences and Render remain separate windows.
- **Touch editing:** compact mouse pointer, LMB/RMB buttons and a collapsible Blender shortcut keyboard, with the working dimensions preserved.
- **Android project folder:** select a folder at first launch; Blender sees the selected Android directory as **D:\\** in Open/Save. Keep a backup of important projects.
- **Lower overhead:** continuous X11 frame tracing, expensive input/focus diagnostics, stack-tracing watchdog and PanVK startup debug output are **disabled by default**. Basic startup/errors remain recorded. Detailed logs and the 20-second performance test remain accessible on demand.
- **Vulkan-oriented automatic driver selection** with conservative chip matching and native preflight where supported. Device-level rendering still depends on each Android firmware and Wine presentation path.

## GPU compatibility (automatic backend choice)

| GPU | Selected path | Evidence and limitations |
| --- | --- | --- |
| **Adreno A6xx/A7xx** (recognized models) | Bundled Mesa Turnip candidate; system Vulkan fallback | Family detected automatically; individual devices not all tested |
| **Adreno 810, 830, 840** | DRNAS chip-specific Turnip/NeuroFlow candidate; system fallback | **Adreno 840** has user-tested Blender editing and smooth zoom; other SoCs experimental |
| **Mali G52, G72, G76** | Matched experimental PanVK candidate when Android version allows; system fallback | **Experimental:** not verified on all Mali devices |
| **Other Mali models** (G57, G610, G720, etc.) | Device **system Vulkan**; no unrelated PanVK injected | Compatibility unverified; depends on firmware |
| **Samsung Xclipse** (incl. 920, 940, 950) | **Samsung/AMD firmware Vulkan** only; no Turnip or PanVK | Family auto-detected; Xclipse 940 rendering **not yet confirmed** on a Samsung test device |
| Other Vulkan-capable Android devices | Device system Vulkan | Unverified |

**Important:** recognizing a GPU, staging a compatible candidate, or enumerating Vulkan devices is **not proof that Blender will render successfully**. Vulkan extensions, Wine/WineVulkan, X11 presentation and memory limits vary by phone. No Samsung proprietary Xclipse driver is bundled or claimed to be portable. No guarantee of all GPU models or ray tracing.

## Installation / use

1. Install **Blender-v1.1-ARM64-Android-Preview-VULKAN-STABLE-SIGNED.apk** on a compatible ARM64 Android device.
2. When prompted, grant access to an Android project folder. That directory is available as **D:\\** for .blend Open/Save.
3. Wait for Blender 4.5.14 to start. Use the ZOOM slider, LMB/RMB and keyboard shortcut strip for navigation.
4. To switch fullscreen, use the **DRNAS safe fullscreen** option, not native Wine fullscreen hotkeys.
5. If you find a device-specific issue, open the app menu and **Pošalji log**. Detailed diagnostics can be enabled temporarily in settings.

## Known limits / credits

This remains a Windows ARM64 Blender build hosted on Android through an experimental compatibility layer. Startup, rendering, GPU features and Save/Load can fail on some devices. Ray tracing and heavy scenes are **not guaranteed**; back up your work.

Blender, Wine/Proton, Mesa Turnip/PanVK, FEX, DXVK and the X11 stack are third-party open-source projects with their own licenses. **This package is unofficial and requires final attribution, license-notice and corresponding-source compliance review before publication.**

**Status:** draft prepared for explicit maintainer approval; **not published**.

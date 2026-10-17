# DRNAS EMU — Pre-alpha announcement

**17 October 2026 | Independent Android Windows gaming runtime | PRE-ALPHA COMING SOON**

<p align="center">
<img src="https://cdn.uc.assets.prezly.com/497bc673-1170-4d96-a149-4c29576f731f/-/preview/-/format/auto/CrysisRemastered_Screenshot1.jpg" alt="Official Crysis Remastered promotional gameplay screenshot (Crytek); illustrative, not a DRNAS EMU capture" width="780" />
</p>
<p align="center"><sub>Crysis Remastered © Crytek — <a href="https://press.crytek.com/media/album/97c5c8d3-9e38-4f92-b713-74be40258620">official press gallery</a>. Illustrative promotional screenshot, not captured from DRNAS EMU.</sub></p>

## Introducing DRNAS EMU

DRNAS EMU is an Android-based Windows PC gaming runtime under active development, designed around modular components, GPU rendering correctness and smooth frame pacing. The goal is to bring Windows software and games to ARM Android devices while retaining careful engineering control of CPU translation, Direct3D translation, Vulkan driver behavior, display/input and audio.

The current research stack explores **Wine / Proton**, **FEX**, **DXVK**, **Mesa Turnip**, native Android UI, embedded **X11**, and audio and controller interaction. These third-party components are credited and remain subject to their respective licenses. DRNAS EMU is an integration and optimization project, not a from-scratch replacement for every component.

Recent **private development builds** have reached visible Wine GUI operation and audio, with ongoing investigations of **DXVK / Direct3D rendering, controller input, Crysis launch behavior, display correctness and frame timing**. These observations are not claims of complete or stable Crysis gameplay support.

## Future direction — Qualcomm Hexagon NPU + GPU

The long-term research goal includes exploring **Qualcomm Hexagon NPU (QNN/HTP) as an auxiliary, asynchronous decision engine for GPU-related optimization**. Candidate experiments include predicting upcoming render workload from history and offering background hints for adaptive GMEM/SYSMEM decisions or shader/texture policies.

- **No rendering waits:** never block the game, GPU submission or presentation waiting for an NPU inference.
- **Safe fallback:** stale, missing, noisy or invalid predictions are ignored in favor of a working Vulkan/driver path.
- **Measure, then keep or reject:** track frametime percentiles, stability, thermal cost and power before calling any optimization successful.
- **Hardware compatibility first:** Qualcomm NPU access and actual usefulness must be tested on supported devices.

**This is an intended R&D direction, not an already working NPU feature or a guarantee of higher FPS.**

## Public testing plans

A first **PRE-ALPHA / TESTING ONLY** version is being actively prepared for external testers and is expected **soon**, once a sufficiently usable test package and documentation are ready. We are **not announcing a fixed date or public download yet**.

Expect experimental limitations. Initial testers should report their Android device / GPU, Android version, emulator and runtime components, tested game, resolution/settings, frametime/FPS where available, heat, graphical artifacts and logs. Tested games may require specific settings and may fail to start.

DRNAS EMU aims to be built in the open when release readiness, binary/source provenance, third-party acknowledgements and licensing checks permit publication.

**Project:** DRNAS EMU / EmuExperimental  
**Developer:** Frane12  
**Status:** actively developed · upcoming pre-alpha for volunteer testing  
**Crysis and Crytek:** trademarks and artwork belong to their respective owners. No affiliation or endorsement is implied.

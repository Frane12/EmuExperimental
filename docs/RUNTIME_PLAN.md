# Runtime plan and upstream licensing

## CPU + Windows runtime
1. **Initial target:** Android rootless Bionic approach for ARM64 and a tested Wine build. FEXCore embedded in the application/compat layer where integration supports it, with Box64 alternate backend.
2. **Proton ARM64EC:** experimental *separate adapter*. Not equivalent to unpacking the desktop Proton archive onto Android. It needs its own patched execution environment, Wine compatibility and compatible library/kernel assumptions.
3. **FEX-Emu itself:** the official Linux-oriented FEX environment is not simply portable to Android. FEXCore is a distinct integration target. Check the upstream FAQ before claiming full FEX support: https://wiki.fex-emu.com/index.php/FAQ

## GPU
`D3D9/10/11 -> DXVK -> Vulkan loader -> AdrenoTools/Turnip`.
`D3D12 -> VKD3D-Proton -> Vulkan loader -> Turnip` when extensions allow it.
Implement a GPU capability probe and keep A810 / A830 / A840 as separate hardware targets, without ID spoofing.

## Sound
Start with `Wine audio client -> PulseAudio-compatible bridge -> Android AAudio/OpenSL ES` or existing working Bionic audio plugin. Make backend and buffering swappable.
Three mode presets: low-latency, balanced, compatibility. Show audio underruns/latency in diagnostics.

## Components & licensing
- Our glue/mock UI is original source; do not copy Winlator assets or trademarks.
- Winlator variants and dependencies have their own licenses; verify LICENSE files of each exact commit before incorporating sources or repackaging binaries.
- Record Wine/FEX/Mesa/DXVK/VKD3D/Proton at specific versions+source references.
- Component imports must require explicit user selection or a curated, checksum-verified manifest.
- No copyrighted game files or proprietary Qualcomm driver blobs in this repository.
- Build reproducibly in CI; ship development APKs only after the shell has a real backend.

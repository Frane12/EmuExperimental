# DRNAS Native FreeCAD LAB — private ARM64 kernel proof

**Stage 1: Open CASCADE Technology (OCCT) geometry engine built natively for Android arm64-v8a.**

This is a **private prototype inside a dedicated branch** of the existing private `EmuExperimental` repository. It is not a public FreeCAD release, not full FreeCAD, and not yet an APK. The existing DRNAS Emu `main` and all Blender branches are untouched.

## What the first build does

- Pins official OCCT **7.9.3** (`V7_9_3`, commit `a016080bf6738d6aeae020badee4e888ad1540a5`) for a stable FreeCAD-compatible starting point.
- Cross-compiles **FoundationClasses, ModelingData and ModelingAlgorithms** with the Android NDK for **ARM64**. No desktop X11, Qt, FreeCAD GUI or Wine dependencies.
- Links `libdrnas_freecad_probe.so`, a small real OCCT consumer that constructs a **box, cylinder and sphere**, checks geometric validity and volumes, and can export three `.brep` files at runtime on the device through a C ABI.
- Validates the `AArch64` ELF architecture, exported probe functions and **16 KiB maximum page alignment**.
- Uploads the native libraries and verification report as a private GitHub Actions artifact (only accounts with access to this private repo can download).

**What this does not prove yet:** actual execution on an Android phone, Android Java/JNI integration, rendering, FreeCAD's Part/Sketcher workbenches or a full FreeCAD Android port. Those come in later stages.

## Next steps after a successful kernel build

Stage 2: small private Android APK wrapping the OCCT probe, three 3D solids, shape volume readouts and `.brep` export. Stage 3: touch viewer (compatible GPU path); Stage 4: integrate FreeCAD modules and test real document workflows.

Original upstream code: [Open CASCADE Technology](https://github.com/Open-Cascade-SAS/OCCT), licensed **LGPL-2.1 with OCCT exception**. This private experiment retains provenance and must provide correct licensing and source attribution before any redistribution. FreeCAD (LGPL) is a separate upstream project and is **not yet compiled in stage 1**.

Start/inspect: **GitHub Actions → DRNAS FreeCAD — Private OCCT ARM64 Kernel (Stage 1)**.

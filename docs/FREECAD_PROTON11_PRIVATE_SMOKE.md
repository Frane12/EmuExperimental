# FreeCAD + DRNAS Proton 11 — private compatibility preparation

Branch: `feature/private-freecad-proton11-test-20261021`. **No APK build triggered.**

This experiment reuses the DRNAS Windows ARM64 Wine/X11 GUI and controller stack. It adds
a read-only FreeCAD.exe lookup and PE machine validation. It will never fetch, install,
or change a Windows application automatically. A manual confirmation is required
before a launch. The main DRNAS release and Blender binaries are unchanged.

The intended test executable is the official FreeCAD Windows x64 portable release.
Extract it in the Windows C: prefix as `C:\FreeCAD\bin\FreeCAD.exe`, or install
normally under `C:\Program Files\FreeCAD…\bin\FreeCAD.exe`. In DRNAS Console >
Shortcuts, tap **FreeCAD (LAB)**, review detected architecture, then approve the launch.
A Linux AArch64 AppImage **must not** be passed to Wine.

Windows x64 execution requires a WORKING FEX/x64 translation path; Proton ARM64EC
alone does not prove that path works. FreeCAD's Qt/Coin3D OpenGL rendering also
requires a compatible OpenGL backend; DXVK alone is not OpenGL support.
Do not report success until a phone test confirms first GUI window, controls,
3D viewport, STEP/BREP opening, and saved CAD document.

A separate branch, `feature/private-freecad-occt-arm64-lab`, contains the experimental
native Android ARM64 OCCT *geometry kernel*, not complete FreeCAD.

Build rule: do not start an APK build without explicit approval. Any future APK
must use a separate package ID so installed Blender is not replaced.

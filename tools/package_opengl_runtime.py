#!/usr/bin/env python3
"""Reproducible, opt-in Android/Bionic Mesa EGL/Zink bundle for Wine 11.

Build inputs are selected from one immutable public commit and checked against
their Git blob objects. Never substitute GLES for desktop GL/GLX.
"""
import hashlib
import json
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

COMMIT = "5865486d275ca00758d592f6221f35f9956ff2fb"
ORIGIN = "https://github.com/The412Banner/Bannerlator"
RAW = "https://raw.githubusercontent.com/The412Banner/Bannerlator/" + COMMIT + "/app/src/main/assets/x11/egl/"
# The exact upstream EGL bundle also depends on two Bionic runtime libraries
# shipped as ELF jniLibs by the SAME immutable upstream revision.
RAW_NATIVE = "https://raw.githubusercontent.com/The412Banner/Bannerlator/" + COMMIT + "/app/src/main/jniLibs/arm64-v8a/"
NATIVE_FILES = {
    "libandroid-support.so": "eaa4a4baef6c1fbb93b9b2a5ecaee666970ad609",
    "libffi.so": "eceb85edf5da4613cfebe759c18d1b65766f7cf9",
}
# Git blob identities from that exact upstream revision.
FILES = {
    "libEGL.so.1": "0566e1990667449f9c6ed63fcbb54db2bc440376",
    "libGL.so.1": "65d61b5d5abfffb8af34a517e845e3513d9e027a",
    "libGLESv2.so.2": "4cc82c9202d59be0c5a3a8c0f575887835fbcf9f",
    "libarchive.so": "c648a2630e27b3827d84a0a5bf439a2297a6c736",
    "libgallium-26.3.0-devel.so": "4ef1d4390f70ac5f65fe892d35e0a835cd80b2c4",
    "libwayland-client.so": "292f5c2ca601ff66e55ab2a5e867f3d4582bcb96",
    **NATIVE_FILES,
}

def git_blob_id(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()

# Android platform libraries provided by the Bionic runtime. Every other
# link-time dependency must be bundled in either the GL or GUI native ZIP.
BIONIC_SYSTEM = {
    "libc.so", "libdl.so", "libm.so", "liblog.so", "libandroid.so",
    "libnativewindow.so", "libz.so", "libstdc++.so", "libc++.so",
    "libc++_shared.so", "libGLESv1_CM.so", "libGLESv2.so",
}

def gui_loader_names(libraries):
    """Match WineGuiRuntime.installAliases(): libX11.so.6 exposes libX11.so.

    An alias is only valid when precisely one versioned SONAME claims it.
    Never silently choose between two different major ABI versions.
    """
    names = set(libraries)
    aliases = {}
    for soname in sorted(names):
        if soname.endswith(".so"):
            continue
        if not re.fullmatch(r"lib[A-Za-z0-9_+-]+\.so(?:\.[A-Za-z0-9_.+-]+)?", soname):
            raise RuntimeError("Unsafe Bionic GUI soname: " + soname)
        base = soname[:soname.index(".so") + 3]
        old = aliases.setdefault(base, soname)
        if old != soname:
            raise RuntimeError(
                f"Conflicting native GUI linker alias {base}: {old} and {soname}")
    return names | set(aliases)

def mesa_required_sonames(mesa_manifest):
    """Get every non-Android DT_NEEDED edge from this exact Mesa package."""
    mesa_provided = gui_loader_names(mesa_manifest)
    return sorted({
        needed
        for meta in mesa_manifest.values()
        for needed in meta["needed"]
        if needed not in mesa_provided and needed not in BIONIC_SYSTEM
    })

def verify_blender_glx_closure(gui_bundle, mesa_manifest):
    """Reject a broken Wine libGL dependency closure BEFORE compiling an APK.

    The GUI bundle originates from the pinned, checksum-verified GameNative
    Bionic imagefs and recursively follows readelf DT_NEEDED dependencies.
    This check does not assert that the embedded Java X11 server supports
    GLX or that Wine/Blender can create a rendered frame.
    """
    with zipfile.ZipFile(gui_bundle) as z:
        gui_libs = {Path(n).name for n in z.namelist()
                    if n.startswith("lib/") and not n.endswith("/")}
        if "libxcb-glx.so" not in gui_libs:
            raise RuntimeError("R64 GLX dependency closure missing libxcb-glx.so")
        if z.getinfo("lib/libxcb-glx.so").file_size < 4096:
            raise RuntimeError("R64 libxcb-glx.so is implausibly small")
    # GUI runtime produces its unversioned symlinks on-device, e.g.
    # libX11.so.6 -> libX11.so. Raw ZIP entries don't contain those aliases.
    # Previous preflight falsely rejected libEGL.so.1 -> libX11.so.
    supplied = gui_loader_names(gui_libs) | gui_loader_names(mesa_manifest) | BIONIC_SYSTEM
    missing = sorted({
        (soname, needed)
        for soname, meta in mesa_manifest.items()
        for needed in meta["needed"]
        if needed not in supplied
    })
    if missing:
        raise RuntimeError("R64 Bionic DT_NEEDED closure incomplete: " +
                           "; ".join(f"{src} needs {dep}" for src,dep in missing))
    gl_needs = set(mesa_manifest["libGL.so.1"]["needed"])
    if "libxcb-glx.so" not in gl_needs:
        raise RuntimeError(
            "Pinned Mesa libGL.so.1 unexpectedly changed GLX DT_NEEDED; "+
            "re-audit dependency model")
    print("DRNAS_R64_BLENDER_OPENGL_DEP_CLOSURE_VALIDATED",
          "Mesa libs",len(mesa_manifest),"GUI libs",len(gui_libs),
          "unversioned_aliases=checked", "libxcb-glx.so=present")

def package(out, gui_bundle=None):
    out = Path(out)
    manifest = {}
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as folder:
        folder = Path(folder)
        for name, expected_blob in FILES.items():
            dest = folder / name
            subprocess.run(["curl", "-fsSL", "--retry", "3", "--max-time", "180",
                            (RAW_NATIVE if name in NATIVE_FILES else RAW) + name,
                            "-o", str(dest)], check=True)
            data = dest.read_bytes()
            if git_blob_id(data) != expected_blob:
                raise RuntimeError("Pinned Git blob SHA mismatch: " + name)
            if data[:5] != b"\x7fELF\x02" or int.from_bytes(data[18:20], "little") != 183:
                raise RuntimeError("Expected ELF64 AArch64 Bionic lib: " + name)
            dynamic = subprocess.check_output(["readelf", "-d", str(dest)], text=True)
            needed = re.findall(r"\(NEEDED\).*?\[(.*?)\]", dynamic)
            print("OPENGLES_STAGE", name, len(data), "bytes; NEEDED", ",".join(needed))
            if any(n.startswith(("libc.so.6", "ld-linux")) for n in needed):
                raise RuntimeError("glibc library must never enter Bionic runtime: " + name)
            manifest[name] = {"sha256": hashlib.sha256(data).hexdigest(),
                              "blob": expected_blob, "size": len(data), "needed": needed}
        if gui_bundle is not None:
            verify_blender_glx_closure(gui_bundle, manifest)
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            for name in sorted(FILES):
                z.write(folder / name, "lib/" + name)
            z.writestr("manifest.json", json.dumps(
                {"version": 1, "upstream_repo": ORIGIN, "commit": COMMIT,
                 "route": "Wine 11 EGL on X11, Mesa Zink (experimental)",
                 "files": manifest}, indent=2))
    print("DRNAS_OPENGL_BUNDLE_WRITTEN", out, out.stat().st_size, "bytes")

def verify_archives(gui_bundle, mesa_bundle):
    with zipfile.ZipFile(mesa_bundle) as z:
        indexed = json.loads(z.read("manifest.json"))["files"]
        actual = {Path(n).name for n in z.namelist()
                  if n.startswith("lib/") and not n.endswith("/")}
        if set(indexed) != actual:
            raise RuntimeError("Mesa archive and manifest SONAMEs differ")
    verify_blender_glx_closure(gui_bundle, indexed)

if __name__ == "__main__":
    if len(sys.argv)==4 and sys.argv[1]=="--verify":
        verify_archives(sys.argv[2],sys.argv[3])
    elif len(sys.argv) in (2,3):
        package(sys.argv[1], sys.argv[2] if len(sys.argv)==3 else None)
    else:
        raise SystemExit("usage: package_opengl_runtime.py OUTPUT.zip [GUI_RUNTIME.zip]\n"
                         "   or: package_opengl_runtime.py --verify GUI.zip MESA.zip")

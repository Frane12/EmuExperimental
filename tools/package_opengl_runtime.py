#!/usr/bin/env python3
"""Reproducible opt-in Bionic Mesa EGL/Zink *complete ELF dependency closure*.

r65 shipped the six pinned Mesa files without the non-platform DT_NEEDED
libraries (notably libxcb-xfixes.so, libxcb-glx.so). r66 extends the bundle
from the SAME SHA-pinned imagefs that DRNAS uses for X11. No APK is built by
this tool. Fail closed if a needed library is absent or not ARM64 ELF.
"""
import hashlib
import json
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

try:
    from .mesa_bionic_closure import (
        BIONIC_SHA256, ANDROID_SYSTEM, check_name,
        verified_imagefs, resolve_needed,
    )
except ImportError:
    from mesa_bionic_closure import (
        BIONIC_SHA256, ANDROID_SYSTEM, check_name,
        verified_imagefs, resolve_needed,
    )

COMMIT = "5865486d275ca00758d592f6221f35f9956ff2fb"
ORIGIN = "https://github.com/The412Banner/Bannerlator"
RAW = "https://raw.githubusercontent.com/The412Banner/Bannerlator/" + COMMIT + "/app/src/main/assets/x11/egl/"
# Original r25/r65 immutable upstream Git blob checks.
FILES = {
    "libEGL.so.1": "0566e1990667449f9c6ed63fcbb54db2bc440376",
    "libGL.so.1": "65d61b5d5abfffb8af34a517e845e3513d9e027a",
    "libGLESv2.so.2": "4cc82c9202d59be0c5a3a8c0f575887835fbcf9f",
    "libarchive.so": "c648a2630e27b3827d84a0a5bf439a2297a6c736",
    "libgallium-26.3.0-devel.so": "4ef1d4390f70ac5f65fe892d35e0a835cd80b2c4",
    "libwayland-client.so": "292f5c2ca601ff66e55ab2a5e867f3d4582bcb96",
}
REQUIRED_XCB = ("libxcb-glx.so", "libxcb-xfixes.so", "libX11-xcb.so")


def git_blob_id(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


def check_arm64(name, data):
    check_name(name)
    if (len(data) < 20 or data[:5] != b"\x7fELF\x02"
            or int.from_bytes(data[18:20], "little") != 183):
        raise ValueError("Expected ELF64 AArch64 Bionic library: " + name)


def package(output, rootfs):
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="drnas-r66-mesa-") as tmp:
        tmp = Path(tmp)
        original = {}
        for name, expected_blob in FILES.items():
            filename = tmp / name
            subprocess.run([
                "curl", "-fsSL", "--retry", "3", "--max-time", "180",
                RAW + name, "-o", str(filename),
            ], check=True)
            data = filename.read_bytes()
            if git_blob_id(data) != expected_blob:
                raise ValueError("Pinned Mesa Git blob mismatch: " + name)
            check_arm64(name, data)
            original[name] = data

        metadata = {}

        def needs(name, data):
            # readelf operates on our reviewed, immutable ELF bytes; never
            # satisfy dynamic dependencies from the CI runner's host paths.
            filename = tmp / name
            filename.write_bytes(data)
            dyn = subprocess.check_output(["readelf", "-d", str(filename)], text=True)
            needed = re.findall(r"\(NEEDED\).*?\[(.*?)\]", dyn)
            for dep in needed:
                check_name(dep)
            metadata[name] = needed
            return needed

        with verified_imagefs(rootfs) as get_pinned_bionic:
            def fetch(name):
                data = get_pinned_bionic(name)
                check_arm64(name, data)
                return data
            libraries = resolve_needed(original, fetch, needs)

        missing = set(REQUIRED_XCB).difference(libraries)
        if missing:
            raise ValueError("Mesa OpenGL XCB dependencies missing: " +
                             ", ".join(sorted(missing)))
        # A final pass ensures every library has a DT_NEEDED audit entry.
        for name in libraries:
            if name not in metadata:
                raise ValueError("Unscanned native library: " + name)
            if any(dep not in ANDROID_SYSTEM and dep not in libraries
                   for dep in metadata[name]):
                raise ValueError("Unclosed DT_NEEDED on " + name)

        manifest = {}
        for name in sorted(libraries):
            data = libraries[name]
            manifest[name] = {
                "sha256": hashlib.sha256(data).hexdigest(),
                "size": len(data),
                "source": "Bannerlator-Git-blob" if name in original else "GameNative-pinned-Bionic",
                "needed": metadata[name],
            }
            if name in original:
                manifest[name]["blob"] = FILES[name]

        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            for name in sorted(libraries):
                z.writestr("lib/" + name, libraries[name])
            z.writestr("manifest.json", json.dumps({
                "version": 2,
                "route": "Wine 11 EGL with Mesa Zink (NOT device validated)",
                "upstream_repo": ORIGIN,
                "commit": COMMIT,
                "bionic_imagefs_sha256": BIONIC_SHA256,
                "elf_dependency_closure": True,
                "files": manifest,
            }, indent=2))

    print("DRNAS_OPENGL_R66_CLOSURE_OK", len(libraries),
          "ELF libraries; zip bytes", output.stat().st_size)
    print("DRNAS_OPENGL_R66_XCB_PRESENT", " ".join(REQUIRED_XCB))
    print("DRNAS_OPENGL_EGL_UNVERIFIED", "No EGL context or Blender render tested")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: package_opengl_runtime.py OUTPUT.zip PINNED_IMAGEFS.txz")
    package(sys.argv[1], sys.argv[2])

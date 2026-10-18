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
# Git blob identities from that exact upstream revision.
FILES = {
    "libEGL.so.1": "0566e1990667449f9c6ed63fcbb54db2bc440376",
    "libGL.so.1": "65d61b5d5abfffb8af34a517e845e3513d9e027a",
    "libGLESv2.so.2": "4cc82c9202d59be0c5a3a8c0f575887835fbcf9f",
    "libarchive.so": "c648a2630e27b3827d84a0a5bf439a2297a6c736",
    "libgallium-26.3.0-devel.so": "4ef1d4390f70ac5f65fe892d35e0a835cd80b2c4",
    "libwayland-client.so": "292f5c2ca601ff66e55ab2a5e867f3d4582bcb96",
}

def git_blob_id(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()

def package(out):
    out = Path(out)
    manifest = {}
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as folder:
        folder = Path(folder)
        for name, expected_blob in FILES.items():
            dest = folder / name
            subprocess.run(["curl", "-fsSL", "--retry", "3", "--max-time", "180",
                            RAW + name, "-o", str(dest)], check=True)
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
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
            for name in sorted(FILES):
                z.write(folder / name, "lib/" + name)
            z.writestr("manifest.json", json.dumps(
                {"version": 1, "upstream_repo": ORIGIN, "commit": COMMIT,
                 "route": "Wine 11 EGL on X11, Mesa Zink (experimental)",
                 "files": manifest}, indent=2))
    print("DRNAS_OPENGL_BUNDLE_WRITTEN", out, out.stat().st_size, "bytes")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: package_opengl_runtime.py OUTPUT.zip")
    package(sys.argv[1])

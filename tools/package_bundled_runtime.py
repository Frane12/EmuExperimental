#!/usr/bin/env python3
"""Fetch and embed the three pinned-by-name Windows runtime WCP assets into an APK.

These files are shipped unchanged as *data* in APK assets. Nothing is executed
or extracted on a build runner. Every build records SHA-256 hashes, lengths
and format markers for mandatory verification before first-use extraction.
"""
import hashlib
import json
import subprocess
import sys
from pathlib import Path

LIMIT = 768 * 1024 * 1024
FILES = [
    {
        "kind": "Proton",
        "name": "proton-10-arm64ec-controller-fix.wcp",
        "compression": "ZSTD",
        "url": "https://github.com/K11MCH1/Winlator101/releases/download/wine_col/proton-10-arm64ec-controller-fix.wcp",
    },
    {
        "kind": "FEXCore",
        "name": "FEXCore-2609.wcp",
        "compression": "XZ",
        "url": "https://downloads.gamenative.app/FEXCore-2609.wcp",
    },
    {
        "kind": "DXVK",
        "name": "dxvk_1.10.3.wcp",
        "compression": "XZ",
        "url": "https://github.com/K11MCH1/Winlator101/releases/download/dxvk_col/dxvk_1.10.3.wcp",
    },
]
MAGIC = {"XZ": bytes.fromhex("fd377a585a00"), "ZSTD": bytes.fromhex("28b52ffd")}


def package(directory):
    output = Path(directory)
    target = output / "bundled"
    target.mkdir(parents=True, exist_ok=True)
    manifest = {"schema": 1, "items": []}
    for component in FILES:
        name = component["name"]
        archive = target / name
        tmp = target / (name + ".partial")
        tmp.unlink(missing_ok=True)
        try:
            subprocess.run(
                [
                    "curl", "--fail", "--location", "--retry", "3",
                    "--connect-timeout", "25", "--max-time", "360",
                    "--user-agent", "DRNAS-EmuExperimental-bundled/0.5.11",
                    "--output", str(tmp), component["url"],
                ],
                check=True,
            )
            size = tmp.stat().st_size
            if not 16 <= size <= LIMIT:
                raise ValueError(f"{name}: invalid package size {size}")
            hasher = hashlib.sha256()
            with tmp.open("rb") as stream:
                magic = stream.read(6)
                if not magic.startswith(MAGIC[component["compression"]]):
                    raise ValueError(f"{name}: expected {component['compression']} WCP, got {magic.hex()}")
                hasher.update(magic)
                while True:
                    block = stream.read(1024 * 1024)
                    if not block:
                        break
                    hasher.update(block)
            tmp.replace(archive)
        finally:
            tmp.unlink(missing_ok=True)
        digest = hasher.hexdigest()
        print(f"{component['kind']} {name} size={size} sha256={digest}", flush=True)
        manifest["items"].append(
            {
                "kind": component["kind"],
                "name": name,
                "asset": "bundled/" + name,
                "compression": component["compression"],
                "bytes": size,
                "sha256": digest,
            }
        )
    (output / "runtime-components-v1.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Bundled 3 component archives; no runtime network or file picker required.", flush=True)


if __name__ == "__main__":
    package(sys.argv[1])

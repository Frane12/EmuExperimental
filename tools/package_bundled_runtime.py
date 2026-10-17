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
import urllib.request
import re
import zipfile
from pathlib import Path

LIMIT = 768 * 1024 * 1024
FILES = [
    {
        "kind": "Proton",
        "name": "proton-11.0-2-arm64ec.wcp",
        "sha256": "fffa467241bdae3eacd6ceb7e8096bb7793d617ce53a198dae8bc63a3453f595",
        "compression": "ZSTD",
        "url": "https://github.com/GameNative/proton-wine/releases/download/proton-11.0-2-20260928/proton-11.0-2-arm64ec.wcp",
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
        "variant": "standard",
        "sha256": "7ae0a7108f08fa12a54be891feecaac9de7d8eb3cd543704426126bb16a3e87a",
        "compression": "XZ",
        "url": "https://github.com/K11MCH1/Winlator101/releases/download/dxvk_col/dxvk_1.10.3.wcp",
    },
    {
        "kind": "DXVK",
        "name": "Dxvk-1.10.3-async.wcp",
        "variant": "async",
        "sha256": "19406702e641950d05b154c07b4dcead62aee94cc008bc8ed425cb72a2874914",
        "compression": "XZ",
        "url": "https://github.com/Xnick417x/winlator-nightly-wcp/releases/download/Stable-Dxvk/Dxvk-1.10.3-async.wcp",
    },
]
MAGIC = {"XZ": bytes.fromhex("fd377a585a00"),
         "ZSTD": bytes.fromhex("28b52ffd"), "ZIP": bytes.fromhex("504b0304")}

def drnas_turnip_assets():
    """Explicit, reproducible driver set; published prereleases are intentional.

    Never discover a different driver during a rebuild. A840 H2.9 remains
    TESTING ONLY; inclusion is an integration experiment, not a stability claim.
    """
    base = "https://github.com/Frane12/Testturnip/releases/download/"
    pinned = [
        ("A810", "drnas-turnip-a810-s-ultimate-v1.0",
         "Drnas-Turnip-A810-S-Ultimate-v1.0.zip",
         "b32c541e76556f06f8ec36e7cafe5034cefe2ae8c3d4f38939d2832a963bc8c0"),
        ("A830", "drnas-turnip-s1-a830-smart", "Drnas-Turnip-S1-A830.zip",
         "c5660b57f39b4334d64811b51f496d19327de975f1368b7f936941c00eb577d5"),
        ("A840", "drnas-turnip-neuroflow-a840-h2.9-testing-20261017",
         "Drnas-Turnip-NeuroFlow-A840-H2.9-TESTING-ONLY.zip",
         "6b67ba723b2a4881214591055c637e2e513363c44874559d16e2a13b1019bb3d"),
    ]
    return [{"kind": "Turnip", "name": "Drnas-Turnip-" + gpu + ".zip",
             "compression": "ZIP", "url": base + tag + "/" + name,
             "sha256": sha, "gpuTarget": gpu, "releaseAssetName": name}
            for gpu, tag, name, sha in pinned]


def check_turnip_zip(archive):
    with zipfile.ZipFile(archive) as z:
        library_names=[n for n in z.namelist() if n.endswith("libvulkan_freedreno.so")]
        if len(library_names)!=1:
            raise ValueError("Turnip ZIP missing unique libvulkan_freedreno.so")
        info=z.getinfo(library_names[0])
        if info.file_size<200000 or info.file_size>128*1024*1024:
            raise ValueError("Invalid Turnip shared library size")
        with z.open(info) as driver:
            header=driver.read(20)
            if header[:5]!=bytes([127,69,76,70,2]) or int.from_bytes(header[18:20],"little")!=183:
                raise ValueError("Turnip library is not ELF64 AArch64")
        if "meta.json" not in z.namelist():
            raise ValueError("Turnip ZIP missing AdrenoTools meta.json")




def package(directory):
    output = Path(directory)
    target = output / "bundled"
    target.mkdir(parents=True, exist_ok=True)
    manifest = {"schema": 1, "items": []}
    for component in FILES + drnas_turnip_assets():
        name = component["name"]
        archive = target / name
        tmp = target / (name + ".partial")
        tmp.unlink(missing_ok=True)
        try:
            subprocess.run(
                [
                    "curl", "--fail", "--location", "--retry", "3",
                    "--connect-timeout", "25", "--max-time", "360",
                    "--user-agent", "DRNAS-EmuExperimental-bundled/0.5.16",
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
            if component["kind"] == "Turnip":
                check_turnip_zip(tmp)
            tmp.replace(archive)
        finally:
            tmp.unlink(missing_ok=True)
        digest = hasher.hexdigest()
        if component.get("sha256") and digest != component["sha256"]:
            raise ValueError(f"{name}: pinned SHA-256 mismatch; refusing third-party runtime")
        print(f"{component['kind']} {name} size={size} sha256={digest}", flush=True)
        manifest["items"].append(
            {
                "kind": component["kind"],
                "name": name,
                "asset": "bundled/" + name,
                "compression": component["compression"],
                "bytes": size,
                "sha256": digest,
                **({"gpuTarget":component["gpuTarget"], "source":component["url"]}
                   if component["kind"]=="Turnip" else {}),
                **({"variant":component["variant"], "source":component["url"]}
                   if component["kind"]=="DXVK" else {}),
            }
        )
    (output / "runtime-components-v1.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Bundled mandatory WCPs + available published Drnas Turnip variants; no runtime network required.", flush=True)


if __name__ == "__main__":
    package(sys.argv[1])

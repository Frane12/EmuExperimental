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
        "compression": "XZ",
        "url": "https://github.com/K11MCH1/Winlator101/releases/download/dxvk_col/dxvk_1.10.3.wcp",
    },
]
MAGIC = {"XZ": bytes.fromhex("fd377a585a00"),
         "ZSTD": bytes.fromhex("28b52ffd"), "ZIP": bytes.fromhex("504b0304")}

def drnas_turnip_assets():
    """Choose only *published* Drnas Turnip release ZIPs, never draft/internal."""
    api = "https://api.github.com/repos/Frane12/Testturnip/releases?per_page=100"
    req = urllib.request.Request(api, headers={
        "User-Agent": "DRNAS-EmuExperimental-bundled/0.5.16",
        "Accept": "application/vnd.github+json"})
    try:
        with urllib.request.urlopen(req, timeout=40) as stream:
            releases = json.load(stream)
    except Exception as error:
        print(f"WARNING: Turnip release catalog unavailable: {error}", flush=True)
        return []
    result = []
    for gpu in ("A810", "A830", "A840"):
        options = []
        for release in releases:
            if release.get("draft") or release.get("prerelease"):
                continue
            tag = release.get("tag_name", "").lower()
            if "internal" in tag or "draft" in tag or "test" in tag:
                continue
            for asset in release.get("assets", []):
                name = asset.get("name", "")
                if (re.search(r"(?i)(?<![a-z0-9])" + gpu + r"(?![0-9])", name) and
                    name.lower().endswith(".zip") and
                    "source" not in name.lower() and "symbols" not in name.lower() and
                    asset.get("size", 0) > 100_000 and
                    asset.get("browser_download_url", "").startswith(
                        "https://github.com/Frane12/Testturnip/releases/download/")):
                    options.append((release.get("published_at", ""),
                                    asset.get("browser_download_url"), name))
        if not options:
            print(f"WARNING: No published {gpu} Drnas Turnip driver ZIP; system Vulkan fallback", flush=True)
            continue
        options.sort(reverse=True)
        published, url, original = options[0]
        result.append({"kind":"Turnip","name":"Drnas-Turnip-"+gpu+".zip",
                       "compression":"ZIP","url":url,"gpuTarget":gpu,
                       "releaseAssetName":original,"publishedAt":published})
        print(f"Drnas Turnip {gpu}: {original} ({published})", flush=True)
    return result

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
            }
        )
    (output / "runtime-components-v1.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print("Bundled mandatory WCPs + available published Drnas Turnip variants; no runtime network required.", flush=True)


if __name__ == "__main__":
    package(sys.argv[1])

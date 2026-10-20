#!/usr/bin/env python3
"""Fail-closed Windows ARM64 Blender distribution packaging for DRNAS Mali LAB.

Builds NOTHING: scans Git LFS content, verifies installed binaries/assets, writes
two separate ZIPs (runtime and optional private PDB symbols), and validates both.
Python stdlib only; preserves unmodified original build output.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import struct
import sys
import zipfile

LFS_HEADER = b"version https://git-lfs.github.com/spec/v1"
OFFICIAL_ZIP_BYTES = 254747440  # v1.1 official reference only, not a hard constraint
MAX_RUNTIME_ZIP = 350 * 1024 * 1024
MAX_RUNTIME_EXPANDED = 1024 * 1024 * 1024
MIN_INSTALLED_FILES = 5000
IMPORTANT = {
    "4.5/datafiles/assets/brushes/essentials_brushes-mesh_sculpt.blend": b"BLENDER",
    "4.5/datafiles/assets/geometry_nodes/procedural_hair_node_assets.blend": b"BLENDER",
    "4.5/scripts/startup/bl_app_templates_system/2D_Animation/startup.blend": b"BLENDER",
    "4.5/datafiles/studiolights/world/forest.exr": b"\x76\x2f\x31\x01",
}
SOURCE_IMPORTANT = {
    "assets/brushes/essentials_brushes-mesh_sculpt.blend": b"BLENDER",
    "assets/geometry_nodes/procedural_hair_node_assets.blend": b"BLENDER",
    "scripts/startup/bl_app_templates_system/2D_Animation/startup.blend": b"BLENDER",
    "release/datafiles/studiolights/world/forest.exr": b"\x76\x2f\x31\x01",
}

def checksum(filename: Path) -> str:
    dig = hashlib.sha256()
    with filename.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            dig.update(block)
    return dig.hexdigest()

def lfs_pointer(stream) -> bool:
    return stream.read(len(LFS_HEADER)) == LFS_HEADER

# Confirmed in Blender v4.5.14 upstream LFS source: essential brushes and hair
# assets are Zstandard-compressed .blend files (28 b5 2f fd), while startup
# templates are uncompressed (BLENDER). Both are legitimate. Avoid false FAIL.
ZSTD_FRAME_MAGIC = bytes.fromhex("28b52ffd")
GZIP_MAGIC = bytes.fromhex("1f8b")


def valid_asset_magic(filename: str, data: bytes, expected: bytes) -> bool:
    if data.startswith(LFS_HEADER):
        return False
    if expected == b"BLENDER" and filename.lower().endswith(".blend"):
        return any(data.startswith(magic) for magic in (
            b"BLENDER", ZSTD_FRAME_MAGIC, GZIP_MAGIC))
    return data.startswith(expected)


def check_magic(path: Path, expected: bytes) -> None:
    if not path.is_file() or path.is_symlink():
        raise ValueError(f"Missing/unsafe required Blender resource: {path}")
    if path.stat().st_size <= 1024:
        raise ValueError(f"Blender resource implausibly small / Git LFS pointer: {path}")
    with path.open("rb") as f:
        actual = f.read(max(132, len(expected)))
    if actual.startswith(LFS_HEADER):
        raise ValueError(f"Unresolved Git LFS pointer in required Blender resource: {path}")
    if not valid_asset_magic(path.name, actual, expected):
        raise ValueError(f"Incorrect Blender resource signature: {path}; expected raw/compressed Blender or EXR")

def source_scan(src: Path) -> dict:
    src = src.resolve(strict=True)
    counts = 0
    unresolved = []
    # These source subtrees supply packaged .blend/.exr assets.
    for folder in ("assets", "scripts", "release/datafiles"):
        path = src / folder
        if not path.is_dir():
            raise ValueError(f"Source subtree missing: {path}")
        for file in path.rglob("*"):
            if file.is_symlink():
                raise ValueError(f"Unexpected source symlink: {file}")
            if not file.is_file() or file.stat().st_size > 512:
                continue
            counts += 1
            with file.open("rb") as f:
                if lfs_pointer(f):
                    unresolved.append(str(file.relative_to(src)))
    if unresolved:
        raise ValueError(f"{len(unresolved)} unresolved source Git LFS pointers: "
                         + "; ".join(unresolved[:18]) +
                         "; run git lfs pull for assets/**, scripts/**, release/datafiles/**")
    for name, header in SOURCE_IMPORTANT.items():
        check_magic(src / name, header)
    return {"source_root": str(src), "checked_small_files": counts,
            "unresolved_lfs_pointers": 0, "critical_resources_valid": len(SOURCE_IMPORTANT)}

def pe_machine(exe: Path) -> int:
    with exe.open("rb") as f:
        hdr = f.read(4096)
    if len(hdr) < 256 or hdr[:2] != b"MZ":
        raise ValueError(f"Not a Windows PE binary: {exe}")
    offset = struct.unpack_from("<I", hdr, 60)[0]
    if offset + 6 > len(hdr) or hdr[offset:offset+4] != b"PE\x00\x00":
        raise ValueError(f"Invalid Windows PE signature: {exe}")
    return struct.unpack_from("<H", hdr, offset+4)[0]

def inspect_installed(root: Path) -> tuple[list[tuple[str, Path, int]], list[tuple[str, Path, int]]]:
    if not root.is_dir() or root.is_symlink():
        raise ValueError(f"Expected real installed Blender directory: {root}")
    exe = root / "blender.exe"
    if pe_machine(exe) != 0xAA64:
        raise ValueError(f"Expected Windows ARM64 machine 0xAA64: {exe}")
    for name, header in IMPORTANT.items():
        check_magic(root / name, header)

    runtime, symbols, names = [], [], set()
    found_pointers = []
    for item in root.rglob("*"):
        if item.is_symlink():
            raise ValueError(f"Refusing to package symlink: {item}")
        if not item.is_file():
            continue
        relative = item.relative_to(root).as_posix()
        normalized = PurePosixPath(relative)
        if relative.startswith("/") or ".." in normalized.parts:
            raise ValueError(f"Unsafe relative path: {relative}")
        key = relative.casefold()
        if key in names:
            raise ValueError(f"Duplicated case-insensitive ZIP entry: {relative}")
        names.add(key)
        size = item.stat().st_size
        if size <= 512:
            with item.open("rb") as f:
                if lfs_pointer(f):
                    found_pointers.append(relative)
        target = symbols if item.suffix.lower() == ".pdb" else runtime
        target.append((relative, item, size))
    if found_pointers:
        raise ValueError(f"{len(found_pointers)} Git LFS pointers found INSIDE Blender install: "
                         + "; ".join(found_pointers[:30]))
    if len(runtime) < MIN_INSTALLED_FILES:
        raise ValueError(f"Unexpectedly incomplete distribution: {len(runtime)} runtime files")
    expanded = sum(size for _, _, size in runtime)
    if expanded > MAX_RUNTIME_EXPANDED:
        raise ValueError(f"Runtime expanded size {expanded} > {MAX_RUNTIME_EXPANDED}; "
                         "unexpected bulk files still included")
    if not symbols:
        raise ValueError("No PDB files were retained; expected separate private debug symbols")
    if not any(name == "blender.pdb" for name, _, _ in symbols):
        raise ValueError("Missing Blender main PDB; cannot later map Wine crash offsets")
    return runtime, symbols

def create_zip(path: Path, entries: list[tuple[str, Path, int]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".partial")
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED,
                             compresslevel=6, allowZip64=True, strict_timestamps=False) as z:
            for name, item, _ in sorted(entries):
                z.write(item, arcname=name)
        with zipfile.ZipFile(temporary) as z:
            names = [e.filename for e in z.infolist() if not e.is_dir()]
            if z.testzip() is not None or len(names) != len(entries):
                raise ValueError(f"ZIP payload corrupt/incomplete: {path}")
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)

def verify_zip(path: Path, expected_names: set[str], deny_pdb: bool, required_magic: dict):
    with zipfile.ZipFile(path) as z:
        names = set(z.namelist())
        if names != expected_names:
            raise ValueError(f"ZIP inventory mismatch ({len(names)} instead of {len(expected_names)})")
        if deny_pdb and any(x.casefold().endswith(".pdb") for x in names):
            raise ValueError("Debug PDB was erroneously packaged into runtime ZIP")
        for name, head in required_magic.items():
            info=z.getinfo(name)
            if info.file_size <= 1024:
                raise ValueError(f"ZIP contains implausibly small/placeholder asset: {name}")
            with z.open(name) as f:
                data = f.read(132)
            if not valid_asset_magic(name, data, head):
                raise ValueError(f"ZIP contains invalid required asset {name}")
        if z.testzip() is not None:
            raise ValueError(f"ZIP CRC check failed: {path}")

def package(root: Path, output: Path) -> dict:
    root = root.resolve(strict=True)
    output.mkdir(parents=True, exist_ok=True)
    runtime, symbols = inspect_installed(root)
    runtime_name = "Blender-4.5.14-DRNAS-MaliVulkan-r64-LFSFIX-WindowsARM64-PRIVATE.zip"
    symbols_name = "Blender-4.5.14-DRNAS-MaliVulkan-r64-LFSFIX-PDB-PRIVATE.zip"
    runtime_file, symbols_file = output / runtime_name, output / symbols_name
    create_zip(runtime_file, runtime)
    verify_zip(runtime_file, {name for name, _, _ in runtime}, True, IMPORTANT)
    if runtime_file.stat().st_size > MAX_RUNTIME_ZIP:
        raise ValueError(f"Runtime ZIP {runtime_file.stat().st_size} bytes > "
                         f"strict cap {MAX_RUNTIME_ZIP}; investigate before publishing")
    create_zip(symbols_file, symbols)
    verify_zip(symbols_file, {name for name, _, _ in symbols}, False, {})
    manifest = {
        "note": "PRIVATE TEST; Windows ARM64 custom Blender 4.5.14, not official release",
        "source_blender_version": "4.5.14",
        "machine": "0xAA64",
        "original_official_sha256": "0153ecefc96a0e23985e6edcdbe04e909ba2078258f4ff6a05c21d4b7c756911",
        "previous_broken_r64_sha256": "8802767f9ac63a6e8054e6e37b3ba994b49c2fb6d37d34cfc50ae48491851d6f",
        "upstream_blender_commit": os.getenv("BLENDER_SOURCE_COMMIT", "unavailable"),
        "toolchain": os.getenv("LLVM_EXE", "unavailable"),
        "exe_sha256": checksum(root / "blender.exe"),
        "runtime": {"file": runtime_name, "sha256": checksum(runtime_file),
                    "bytes": runtime_file.stat().st_size,
                    "files": len(runtime), "expanded_bytes": sum(x[2] for x in runtime)},
        "symbols": {"file": symbols_name, "sha256": checksum(symbols_file),
                    "bytes": symbols_file.stat().st_size,
                    "files": len(symbols), "expanded_bytes": sum(x[2] for x in symbols)},
        "verified_assets": list(IMPORTANT),
        "unresolved_git_lfs_pointers": 0,
        "pdb_in_runtime": 0,
        "symbols_kept_separate": True,
        "device_tested": False,
        "gpu_validated": False,
    }
    (output / "DISTRIBUTION-MANIFEST.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    with (output / "SHA256SUMS.txt").open("w", encoding="ascii", newline="\n") as f:
        for p in (runtime_file, symbols_file):
            f.write(f"{checksum(p)}  {p.name}\n")
    (output / "README.txt").write_text(
        "DRNAS Blender 4.5.14 Mali Vulkan experimental build: NOT DEVICE VERIFIED.\n"
        "Source: upstream v4.5.14 plus DRNAS Mali Vulkan patch.\n"
        "Git LFS source/install pointers: NONE; PDB only in separate PRIVATE symbol ZIP.\n"
        "Use PDB with the exact matching blender.exe for later Wine crash analysis.\n"
        "This is not the official Blender build or an Android APK. "
        "Do not publish without GPL compliance, attribution and device QA.\n",
        encoding="utf-8")
    print(json.dumps(manifest, indent=2))
    return manifest

def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest="command", required=True)
    s = sub.add_parser("source-scan", help="Fail if source Git LFS assets are pointers")
    s.add_argument("source", type=Path)
    b = sub.add_parser("package", help="Validate installed Blender and package PDB separately")
    b.add_argument("install_root", type=Path)
    b.add_argument("output", type=Path)
    a = p.parse_args()
    if a.command == "source-scan":
        print(json.dumps(source_scan(a.source), indent=2))
    elif a.command == "package":
        package(a.install_root, a.output)

if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, zipfile.BadZipFile) as exc:
        sys.exit(f"DRNAS_R64_RELEASE_PREPARATION_FAIL: {exc}")

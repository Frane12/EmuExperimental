#!/usr/bin/env python3
"""Package a VERIFIED private Blender r64 Windows ARM64 ZIP for an ISOLATED Android LAB.

The input is a compiled Blender distribution ZIP, NOT the official stock ZIP.
This helper does not download anything and cannot update the public release.
Requires a source ZIP checked against the matching private release's SHA256SUMS.
"""
from __future__ import annotations

import argparse
import hashlib
import pathlib
import re
import shutil
import stat
import sys
import zipfile

ROOT = "blender-4.5.14-windows-arm64/"
NAME = "blender-4.5.14-windows-arm64.zip"
MAX_FILES = 50_000
MAX_EXPANDED = 3 * 1024**3
MAX_SOURCE_ZIP = 1900 * 1024**2


def replace_one(path: pathlib.Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise ValueError(f"Refusing to rewrite {path}: {count} matches for {old[:65]!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


def verify_pe_arm64_header(head: bytes) -> None:
    if len(head) < 256 or head[:2] != b"MZ":
        raise ValueError("Invalid blender.exe DOS header")
    offset = int.from_bytes(head[60:64], "little")
    if offset < 64 or offset + 6 > len(head):
        raise ValueError("Unrecognized blender.exe PE header offset")
    if head[offset : offset + 4] != b"PE\0\0":
        raise ValueError("Invalid blender.exe PE signature")
    if int.from_bytes(head[offset + 4 : offset + 6], "little") != 0xAA64:
        raise ValueError("blender.exe is NOT Windows ARM64 (machine != 0xAA64)")


def package(source_path: pathlib.Path, assets_path: pathlib.Path,
            archive_java: pathlib.Path, bundle_java: pathlib.Path,
            gradle_path: pathlib.Path) -> tuple[str, int]:
    if not source_path.is_file():
        raise ValueError(f"Missing previously verified Windows ARM64 ZIP: {source_path}")
    if source_path.stat().st_size > MAX_SOURCE_ZIP:
        raise ValueError("Compiled Windows Blender ZIP too large")

    target_dir = assets_path / "blender"
    target_dir.mkdir(parents=True, exist_ok=True)
    target = target_dir / NAME
    temporary = target_dir / (NAME + ".part")
    temporary.unlink(missing_ok=True)

    seen: set[str] = set()
    count = 0
    expanded = 0
    try:
        with zipfile.ZipFile(source_path, "r") as src, zipfile.ZipFile(
            temporary, "w", compression=zipfile.ZIP_DEFLATED,
            compresslevel=6, allowZip64=True
        ) as dst:
            for entry in src.infolist():
                name = entry.filename
                if name.startswith(ROOT):
                    name = name[len(ROOT):]
                if entry.is_dir():
                    continue
                if (not name or "\\" in name or name.startswith("/")
                        or ":" in name or "\x00" in name):
                    raise ValueError(f"Unsafe Windows Blender archive member: {name!r}")
                parts = name.split("/")
                if any(part in ("", ".", "..") for part in parts):
                    raise ValueError(f"Unsafe Blender path: {name!r}")
                if name in seen:
                    raise ValueError(f"Duplicate Blender path: {name!r}")
                seen.add(name)
                filemode = (entry.external_attr >> 16)
                if stat.S_ISLNK(filemode):
                    raise ValueError(f"Blender archive contains symlink: {name!r}")
                count += 1
                expanded += entry.file_size
                if count > MAX_FILES or expanded > MAX_EXPANDED:
                    raise ValueError("Excessive count/expanded size in Windows Blender archive")
                with src.open(entry, "r") as in_file, dst.open(ROOT + name, "w") as out_file:
                    shutil.copyfileobj(in_file, out_file, 1024 * 1024)

        with zipfile.ZipFile(temporary, "r") as finished:
            if ROOT + "blender.exe" not in finished.namelist():
                raise ValueError("Compiled distribution ZIP does not contain blender.exe")
            with finished.open(ROOT + "blender.exe") as exe:
                verify_pe_arm64_header(exe.read(4096))
            if finished.testzip() is not None:
                raise ValueError("Repackaged ZIP has a CRC/contents error")
        digest = hashlib.sha256()
        with temporary.open("rb") as f:
            for block in iter(lambda: f.read(1024 * 1024), b""):
                digest.update(block)
        checksum = digest.hexdigest()
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)

    # The app installer verifies this exact new archive hash on first use.
    replace_one(
        archive_java,
        'static final String SHA256="0153ecefc96a0e23985e6edcdbe04e909ba2078258f4ff6a05c21d4b7c756911";',
        f'static final String SHA256="{checksum}";',
    )
    ceiling = min(MAX_EXPANDED, expanded + 192 * 1024**2)
    replace_one(
        archive_java,
        'static final long MAX_EXPANDED=1024L*1024*1024;',
        f'static final long MAX_EXPANDED={ceiling}L;',
    )
    minimum_free = expanded + 192 * 1024**2
    replace_one(
        bundle_java,
        'available.getUsableSpace()<900L*1024*1024',
        f'available.getUsableSpace()<{minimum_free}L',
    )

    # Distinct LAB applicationId protects the user's installed DRNAS Blender.
    replace_one(
        gradle_path,
        'val drnasPrefixRecoveryLab = System.getenv("DRNAS_PREFIX_RECOVERY_LAB") == "1"',
        'val drnasPrefixRecoveryLab = System.getenv("DRNAS_PREFIX_RECOVERY_LAB") == "1"\n'
        'val drnasMaliR64Lab = System.getenv("DRNAS_R64_MALI_LAB") == "1"',
    )
    replace_one(
        gradle_path,
        'versionCode = 130',
        'versionCode = 131',
    )
    replace_one(
        gradle_path,
        'versionName = "1.1"',
        'versionName = "1.1-r64-mali-lab"',
    )
    replace_one(
        gradle_path,
        '            if (drnasPrefixRecoveryLab) {',
        '            if (drnasMaliR64Lab) {\n'
        '                applicationIdSuffix = ".malir64lab"\n'
        '                manifestPlaceholders["appLabel"] = "DRNAS Blender Mali r64 LAB"\n'
        '            } else if (drnasPrefixRecoveryLab) {',
    )

    print(f"Verified and embedded patched Windows ARM64 Blender: {target}")
    print(f"Blender bundle SHA-256: {checksum}")
    print(f"Blender uncompressed bytes: {expanded:,} in {count:,} files")
    print("Isolated Android package: com.drnas.console.malir64lab (versionCode 131)")
    return checksum, expanded


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("compiled_zip", type=pathlib.Path)
    p.add_argument("--app", type=pathlib.Path, default=pathlib.Path("android-app/app"))
    args = p.parse_args()
    app = args.app
    package(
        args.compiled_zip,
        app / "src/main/assets",
        app / "src/main/java/com/drnas/console/BundledBlenderArchive.java",
        app / "src/main/java/com/drnas/console/BundledBlender.java",
        app / "build.gradle.kts",
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Drnas Console WCP file importer prototype.

Standalone POSIX-side reference. NOT an Android runtime/launcher.
Never executes archive contents or writes into any active Wine prefix.
Supports user-provided Wine, Proton, DXVK and FEXCore packages with tar, XZ,
or Zstandard compression (zstd must be installed for the latter).
"""
import argparse
import contextlib
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tarfile
import tempfile

MAX_MEMBERS = 10000
MAX_UNPACKED = 4 * 1024**3
MAX_FILE = 1024**3
TARGET = re.compile(r"^\$\{(system32|syswow64)\}/([a-zA-Z0-9_]+\.dll)$")
SAFE_NAME = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,80}$")
FEXCORE_PE_DLLS = frozenset({
    "${system32}/libarm64ecfex.dll",
    "${system32}/libwow64fex.dll",
})
FEXCORE_UNIX_SO = frozenset({
    "${libdir}/wine/aarch64-unix/libarm64ecfex.so",
    "${libdir}/wine/aarch64-unix/libwow64fex.so",
})
FEXCORE_TARGETS = FEXCORE_PE_DLLS | FEXCORE_UNIX_SO

class WCPError(ValueError):
    pass

def fingerprint(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def compression(path):
    with open(path, "rb") as f:
        first = f.read(6)
    if first.startswith(bytes.fromhex("28b52ffd")): return "zstd"
    if first.startswith(bytes.fromhex("fd377a585a00")): return "xz"
    if first.startswith(bytes.fromhex("1f8b")): return "gzip"
    return "tar"

def normalized_archive_path(raw):
    if not raw or raw.startswith("/") or "\\" in raw or "\x00" in raw:
        raise WCPError("Unsafe archive path")
    parts = PurePosixPath(raw).parts
    if not parts or any(x in (".", "..") for x in raw.split("/")) and ".." in raw.split("/"):
        raise WCPError("Path traversal forbidden: " + raw)
    clean = "/".join(parts)
    if clean in ("", "."): raise WCPError("Unsafe root entry")
    return clean

def validate(profile, paths):
    if not isinstance(profile, dict): raise WCPError("Manifest must be an object")
    kind = profile.get("type")
    if kind not in ("Wine", "Proton", "DXVK", "FEXCore"):
        raise WCPError("Unsupported type: " + str(kind))
    ver = profile.get("versionName")
    if not isinstance(ver, str) or not SAFE_NAME.fullmatch(ver):
        raise WCPError("Unsafe or missing versionName")
    mappings = profile.get("files")
    if not isinstance(mappings, list): raise WCPError("Missing files array")
    if kind in ("Wine", "Proton"):
        section = profile.get(kind.lower())
        if not isinstance(section, dict):
            # Some producers label Proton paths under "wine".
            section = profile.get("wine" if kind == "Proton" else "proton")
        if not isinstance(section, dict):
            raise WCPError("Wine/Proton metadata missing: need binPath/libPath/prefixPack")
        for k in ("binPath", "libPath", "prefixPack"):
            v = section.get(k)
            if not isinstance(v, str): raise WCPError("Missing " + k)
            v = normalized_archive_path(v)
            if k == "prefixPack":
                if v not in paths: raise WCPError("Missing prefixPack: " + v)
            elif not any(x.startswith(v + "/") for x in paths):
                raise WCPError("Missing nonempty " + k + ": " + v)
        if mappings:
            raise WCPError("Wine/Proton file overrides not supported in this importer prototype")
    elif kind == "DXVK":
        if not mappings: raise WCPError("DXVK is missing DLL mappings")
        for entry in mappings:
            if not isinstance(entry, dict): raise WCPError("Bad DXVK mapping")
            src = normalized_archive_path(str(entry.get("source", "")))
            target = entry.get("target")
            if src not in paths or not isinstance(target, str) or not TARGET.fullmatch(target):
                raise WCPError("Missing source or untrusted DXVK destination: " + str(entry))
            if PurePosixPath(src).name != TARGET.fullmatch(target).group(2):
                raise WCPError("DLL target mismatch")
    else:  # FEXCore: an ABI-specific Wine/Proton bridge, NOT a standalone executable.
        if not mappings: raise WCPError("FEXCore has no module mappings")
        selected = set()
        for entry in mappings:
            if not isinstance(entry, dict): raise WCPError("Bad FEXCore mapping")
            src = normalized_archive_path(str(entry.get("source", "")))
            target = entry.get("target")
            if src not in paths or not isinstance(target, str) or target not in FEXCORE_TARGETS:
                raise WCPError("Missing source or untrusted FEXCore destination: " + str(entry))
            if PurePosixPath(src).name != PurePosixPath(target).name:
                raise WCPError("FEXCore target basename mismatch")
            if target in selected: raise WCPError("Duplicate FEXCore mapping")
            selected.add(target)
        if not (FEXCORE_PE_DLLS <= selected or FEXCORE_UNIX_SO <= selected):
            raise WCPError("FEXCore needs a complete pair of Wine bridge DLLs or Unix .so modules")
    return kind, ver

def import_wcp(source, root, dry_run=False):
    """Validate, optionally store to immutable version/hash directory.

    Extraction occurs only in private staging, not into Wine/Android system.
    """
    source = Path(source)
    root = Path(root)
    if not source.is_file(): raise WCPError("WCP file does not exist")
    sha = fingerprint(source)
    ctype = compression(source)
    cmd = {"zstd":["zstd", "-dc", "--", str(source)],
           "xz":["xz", "-dc", "--", str(source)],
           "gzip":["gzip", "-dc", "--", str(source)]}
    if not dry_run:
        root.mkdir(parents=True, exist_ok=True)
        staging = Path(tempfile.mkdtemp(prefix=".wcp-staging-", dir=root))
    else:
        staging = None
    seen, total, profile = set(), 0, None
    proc = None
    try:
        if ctype == "tar":
            pipe = open(source, "rb")
        else:
            try:
                proc = subprocess.Popen(cmd[ctype], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            except OSError as e:
                raise WCPError("Decoder missing or unavailable: " + str(e)) from e
            pipe = proc.stdout
        try:
            with tarfile.open(fileobj=pipe, mode="r|") as t:
                for member in t:
                    if member.isdir() and member.name in (".", "./"):
                        continue  # valid TAR root directory entry, found in actual DXVK WCP
                    name = normalized_archive_path(member.name)
                    if name in seen: raise WCPError("Duplicate archive path: " + name)
                    seen.add(name)
                    if len(seen) > MAX_MEMBERS: raise WCPError("Too many members")
                    if not (member.isfile() or member.isdir()):
                        raise WCPError("Links and special files prohibited: " + name)
                    if member.size > MAX_FILE: raise WCPError("Member too large: " + name)
                    total += member.size
                    if total > MAX_UNPACKED: raise WCPError("Expanded archive exceeds size cap")
                    profile_bytes = None
                    if name == "profile.json" and member.isfile():
                        if member.size > 128 * 1024: raise WCPError("Manifest too large")
                        profile_bytes = t.extractfile(member).read()
                        profile = json.loads(profile_bytes)
                    if staging is not None:
                        out = staging.joinpath(*PurePosixPath(name).parts)
                        if member.isdir():
                            out.mkdir(parents=True, exist_ok=True)
                        else:
                            out.parent.mkdir(parents=True, exist_ok=True)
                            mode = os.O_WRONLY | os.O_CREAT | os.O_EXCL
                            if hasattr(os, "O_NOFOLLOW"): mode |= os.O_NOFOLLOW
                            with os.fdopen(os.open(out, mode, 0o600), "wb") as dest:
                                if profile_bytes is not None:
                                    dest.write(profile_bytes)  # stream is forward-only; do not open member twice
                                else:
                                    with t.extractfile(member) as f:
                                        shutil.copyfileobj(f, dest, length=1024*1024)
        finally:
            if pipe is not None: pipe.close()
        if proc is not None:
            err = proc.stderr.read().decode("utf-8", "replace")
            code = proc.wait()
            proc.stderr.close()
            if code != 0: raise WCPError("Archive decompression failed: " + err[-900:])
        if profile is None: raise WCPError("Root profile.json missing")
        kind, version = validate(profile, seen)
        metadata = {"type":kind, "version":version, "sha256":sha,
                    "compression":ctype, "members":len(seen),
                    "expandedBytes":total, "manifest":profile,
                    "status":"imported_unvalidated"}
        if dry_run:
            return metadata
        install = root / kind.lower() / (version + "-" + sha[:12])
        install.parent.mkdir(parents=True, exist_ok=True)
        if install.exists(): raise WCPError("Already installed: " + str(install))
        (staging / "drnas-component.json").write_text(
            json.dumps(metadata, indent=2, ensure_ascii=False) + "\n")
        staging.rename(install)
        metadata["installedAt"] = str(install)
        staging = None
        return metadata
    finally:
        if proc is not None and proc.poll() is None:
            proc.kill()
            proc.wait()
        if proc is not None and proc.stderr is not None and not proc.stderr.closed:
            proc.stderr.close()
        if staging is not None: shutil.rmtree(staging, ignore_errors=True)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="WCP offline importer PROTOTYPE; does not run games")
    parser.add_argument("file", help="local .wcp archive")
    parser.add_argument("--root", default="./drnas-content-store")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    print(json.dumps(import_wcp(args.file,args.root,args.dry_run),indent=2,ensure_ascii=False))

#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 DRNAS contributors
# SPDX-License-Identifier: GPL-2.0-or-later
"""Read-only by default: validate and optionally install DRNAS Native source overlay.

This touches ONLY a user-supplied checkout of the pinned upstream Android Blender
source. It never builds, downloads, signs or publishes an APK.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile

UPSTREAM_SHA = "4bd0075178bdf71c9aa3de139af6d07e9da61b4d"
APP_ID = "com.drnas.blendernative"
JAVA_ROOT = Path("build_files/android/apk/app/src/main/java/org/blender/blender")
ACTIVITY = JAVA_ROOT / "BlenderActivity.java"
OVERLAY = JAVA_ROOT / "DrnasNativeControls.java"
MANIFEST = Path("build_files/android/apk/app/src/main/AndroidManifest.xml")
GRADLE = Path("build_files/android/apk/app/build.gradle")
PACKAGE = Path("build_files/android/apk/package.sh")
RUNNER = Path("build_files/android/build.py")


def replace_once(source: str, old: str, new: str, location: str) -> str:
    """Require an exact, unambiguous upstream anchor; reruns are idempotent."""
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise ValueError(f"{location}: expected one upstream anchor, found {count}")
    return source.replace(old, new, 1)


def transform(path: Path, original: str) -> str:
    """Prepare changes in memory, no writes until all source anchors validate."""
    if path == ACTIVITY:
        result = replace_once(
            original,
            "  private InputView inputView;\n",
            "  private InputView inputView;\n  private DrnasNativeControls drnasControls;\n",
            str(path))
        result = replace_once(
            result,
            "    addContentView(inputView, new ViewGroup.LayoutParams(1, 1));\n",
            "    addContentView(inputView, new ViewGroup.LayoutParams(1, 1));\n"
            "    drnasControls = new DrnasNativeControls(this,\n"
            "        (keyCode, action, metaState) -> nativeOnKey(keyCode, action, metaState));\n"
            "    drnasControls.install();\n",
            str(path))
        result = replace_once(
            result,
            "    super.onWindowFocusChanged(hasFocus);\n",
            "    super.onWindowFocusChanged(hasFocus);\n"
            "    if (!hasFocus && drnasControls != null) {\n"
            "      drnasControls.releaseAll();\n"
            "    }\n",
            str(path))
        return result
    if path == PACKAGE:
        old = '  "$SCRIPT_DIR/app/src/main/java/org/blender/blender/BlenderActivity.java"'
        new = old + ' \\\n' + '  "$SCRIPT_DIR/app/src/main/java/org/blender/blender/DrnasNativeControls.java"'
        return replace_once(original, old, new, str(path))
    if path == MANIFEST:
        out = replace_once(original, 'package="org.blender.blender"',
                           f'package="{APP_ID}"', str(path))
        return out.replace('android:label="Blender"',
                           'android:label="DRNAS Blender Native"')
    if path == GRADLE:
        out = replace_once(original, "applicationId 'org.blender.blender'",
                           f"applicationId '{APP_ID}'", str(path))
        return replace_once(out, "versionName '5.3-android'",
                            "versionName '5.3-drnas-native-v0.1'", str(path))
    if path == RUNNER:
        out = replace_once(original, 'PACKAGE = "org.blender.blender"',
                           f'PACKAGE = "{APP_ID}"', str(path))
        return replace_once(out, 'ACTIVITY = f"{PACKAGE}/.BlenderActivity"',
                            'ACTIVITY = f"{PACKAGE}/org.blender.blender.BlenderActivity"',
                            str(path))
    raise ValueError(f"unknown overlay target: {path}")


def git_head(directory: Path) -> str:
    return subprocess.check_output(
        ["git", "-C", str(directory), "rev-parse", "HEAD"],
        text=True, stderr=subprocess.PIPE).strip()


def atomic_write(path: Path, contents: str) -> None:
    fd, temp = tempfile.mkstemp(dir=path.parent, prefix=".drnas-native-", suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as stream:
            stream.write(contents)
        os.chmod(temp, path.stat().st_mode & 0o777)
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


def prepare(checkout: Path, overlay_java: Path, apply: bool) -> list[Path]:
    checkout = checkout.resolve()
    if git_head(checkout) != UPSTREAM_SHA:
        raise ValueError(
            "upstream revision mismatch: pin to " + UPSTREAM_SHA +
            " before applying this overlay; do not blindly patch newer builds")
    changes: dict[Path, str] = {}
    for relative in (ACTIVITY, MANIFEST, GRADLE, PACKAGE, RUNNER):
        target = checkout / relative
        if not target.is_file():
            raise FileNotFoundError(target)
        original = target.read_text(encoding="utf-8")
        patched = transform(relative, original)
        if original != patched:
            changes[target] = patched
    source = overlay_java.read_text(encoding="utf-8")
    destination = checkout / OVERLAY
    if destination.exists():
        if destination.read_text(encoding="utf-8") != source:
            raise ValueError("existing DRNAS Java overlay differs; aborting")
    else:
        changes[destination] = source

    print(("APPLY" if apply else "DRY-RUN") +
          f": {len(changes)} file(s) to update; upstream pin verified")
    for name in changes:
        print(" ", name.relative_to(checkout))
    if apply:
        for name, contents in changes.items():
            if name.exists():
                atomic_write(name, contents)
            else:
                name.write_text(contents, encoding="utf-8")
        print("source overlay written; NO compile or APK build was started")
    return list(changes)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("upstream_checkout", type=Path)
    parser.add_argument("--apply", action="store_true",
                        help="write source overlay; default validates without writing")
    args = parser.parse_args()
    overlay_java = (Path(__file__).resolve().parent.parent /
                    "overlay/DrnasNativeControls.java")
    try:
        prepare(args.upstream_checkout, overlay_java, args.apply)
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        print("ERROR:", exc, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

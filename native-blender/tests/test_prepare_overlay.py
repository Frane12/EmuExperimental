# SPDX-FileCopyrightText: 2026 DRNAS contributors
# SPDX-License-Identifier: GPL-2.0-or-later
"""Offline anchor/idempotency checks; does not fetch or compile anything."""

import importlib.util
from pathlib import Path
import unittest

path = Path(__file__).resolve().parents[1] / "tools/prepare_upstream.py"
spec = importlib.util.spec_from_file_location("drnas_native_prepare", path)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class TestOverlay(unittest.TestCase):
    def test_replace_once_and_idempotency(self):
        self.assertEqual(mod.replace_once("a\nb", "a\n", "c\n", "test"), "c\nb")
        self.assertEqual(mod.replace_once("c\nb", "a\n", "c\n", "test"), "c\nb")

    def test_conflicting_or_missing_anchor_fails_closed(self):
        with self.assertRaises(ValueError):
            mod.replace_once("two two", "two", "three", "test")
        with self.assertRaises(ValueError):
            mod.replace_once("unknown", "two", "three", "test")

    def test_activity_overlay_anchors(self):
        upstream = ("  private InputView inputView;\n"
                    "    addContentView(inputView, new ViewGroup.LayoutParams(1, 1));\n"
                    "    super.onWindowFocusChanged(hasFocus);\n")
        first = mod.transform(mod.ACTIVITY, upstream)
        self.assertIn("drnasControls.install()", first)
        self.assertIn("drnasControls.releaseAll()", first)
        self.assertEqual(first, mod.transform(mod.ACTIVITY, first))

    def test_package_compiles_both_java_files(self):
        source = '  "$SCRIPT_DIR/app/src/main/java/org/blender/blender/BlenderActivity.java"'
        patched = mod.transform(mod.PACKAGE, source)
        self.assertIn('DrnasNativeControls.java', patched)
        self.assertEqual(patched, mod.transform(mod.PACKAGE, patched))

    def test_manifest_and_runner_have_separate_id(self):
        manifest = '<manifest package="org.blender.blender" android:label="Blender">'
        changed = mod.transform(mod.MANIFEST, manifest)
        self.assertIn('com.drnas.blendernative', changed)
        self.assertEqual(mod.transform(mod.MANIFEST, changed), changed)
        runner = 'PACKAGE = "org.blender.blender"\nACTIVITY = f"{PACKAGE}/.BlenderActivity"'
        changed_run = mod.transform(mod.RUNNER, runner)
        self.assertIn('org.blender.blender.BlenderActivity', changed_run)
        self.assertEqual(mod.transform(mod.RUNNER, changed_run), changed_run)


if __name__ == "__main__":
    unittest.main()

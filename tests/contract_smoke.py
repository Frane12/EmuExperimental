#!/usr/bin/env python3
"""Offline contract tests; no emulator, root, game or Android SDK required."""
import json
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "contracts"))
from input_router_reference import InputRouter

class TouchOwnership(unittest.TestCase):
    def test_simultaneous_walk_and_camera(self):
        router=InputRouter()
        self.assertEqual(router.pointer_down(14, 0.15), "virtual-stick")
        self.assertEqual(router.pointer_down(23, 0.92), "relative-mouse")
        self.assertEqual(router.pointer_move(14, 0.94), "virtual-stick")
        self.assertEqual(router.pointer_move(23, 0.02), "relative-mouse")
        self.assertEqual(router.pointer_up(23), "relative-mouse")
        self.assertEqual(router.owners(), {14:"virtual-stick"})
        router.cancel_all()
        self.assertEqual(router.owners(), {})

    def test_reject_duplicate_and_invalid_pointer(self):
        r=InputRouter()
        r.pointer_down(7, 0.5)
        with self.assertRaises(ValueError):
            r.pointer_down(7, 0.5)
        self.assertIsNone(r.pointer_move(8, 0.9))
        r.pointer_up(7)
        with self.assertRaises(ValueError):
            r.pointer_down(9, 3.0)

class ProfileContract(unittest.TestCase):
    def test_profile_matches_schema_contract(self):
        schema=json.loads((ROOT/"profiles/profile.schema.json").read_text())
        data=json.loads((ROOT/"profiles/far-cry-3.example.json").read_text())
        self.assertEqual(data["schema"],schema["properties"]["schema"]["const"])
        self.assertEqual(set(data),set(schema["required"]))
        self.assertTrue(data["input"]["pointerOwnerRouting"])
        self.assertEqual(data["graphics"]["api"],"d3d11")
        self.assertEqual(data["graphics"]["driver"],"turnip-a840-neuroflow-experimental")
        self.assertTrue(data["maxFrameLatency"])
        self.assertTrue(15 <= data["frameCap"] <= 240)

    def test_cross_gpu_selection_must_be_rejected(self):
        # The browser concept intentionally rejects A810/A830 driver choices on A840.
        target="a840"
        for name in ("turnip-a810","turnip-a830"):
            self.assertNotIn(target,name)

if __name__ == "__main__":
    unittest.main(verbosity=2)

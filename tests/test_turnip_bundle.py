import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("bundle", Path(__file__).parents[1] / "tools/package_bundled_runtime.py")
bundle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bundle)


class TurnipBundleTest(unittest.TestCase):
    def test_all_supported_gpus_have_reproducible_published_drivers(self):
        drivers = bundle.drnas_turnip_assets()
        self.assertEqual([x["gpuTarget"] for x in drivers], ["A810", "A830", "A840"])
        for driver in drivers:
            self.assertRegex(driver["sha256"], r"^[0-9a-f]{64}$")
            self.assertTrue(driver["url"].startswith("https://github.com/Frane12/Testturnip/releases/download/"))
            self.assertNotIn("Source", driver["url"])
        self.assertIn("TESTING-ONLY", drivers[2]["url"])
        self.assertEqual(drivers, bundle.drnas_turnip_assets())

    def test_a830_prerelease_is_not_dropped_and_a840_hash_is_pinned(self):
        by_gpu = {d["gpuTarget"]: d for d in bundle.drnas_turnip_assets()}
        self.assertIn("drnas-turnip-s1-a830-smart", by_gpu["A830"]["url"])
        self.assertEqual(by_gpu["A840"]["sha256"], "033039c5c9843ced2f119387bdc0b21af3fa359f97d8fabaa3189dbc411510bc")

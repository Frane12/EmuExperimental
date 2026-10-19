import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("bundle", Path(__file__).parents[1] / "tools/package_bundled_runtime.py")
bundle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bundle)


class TurnipBundleTest(unittest.TestCase):
    def test_picker_catalog_matches_every_embedded_archive(self):
        catalog=(Path(__file__).parents[1]/"android-app/app/src/main/java/com/drnas/console/GraphicsDriverCatalog.java").read_text()
        drivers=bundle.drnas_turnip_assets()
        self.assertEqual(len({d['name'] for d in drivers}),len(drivers))
        for d in drivers:
            self.assertIn('"'+d['name']+'"',catalog)
            self.assertIn('"'+d['sha256']+'"',catalog)
    def test_boot_manifest_accepts_eight_pinned_components(self):
        # Regression for 2026-10-18 BOOT BLOCKED before Wine even started:
        # Proton + FEXCore + 2 DXVK variants + A740/A810/A830/A840 = eight.
        components = bundle.FILES + bundle.drnas_turnip_assets()
        self.assertEqual(len(components), 11)
        self.assertEqual(sum(c["kind"]=="DXVK" for c in components), 2)
        self.assertEqual(sum(c["kind"]=="Turnip" for c in components), 7)
        root = Path(__file__).parents[1]
        installer = (root/"android-app/app/src/main/java/com/drnas/console/BundledComponents.java").read_text()
        self.assertIn("items.length() > 11", installer)
        self.assertIn('gpuTarget.equals(TurnipDevicePolicy.LEGACY)', installer)
        self.assertIn('kinds.contains("DXVKasync")', installer)

    def test_all_supported_gpus_have_reproducible_published_drivers(self):
        drivers = bundle.drnas_turnip_assets()
        self.assertEqual([x["gpuTarget"] for x in drivers], ["A6XX_A7XX", "A810", "A830", "A840", "A810", "A830", "A840"])
        for driver in drivers:
            self.assertRegex(driver["sha256"], r"^[0-9a-f]{64}$")
            self.assertTrue(driver["url"].startswith("https://github.com/StevenMXZ/Adreno-Tools-Drivers/releases/download/" if driver["gpuTarget"]=="A6XX_A7XX" else "https://github.com/Frane12/Testturnip/releases/download/"))
            self.assertNotIn("Source", driver["url"])
        self.assertIn("TESTING-ONLY", drivers[3]["url"])
        self.assertEqual(drivers, bundle.drnas_turnip_assets())

    def test_a830_prerelease_is_not_dropped_and_a840_hash_is_pinned(self):
        by_gpu = {d["gpuTarget"]: d for d in bundle.drnas_turnip_assets()[:4]}
        self.assertIn("drnas-turnip-s1-a830-smart", by_gpu["A830"]["url"])
        self.assertIn("H2.9",by_gpu["A840"]["url"])
        self.assertEqual(by_gpu["A840"]["sha256"], "6b67ba723b2a4881214591055c637e2e513363c44874559d16e2a13b1019bb3d")

    def test_a740_uses_unchanged_upstream_a7xx_not_a840_experiment(self):
        d = bundle.drnas_turnip_assets()[0]
        self.assertEqual(d["sha256"], "5fa2305eb9eff64d9347a670e6ca5ef7c518f60e9e782a4db7ee5588c9852d81")
        self.assertIn("/Turnip_v26.3.0-R6.zip", d["url"])
        self.assertNotIn("a8xx", d["url"])

    def test_validates_both_library_names_and_rejects_metadata_path(self):
        import tempfile, zipfile, json
        header = bytearray(200001)
        header[:5] = b"\x7fELF\x02"
        header[18:20] = (183).to_bytes(2,"little")
        with tempfile.TemporaryDirectory() as directory:
            archive=Path(directory)/"driver.zip"
            for name in ("libvulkan_freedreno.so","vulkan.ad07xx.so"):
                with zipfile.ZipFile(archive,"w") as z:
                    z.writestr("meta.json",json.dumps({"libraryName":name}))
                    z.writestr(name,header)
                bundle.check_turnip_zip(archive)
            with zipfile.ZipFile(archive,"w") as z:
                z.writestr("meta.json",json.dumps({"libraryName":"../untrusted.so"}))
                z.writestr("../untrusted.so",header)
            with self.assertRaises(ValueError):bundle.check_turnip_zip(archive)


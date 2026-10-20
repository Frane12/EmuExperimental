#!/usr/bin/env python3
"""No-build fixtures for LFS/PDB stripping and Windows ARM64 installer guardrails."""
import json
import pathlib
import struct
import sys
import tempfile
import unittest
import zipfile
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1] / "tools"))
import prepare_r64_blender_distribution as prep

class BlenderReleasePreparationTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=pathlib.Path(self.temp.name)

    @staticmethod
    def write(path: pathlib.Path, data: bytes):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    @staticmethod
    def fake_arm64_pe() -> bytes:
        b=bytearray(4096)
        b[:2]=b"MZ"
        struct.pack_into("<I", b, 60, 128)
        b[128:132]=b"PE\x00\x00"
        struct.pack_into("<H", b, 132, 0xAA64)
        return bytes(b)

    def install_tree(self):
        root=self.root/"installed"
        self.write(root/"blender.exe", self.fake_arm64_pe())
        self.write(root/"blender.pdb", b"private symbols only")
        self.write(root/"4.5/scripts/startup/good.py", b"print('ready')")
        for name, magic in prep.IMPORTANT.items():
            self.write(root/name, magic + b"REAL_RESOURCE_CONTENT")
        return root

    def source_tree(self):
        root=self.root/"source"
        for name,magic in prep.SOURCE_IMPORTANT.items():
            self.write(root/name,magic+b"FULL_ASSET")
        return root

    def test_fail_closed_on_source_lfs_pointer_even_if_small(self):
        root=self.source_tree()
        self.write(root/"assets"/"brushes"/"bad.blend",
                   b"version https://git-lfs.github.com/spec/v1\n"
                   b"oid sha256:" + b"a"*64 + b"\nsize 12345\n")
        with self.assertRaisesRegex(ValueError, "unresolved source Git LFS"):
            prep.source_scan(root)

    def test_source_assets_validate_real_magic(self):
        root=self.source_tree()
        self.assertEqual(prep.source_scan(root)["unresolved_lfs_pointers"],0)
        self.write(root/"release/datafiles/studiolights/world/forest.exr",
                   b"not an exr")
        with self.assertRaisesRegex(ValueError, "Incorrect Blender resource signature"):
            prep.source_scan(root)

    def test_no_blender_pdb_in_runtime_but_kept_in_private_symbols(self):
        root=self.install_tree()
        with mock.patch.object(prep, "MIN_INSTALLED_FILES", 1):
            result=prep.package(root,self.root/"out")
        self.assertTrue(result["symbols_kept_separate"])
        self.assertEqual(result["pdb_in_runtime"],0)
        dist=self.root/"out"/result["runtime"]["file"]
        symbols=self.root/"out"/result["symbols"]["file"]
        with zipfile.ZipFile(dist) as z:
            self.assertIn("blender.exe",z.namelist())
            self.assertNotIn("blender.pdb",z.namelist())
            self.assertEqual(z.read("4.5/datafiles/assets/brushes/essentials_brushes-mesh_sculpt.blend")[:7],b"BLENDER")
        with zipfile.ZipFile(symbols) as z:
            self.assertIn("blender.pdb",z.namelist())
            self.assertNotIn("blender.exe",z.namelist())
        manifest=json.loads((self.root/"out"/"DISTRIBUTION-MANIFEST.json").read_text())
        self.assertEqual(manifest["runtime"]["sha256"],prep.checksum(dist))
        self.assertEqual(manifest["symbols"]["sha256"],prep.checksum(symbols))

    def test_no_unresolved_pointer_anywhere_in_installed_tree(self):
        root=self.install_tree()
        self.write(root/"4.5/datafiles/other.exr",
                   b"version https://git-lfs.github.com/spec/v1\n"
                   b"oid sha256:" + b"a"*64 + b"\nsize 552641\n")
        with self.assertRaisesRegex(ValueError,"Git LFS pointers found INSIDE"):
            prep.inspect_installed(root)

    def test_compiler_arch_must_be_true_arm64(self):
        root=self.install_tree()
        b=bytearray(self.fake_arm64_pe())
        struct.pack_into("<H",b,132,0x8664)
        self.write(root/"blender.exe",bytes(b))
        with self.assertRaisesRegex(ValueError,"Expected Windows ARM64"):
            prep.inspect_installed(root)

    def test_broken_critical_resource_blocks_even_if_rest_exist(self):
        root=self.install_tree()
        name=list(prep.IMPORTANT)[0]
        self.write(root/name,b"invalid placeholder")
        with self.assertRaisesRegex(ValueError,"Incorrect Blender resource signature"):
            prep.inspect_installed(root)

    def test_large_unexpected_runtime_rejected(self):
        root=self.install_tree()
        with mock.patch.object(prep,"MIN_INSTALLED_FILES",1):
            with mock.patch.object(prep,"MAX_RUNTIME_EXPANDED",2048):
                with self.assertRaisesRegex(ValueError,"Runtime expanded size"):
                    prep.inspect_installed(root)

if __name__=="__main__":
    unittest.main(verbosity=2)

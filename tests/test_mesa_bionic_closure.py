"""Pure regression checks. No APK, downloads, signing or GPU claimed."""
import hashlib
import io
import os
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import mesa_bionic_closure as closure


class ClosureTests(unittest.TestCase):
    def test_recursive_binary_deps(self):
        dependency = {
            "libEGL.so.1": ["libgallium-26.3.0-devel.so", "libxcb-xfixes.so", "libc.so"],
            "libgallium-26.3.0-devel.so": ["libxcb-glx.so", "libm.so"],
            "libxcb-xfixes.so": ["libxcb.so"],
            "libxcb-glx.so": ["libxcb.so"],
            "libxcb.so": ["libc.so"],
        }
        visited = []
        packages = closure.resolve_needed(
            {"libEGL.so.1": b"seed"},
            lambda name: (visited.append(name) or name.encode()),
            lambda name, data: dependency[name],
        )
        self.assertEqual(set(packages), set(dependency))
        self.assertEqual(set(visited), set(dependency) - {"libEGL.so.1"})

    def test_missing_is_not_skipped(self):
        def missing(name):
            raise FileNotFoundError("Rootfs missing " + name)
        with self.assertRaisesRegex(FileNotFoundError, "libxcb-xfixes"):
            closure.resolve_needed({"libEGL.so.1": b"seed"}, missing,
                                   lambda name, data: ["libxcb-xfixes.so"])

    def test_path_injection_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Unsafe ELF"):
            closure.resolve_needed({"libEGL.so.1": b"a"},
                                   lambda name: b"x",
                                   lambda name, data: ["../../libbad.so"])
        with self.assertRaises(ValueError):
            closure.check_name("libGL.so;rm -rf")
        with self.assertRaises(ValueError):
            closure.check_name("/usr/lib/libGL.so.1")

    def test_pinned_imagefs_alias_and_cycle(self):
        elf = bytearray(64)
        elf[:5] = b"\x7fELF\x02"
        elf[18:20] = (183).to_bytes(2, "little")
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "imagefs.txz"
            with tarfile.open(path, "w:xz") as t:
                payload = bytes(elf)
                item = tarfile.TarInfo("./usr/lib/libxcb-glx.so.0")
                item.size = len(payload)
                t.addfile(item, io.BytesIO(payload))
                link = tarfile.TarInfo("./usr/lib/libxcb-glx.so")
                link.type = tarfile.SYMTYPE
                link.linkname = "libxcb-glx.so.0"
                t.addfile(link)
                cyc1 = tarfile.TarInfo("./usr/lib/libcycle.so")
                cyc1.type = tarfile.SYMTYPE
                cyc1.linkname = "libcycle.so.1"
                t.addfile(cyc1)
                cyc2 = tarfile.TarInfo("./usr/lib/libcycle.so.1")
                cyc2.type = tarfile.SYMTYPE
                cyc2.linkname = "libcycle.so"
                t.addfile(cyc2)
            with patch.object(closure, "BIONIC_SHA256", hashlib.sha256(path.read_bytes()).hexdigest()):
                with closure.verified_imagefs(path) as fetch:
                    self.assertEqual(fetch("libxcb-glx.so"), bytes(elf))
                    with self.assertRaisesRegex(ValueError, "Cyclic"):
                        fetch("libcycle.so")
                    with self.assertRaises(FileNotFoundError):
                        fetch("libxcb-xfixes.so")

    def test_broken_pin_fails_before_extraction(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "fake.txz"
            p.write_bytes(b"not a pinned rootfs")
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                with closure.verified_imagefs(p):
                    pass


if __name__ == "__main__":
    unittest.main()

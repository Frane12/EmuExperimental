#!/usr/bin/env python3
"""Offline WCP importer tests. Synthetic payloads; no third-party binaries."""
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from wcp_import import WCPError, import_wcp

class WCPTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def make_xz(self, name, entries, links=()):
        dest = self.root / name
        with tarfile.open(dest, "w:xz") as tar:
            for path, data in entries.items():
                blob = data.encode() if isinstance(data,str) else data
                info=tarfile.TarInfo(path)
                info.size=len(blob)
                tar.addfile(info, io.BytesIO(blob))
            for path,target in links:
                info=tarfile.TarInfo(path)
                info.type=tarfile.SYMTYPE
                info.linkname=target
                tar.addfile(info)
        return dest

    def proton(self, section="proton"):
        return {"type":"Proton","versionName":"10-arm64ec","versionCode":0,
                "files":[],section:{"binPath":"bin","libPath":"lib","prefixPack":"prefixPack.txz"}}

    def test_real_proton_manifest_shape_and_install(self):
        p=self.proton()
        path=self.make_xz("p.wcp",{"bin/wine":b"ELF",
            "lib/wine/fake.so":b"123","prefixPack.txz":b"data",
            "profile.json":json.dumps(p)})
        meta=import_wcp(path,self.root/"store",dry_run=True)
        self.assertEqual(meta["type"],"Proton")
        self.assertEqual(meta["compression"],"xz")
        self.assertFalse((self.root/"store").exists())
        installed=import_wcp(path,self.root/"store")
        target=Path(installed["installedAt"])
        self.assertTrue((target/"bin/wine").is_file())
        self.assertTrue((target/"drnas-component.json").is_file())
        with self.assertRaises(WCPError):import_wcp(path,self.root/"store")

    def test_proton_wine_alias(self):
        p=self.proton("wine")
        path=self.make_xz("p.wcp",{"bin/wine":"x","lib/wine/x.so":"x",
            "prefixPack.txz":"x","profile.json":json.dumps(p)})
        self.assertEqual(import_wcp(path,self.root,dry_run=True)["type"],"Proton")

    def test_dxvk_map(self):
        p={"type":"DXVK","versionName":"1.10.3","versionCode":1,
           "files":[{"source":"system32/d3d11.dll","target":"${system32}/d3d11.dll"},
                    {"source":"syswow64/d3d9.dll","target":"${syswow64}/d3d9.dll"}]}
        path=self.make_xz("dx.wcp",{"profile.json":json.dumps(p),
            "system32/d3d11.dll":"64","syswow64/d3d9.dll":"32"})
        meta=import_wcp(path,self.root/"store")
        self.assertEqual(meta["type"],"DXVK")
        self.assertTrue((Path(meta["installedAt"])/"syswow64/d3d9.dll").exists())

    def test_reject_traversal(self):
        p=self.proton()
        path=self.make_xz("bad.wcp",{"../escapefile":b"evil","profile.json":json.dumps(p)})
        with self.assertRaises(WCPError):import_wcp(path,self.root/"store")
        self.assertFalse((self.root/"escapefile").exists())

    def test_reject_symlink(self):
        path=self.make_xz("bad.wcp",{"profile.json":"{}"},links=[("escape","../../evil")])
        with self.assertRaises(WCPError):import_wcp(path,self.root/"store",dry_run=True)

    def test_reject_untrusted_target(self):
        p={"type":"DXVK","versionName":"1.10.3","files":[
           {"source":"system32/d3d11.dll","target":"${libdir}/d3d11.dll"}]}
        path=self.make_xz("bad.wcp",{"profile.json":json.dumps(p),
            "system32/d3d11.dll":"x"})
        with self.assertRaises(WCPError):import_wcp(path,self.root,dry_run=True)


    def fexcore(self, entries=None):
        if entries is None:
            entries = [
                {"source":"system32/libarm64ecfex.dll",
                 "target":"${system32}/libarm64ecfex.dll"},
                {"source":"system32/libwow64fex.dll",
                 "target":"${system32}/libwow64fex.dll"}
            ]
        return {"type":"FEXCore", "versionName":"2609-GameNative-O3",
                "versionCode":260900081, "files":entries}

    def test_fexcore_gamenative_xz_real_manifest_shape(self):
        p=self.fexcore()
        path=self.make_xz("fex.wcp", {
            "./profile.json":json.dumps(p),
            "./system32/libarm64ecfex.dll":b"MZ-stub-ARM64EC",
            "./system32/libwow64fex.dll":b"MZ-stub-WOW64"
        })
        meta=import_wcp(path,self.root/"store",dry_run=True)
        self.assertEqual(meta["compression"],"xz")
        self.assertEqual(meta["type"],"FEXCore")
        self.assertEqual(meta["version"],"2609-GameNative-O3")
        self.assertEqual(meta["manifest"]["versionCode"],260900081)
        installed=import_wcp(path,self.root/"store")
        root=Path(installed["installedAt"])
        self.assertEqual((root/"system32/libarm64ecfex.dll").read_bytes(), b"MZ-stub-ARM64EC")
        self.assertTrue((root/"system32/libwow64fex.dll").is_file())
        self.assertEqual(json.loads((root/"drnas-component.json").read_text())["status"],
                         "imported_unvalidated")

    def test_fexcore_missing_bridge_pair_rejected(self):
        p=self.fexcore([{"source":"system32/libwow64fex.dll",
                         "target":"${system32}/libwow64fex.dll"}])
        path=self.make_xz("invalid.wcp", {
            "profile.json":json.dumps(p),"system32/libwow64fex.dll":"stub"})
        with self.assertRaisesRegex(WCPError, "complete pair"):
            import_wcp(path,self.root/"store",dry_run=True)

    def test_fexcore_arbitrary_dll_overwrite_rejected(self):
        p=self.fexcore([
            {"source":"system32/libarm64ecfex.dll", "target":"${system32}/kernel32.dll"},
            {"source":"system32/libwow64fex.dll", "target":"${system32}/libwow64fex.dll"}])
        path=self.make_xz("invalid.wcp", {
            "profile.json":json.dumps(p),
            "system32/libarm64ecfex.dll":"x",
            "system32/libwow64fex.dll":"x"
        })
        with self.assertRaisesRegex(WCPError, "untrusted"):
            import_wcp(path,self.root/"store",dry_run=True)

    def test_fexcore_unix_bridge_pair_supported(self):
        p=self.fexcore([
            {"source":"lib/wine/aarch64-unix/libarm64ecfex.so",
             "target":"${libdir}/wine/aarch64-unix/libarm64ecfex.so"},
            {"source":"lib/wine/aarch64-unix/libwow64fex.so",
             "target":"${libdir}/wine/aarch64-unix/libwow64fex.so"}])
        path=self.make_xz("unix.wcp", {
            "profile.json":json.dumps(p),
            "lib/wine/aarch64-unix/libarm64ecfex.so":"ELF",
            "lib/wine/aarch64-unix/libwow64fex.so":"ELF"})
        self.assertEqual(import_wcp(path,self.root/"store",dry_run=True)["type"],"FEXCore")

    def test_zstd(self):
        if not shutil.which("zstd"):self.skipTest("zstd not installed")
        path=self.root/"raw.tar"
        p=self.proton()
        with tarfile.open(path,"w") as tar:
            for name,blob in {"bin/wine":b"x","lib/wine/x.so":b"x",
                         "prefixPack.txz":b"x","profile.json":json.dumps(p).encode()}.items():
                info=tarfile.TarInfo(name);info.size=len(blob)
                tar.addfile(info,io.BytesIO(blob))
        out=self.root/"zstd.wcp"
        with open(out,"wb") as o:
            subprocess.run(["zstd","-cq",str(path)],stdout=o,check=True)
        meta=import_wcp(out,self.root/"store",dry_run=True)
        self.assertEqual(meta["compression"],"zstd")

if __name__=="__main__":
    unittest.main(verbosity=2)

"""Offline regression tests for ARM64/Bionic Mesa GUI dependency closure."""
import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest
import zipfile

TOOLS = pathlib.Path(__file__).resolve().parents[1] / "tools"
sys.path.insert(0, str(TOOLS))
import package_opengl_runtime as opengl


def gui_archive(path, names):
    with zipfile.ZipFile(path, "w") as archive:
        for soname in names:
            archive.writestr("lib/" + soname, b"\\x7fELF" + b"0" * 4096)


class NativeLibraryClosure(unittest.TestCase):
    def test_gui_runtime_creates_unversioned_alias(self):
        self.assertIn("libX11.so",opengl.gui_loader_names(
            {"libX11.so.6", "libxcb-glx.so", "libXext.so.6"}))
        self.assertIn("libXext.so",opengl.gui_loader_names({"libXext.so.6"}))

    def test_vulkan_blender_closure_includes_runtime_aliases(self):
        with tempfile.TemporaryDirectory() as t:
            archive=pathlib.Path(t)/"gui.zip"
            gui_archive(archive,["libX11.so.6","libxcb-glx.so","libxcb.so",
                                 "libXext.so.6"])
            mesa={"libGL.so.1":{"needed":["libX11.so","libxcb-glx.so",
                                              "libXext.so","libc.so"]},
                  "libEGL.so.1":{"needed":["libX11.so","libc.so"]}}
            opengl.verify_blender_glx_closure(archive,mesa)

    def test_multiple_missing_edges_are_reported_in_one_build(self):
        with tempfile.TemporaryDirectory() as t:
            archive=pathlib.Path(t)/"gui.zip"
            gui_archive(archive,["libX11.so.6","libxcb-glx.so"])
            mesa={"libGL.so.1":{"needed":["libxcb-glx.so","libffi.so"]},
                  "libwayland-client.so":{"needed":["libandroid-support.so"]}}
            with self.assertRaisesRegex(RuntimeError, "libffi.so") as error:
                opengl.verify_blender_glx_closure(archive,mesa)
            self.assertIn("libandroid-support.so",str(error.exception))

    def test_dynamic_mesa_roots_exclude_packaged_and_platform_libs(self):
        mesa={"libGL.so.1":{"needed":["libGL.so.1","libX11.so","libffi.so",
                                             "libc.so"]},
              "libwayland-client.so":{"needed":["libandroid-support.so"]}}
        self.assertEqual(opengl.mesa_required_sonames(mesa),
                         ["libX11.so","libandroid-support.so","libffi.so"])

    def test_conflicting_abi_aliases_fail(self):
        with self.assertRaisesRegex(RuntimeError,"Conflicting native GUI linker alias"):
            opengl.gui_loader_names(["libX11.so.6","libX11.so.7"])

    def test_zip_manifest_verified_without_network(self):
        with tempfile.TemporaryDirectory() as t:
            root=pathlib.Path(t)
            gui_archive(root/"gui.zip",["libX11.so.6","libxcb-glx.so"])
            manifest={"libGL.so.1":{"needed":["libX11.so","libxcb-glx.so"]}}
            with zipfile.ZipFile(root/"mesa.zip","w") as z:
                z.writestr("lib/libGL.so.1",b"\\x7fELF")
                z.writestr("manifest.json",json.dumps({"files":manifest}))
            opengl.verify_archives(root/"gui.zip",root/"mesa.zip")


if __name__=="__main__":
    unittest.main()

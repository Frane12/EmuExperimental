#!/usr/bin/env python3
"""Read-only pre-build review: verify r64 patch against a Blender 4.5.14 checkout.

NO build, NO changes to source, and NO change to the DRNAS APK.
Usage: python3 tools/check_blender_mali_patch.py /path/to/blender-v4.5.14
"""
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
PATCH = ROOT / "patches/blender-4.5.14-mali-vulkan-optional-features-r64.patch"
FILES = (
    "intern/ghost/intern/GHOST_ContextVK.cc",
    "source/blender/gpu/vulkan/vk_backend.cc",
    "source/blender/gpu/vulkan/render_graph/vk_command_buffer_wrapper.cc",
    "source/blender/gpu/vulkan/vk_pipeline_pool.cc",
)

def run(*cmd):
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout.strip()

def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 tools/check_blender_mali_patch.py /path/to/blender-v4.5.14")
    checkout = pathlib.Path(sys.argv[1]).resolve()
    if not PATCH.is_file() or not all((checkout / name).is_file() for name in FILES):
        raise SystemExit("Missing patch or source files: use the clean upstream Blender 4.5.14 source checkout")
    # Never accept a patch that silently applies to a different source revision.
    tag_sha = run("git", "-C", str(checkout), "rev-parse", "refs/tags/v4.5.14^{commit}")
    head_sha = run("git", "-C", str(checkout), "rev-parse", "HEAD")
    if head_sha != tag_sha:
        raise SystemExit("Refusing review: checkout HEAD is not exactly upstream v4.5.14 tag")
    dirty = run("git", "-C", str(checkout), "status", "--porcelain")
    if dirty:
        raise SystemExit("Refusing review: source checkout has uncommitted changes")
    run("git", "-C", str(checkout), "apply", "--check", str(PATCH))
    print("PASS: r64 Mali Vulkan source patch applies cleanly to Blender v4.5.14.")
    print("No files modified. No Blender or APK compilation performed.")

if __name__ == "__main__":
    main()

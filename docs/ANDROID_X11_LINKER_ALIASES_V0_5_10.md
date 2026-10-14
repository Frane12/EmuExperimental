# v0.5.10 — unversioned Bionic linker names

The 19:41 device trace confirms every versioned probe succeeds and
Xlib XOpenDisplay=CONNECTED. Both the isolated probe and Wine's own loader
report the exact same blocker: winex11.so needs libXext.so, which is absent.
The APK contains libXext.so.6. The trace also still reports missing FreeType.

After verifying the 22 bundled library hashes, install relative unversioned
aliases inside the APK-owned private GUI runtime: e.g. libXext.so points to
libXext.so.6, libfreetype.so to libfreetype.so.6, and libfontconfig.so to
libfontconfig.so.1. Existing correct links are reused, broken links repaired,
ambiguous targets/path traversal rejected. Android's unversioned Vulkan loader
is preserved; the explicitly versioned bundled loader remains available.

The native probe now checks the exact unversioned names as well as SONAME
versions, and a missing-library failure while opening winex11 yields nonzero
probe status. Wine-specific unresolved symbols in a standalone process remain
separate from a missing-library error.

Tests exercise readable relative aliases, idempotence, broken-link repair and
path rejection. Device acceptance still requires a real Wine window mapping
and Canvas draw; successful XOpenDisplay alone does not prove a working GUI.

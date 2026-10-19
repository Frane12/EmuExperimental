# Blender PrefixFix LAB r62 — PRIVATE review candidate

**Based on r61. No public Testturnip release.**

## Work
- Prefix initialization now performs a two-pass SHA-256 verification of all regular extracted files before commit.
- Checksum-backed schema-2 prefix marker; invalid/legacy markers cannot bypass recovery. Existing data is moved to same-filesystem backups before committing the replacement, never deleted.
- 18-minute cooperative deadline for each extraction attempt, checks at TAR entries and each read chunk. A bounded second attempt occurs after an IOException; no unbounded retry loop.
- Bottom-right 18dp EMUDRNAS branding on Blender setup/boot overlays; fades with normal GUI startup.
- **← Blender** action sends a bounded atomic request; Blender closes only a secondary IMAGE_EDITOR window showing Render Result. It never sends blind Alt+F4 to Wine. F11 remains for toggling Render Result in the main window.
- Separate signed Android package `com.drnas.console.prefixfixlab`, versionCode 130; public build and its files unchanged.

## Device validation
1. Install alongside public Blender; confirm previous projects/settings and D: permissions unchanged.
2. Verify first-use checksum log `PREFIX_INTEGRITY: SHA-256 verified` and wineboot/GUI.
3. Interrupt a first-use installation and verify safe recovery and preserved backups.
4. Test Render View (F11), **← Blender**, Preferences, D: open/save, fullscreen and Vulkan.
5. Verify EMUDRNAS is discreet on startup and absent over the viewport.

## Boundaries
Cooperative deadline is not a process-level kill; reads blocked inside a native decoder cannot be forcibly interrupted by this logic. The recorded manifest attests to files checked during installation, not to all future user modifications. Hardware behaviour and Render button effectiveness require on-device testing.

# Verified WCP samples — 2026-10-14

**Source files supplied directly by the user.** Inspected, **not executed**, in private GitHub Actions run [38485965447](https://github.com/Frane12/Turnip-Neuro-Private/actions/runs/38485965447). Both downloads matched published GitHub release asset sizes; SHA-256 computed on downloaded bytes.

## Proton 10 ARM64EC controller-fix

- Source: https://github.com/K11MCH1/Winlator101/releases/download/wine_col/proton-10-arm64ec-controller-fix.wcp
- 233435748 bytes
- SHA-256: `b0a0f302b479bffb6a1a24cdc55cbc41e5e610bdfd94702ca3615b5f59c9f64e`
- Magic: Zstandard (`28 b5 2f fd`), tar.zst payload, not XZ
- Tar: 1762 members, 1751 regular files, 11 directories, no symlinks or absolute/parent-traversal paths in the inspection
- Manifest at tar root:
```json
{
  "type": "Proton",
  "versionName": "10-arm64ec",
  "versionCode": 0,
  "description": "Proton 10 arm64ec for newer cmod versions",
  "files": [],
  "proton": {
    "binPath": "bin",
    "libPath": "lib",
    "prefixPack": "prefixPack.txz"
  }
}
```
- Contains `bin/wine`, `bin/wineserver`, `lib/wine/aarch64-unix/`, `lib/wine/aarch64-windows/`, `lib/wine/i386-windows/`, `prefixPack.txz`.
- **Compatibility insight:** The manifest nests paths under `proton`, not `wine`. Some downstream Winlator/Ludashi importers read `wine` unconditionally for Proton. Do not assume that this WCP can be installed by any such importer, even if decompression succeeds. Our adapter supports either documented key and validates referenced archive paths.
- Import of the WCP does not prove that Proton 10 ARM64EC can actually run on Android. It still needs matched FEXCore integration, graphics + audio bridge, a suitable Bionic/glibc environment, etc.

## DXVK 1.10.3

- Source: https://github.com/K11MCH1/Winlator101/releases/download/dxvk_col/dxvk_1.10.3.wcp
- 3216192 bytes
- SHA-256: `7ae0a7108f08fa12a54be891feecaac9de7d8eb3cd543704426126bb16a3e87a`
- Magic: XZ (`fd 37 7a 58 5a 00`), tar.xz payload
- Tar: 18 members, 15 regular files, 3 directories, no symlinks or absolute/parent-traversal paths in the inspection
- Manifest:
  - `type: DXVK`; `versionName: 1.10.3`; `versionCode: 1`
  - `description: DXVK-1.10.3 with d3d8to9`
  - `files` maps `system32/d3d9.dll`, `d3d10.dll`, `d3d10_1.dll`, `d3d10core.dll`, `d3d11.dll`, `dxgi.dll` into `${system32}`; similarly the `${syswow64}` DLLs (including `d3d8.dll`).
- Importer must NEVER trust arbitrary `target` destinations: validate `system32`/`syswow64` macros against an allowlist and apply only to the selected, isolated Wine prefix. Preserve backups or use a staging/layer mechanism, not destructive global copy.
- PE target ABI inspection is separate from archive-format verification; do not assume that ordinary x86/x86-64 DXVK DLLs execute natively on ARM64.

## Our Drnas Console component lifecycle

1. Accept user-selected local files; detect format **by magic**, not `.wcp` extension.
2. Hash download and verify checksum against pinned manifest.
3. Stream inspect without execution; reject path traversal, links and special files; cap decompressed size/member count.
4. Validate `profile.json` and referenced payload files. Normalise `Wine` and `Proton` metadata key names separately.
5. Copy files into an immutable **version+hash content store** in private app storage, not directly into the active Wine prefix.
6. Make an explicit `SessionPlan`, resolve components and ABI requirements.
7. When launching, prepare a temporary per-session DXVK layer and Wine paths, with rollback on failure.
8. Release note: **No actual Android execution, ABI-level compatibility, or game start has yet been demonstrated.**

Do not commit third-party WCP binary blobs or proprietary game files.

## Confirmed importer integration test

Private CI run [38486461239](https://github.com/Frane12/Turnip-Neuro-Private/actions/runs/38486461239) **SUCCESS** (2026-10-14): the `tools/wcp_import.py` reference importer read the **actual** 233 MB ZSTD Proton WCP and the **actual** 3 MB XZ DXVK WCP in dry-run mode, validated their real manifests, hashes, and member paths; then installed the DXVK payload into an isolated temporary component store. Validated presence of `system32/d3d11.dll`, `syswow64/d3d9.dll`, and the `drnas-component.json` record. **No game launched and no live Wine prefix changed.** The standalone Python importer is a specification/prototype for a future native Android port, not an already-integrated Android runtime.

## FEXCore 2609 GameNative O3

A third package was supplied by file upload (not from GitHub). It is an XZ-compressed TAR containing exactly two FEX-related Wine PE DLLs and `profile.json`: `libarm64ecfex.dll` and `libwow64fex.dll`. The local actual-file staging check passed; import support and synthetic CI coverage are now in the same branch. See [FEXCORE_2609_GAMENATIVE.md](FEXCORE_2609_GAMENATIVE.md) for manifest, hashes, architecture caveats and activation plan. The binary is *not committed* to this repository.

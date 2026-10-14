# FEXCore 2609 GameNative O3 — inspected local WCP

The user provided `FEXCore-2609-GameNative-O3.wcp` directly in the conversation.
The third-party binary was **analyzed as data only**, never executed, and **not uploaded to this repository**.

## Verified package facts

| Property | Value |
|---|---|
| Archive | `FEXCore-2609-GameNative-O3.wcp` |
| Bytes | `993676` |
| SHA-256 | `e0bafa70aed144620eb8cc9fb81f7e75cdfa7f1679379b4cc2986653a113f0cf` |
| Compression | XZ (CRC64), containing a TAR |
| TAR contents | 2 DLLs, `profile.json`, two directory entries |
| Expanded data bytes | `7631266` |
| `type` | `FEXCore` |
| `versionName` | `2609-GameNative-O3` |
| `versionCode` | `260900081` |
| Declared author | `Ragnarok93 / GameNative` |

Manifest exactly:

```json
{
  "type": "FEXCore",
  "versionName": "2609-GameNative-O3",
  "versionCode": 260900081,
  "description": "FEXCore 2609 GameNative O3 control build",
  "author": "Ragnarok93 / GameNative",
  "files": [
    { "source": "system32/libarm64ecfex.dll", "target": "${system32}/libarm64ecfex.dll" },
    { "source": "system32/libwow64fex.dll", "target": "${system32}/libwow64fex.dll" }
  ]
}
```

| Module | Bytes | SHA-256 | PE header machine |
|---|---:|---|---|
| `system32/libarm64ecfex.dll` | 3952640 | `126ea744d665a0792aa733600f5310b4c13beec6aa3624f311ead9d75b654148` | `0x8664` |
| `system32/libwow64fex.dll` | 3678208 | `2e4707f9c2ec4db4a2a4a8dd40e0bfdd931b731f2d9756874a478c54ef26b939` | `0xAA64` |

The PE machine fields alone are **not proof** of Wine ARM64EC module/runtime compatibility. Inspecting readable strings shows embedded FEXCore/JIT references in both DLLs, but their capabilities and `-O3` performance are **not benchmarked or validated**.

## What this file IS — and IS NOT

This package contains two **Windows PE Wine bridge DLLs**, intended for different Wine guest execution paths.

- It does **not** include a standalone Android/Linux `FEX` executable.
- It does **not** contain a separate shared ELF module under `lib/wine/aarch64-unix`.
- It is not a complete, independent Windows runtime.
- It must be coupled to a compatible ARM64EC Wine/Proton build. A successful WCP import alone proves neither compatibility nor game performance.

## Our import/activation separation

**Import (implemented in Python reference importer)**

1. Identify XZ by magic; stream-decode TAR.
2. Validate every entry, refuse path traversal/symlinks/duplicate paths.
3. Validate exact `FEXCore` target allowlist and both Wine bridge DLLs.
4. Hash and place file payloads in an isolated content-addressed store:
   `<store>/fexcore/2609-GameNative-O3-e0bafa70aed1/`
5. Emit `drnas-component.json` with `status: imported_unvalidated`.

**Session activation (NOT IMPLEMENTED YET)**

1. Probe selected Proton/Wine for FEX bridge ABI compatibility, loader support and required DLL overrides.
2. Prepare a per-game Wine prefix or temporary overlay. Never overwrite a system or shared Wine prefix without backup and rollback.
3. Map both DLLs to the Wine prefix `drive_c/windows/system32/` only after validation.
4. Ensure required Proton/Bionic/FEX support, environment and translator mode; inspect logs.
5. Launch a trivial executable before a game; reject incompatible module combinations.
6. The A840 Turnip/DXVK/audiobridge live runtime remains independent.

## Reproducible local reference

```bash
python3 tools/wcp_import.py /path/to/FEXCore-2609-GameNative-O3.wcp --dry-run
python3 tools/wcp_import.py /path/to/FEXCore-2609-GameNative-O3.wcp --root ./drnas-content-store
```

A local isolated **test extraction of the actual package** confirmed both DLLs, their hashes and manifest destinations; the staging directory was then deleted. Private GitHub CI validates matching synthetic XZ FEXCore fixtures and rejects untrusted target mappings. Since this conversation upload is not in GitHub, CI does not contain or distribute it.

**Current status:** WCP format + manifest understood; Python importer adapted and CI tested; Android loader and real session activation pending.

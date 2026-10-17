"""Validate both pinned DXVK WCP variants *before* producing any APK.
No APK is released with an async mode that lacks 32+64-bit Wine DLLs.
"""
import json
import tarfile
from pathlib import Path

root = Path("android-app/app/src/main/assets")
manifest=json.loads((root/"runtime-components-v1.json").read_text())
items={x.get("variant"):x for x in manifest["items"] if x["kind"]=="DXVK"}
assert set(items)=={"standard","async"},f"Unexpected DXVK variants: {set(items)}"
expected={"system32","syswow64"}
dlls={"d3d9.dll","dxgi.dll","d3d11.dll","d3d10core.dll"}
for kind,item in items.items():
    path=root/item["asset"]
    assert path.is_file()
    with tarfile.open(path,"r:xz") as archive:
        profile=json.load(archive.extractfile("profile.json"))
        assert profile["type"]=="DXVK",(kind,profile.get("type"))
        entries=profile["files"]
        seen=set()
        for entry in entries:
            target=entry["target"]
            for arch in expected:
                if target.startswith("${"+arch+"}/"):
                    dll=target.rsplit("/",1)[-1]
                    if dll not in dlls:continue
                    key=(arch,dll)
                    assert key not in seen, (kind,key)
                    seen.add(key)
                    source=entry["source"]
                    member=archive.extractfile(source)
                    assert member is not None, (kind,source)
                    hdr=member.read(256)
                    assert hdr[:2]==b"MZ", (kind,source)
                    pe=int.from_bytes(hdr[60:64],"little")
                    assert pe+6<=len(hdr), (kind,source)
                    assert hdr[pe:pe+4]==b"PE\\x00\\x00",(kind,source)
                    machine=int.from_bytes(hdr[pe+4:pe+6],"little")
                    assert (machine==0x14c if arch=="syswow64" else machine in (0x8664,0xa641,0xaa64)), (kind,key,hex(machine))
        assert seen=={(arch,dll) for arch in expected for dll in dlls},(kind,seen)
    print(f"DXVK {kind}: verified exact eight X86/X64 or ARM64EC PE DLLs, SHA-pinned archive: {path.name}")

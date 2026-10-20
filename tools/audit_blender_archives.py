#!/usr/bin/env python3
"""Read-only, byte-for-byte DRNAS Blender 4.5.14 official vs Mali R64 ZIP forensic audit.
No compilation, installation, publication or modification of either archive.
"""
import collections
import csv
import hashlib
import json
import pathlib
import struct
import sys
import zipfile

OFFICIAL_SHA = "0153ecefc96a0e23985e6edcdbe04e909ba2078258f4ff6a05c21d4b7c756911"
MALI_SHA = "8802767f9ac63a6e8054e6e37b3ba994b49c2fb6d37d34cfc50ae48491851d6f"
WRAPPER = "blender-4.5.14-windows-arm64/"

def filehash(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(2**20), b""):
            h.update(block)
    return h.hexdigest()

def inventory(path, kind):
    entries = {}
    with zipfile.ZipFile(path) as z:
        for item in z.infolist():
            if item.is_dir():
                continue
            name = item.filename.replace("\\", "/")
            if kind == "official":
                if not name.startswith(WRAPPER):
                    raise RuntimeError("Official ZIP unexpected root: " + name)
                name = name[len(WRAPPER):]
            if not name or name.startswith("/") or ".." in name.split("/"):
                raise RuntimeError("Unsafe ZIP entry: " + name)
            norm = name.casefold()
            if norm in entries:
                raise RuntimeError("Case-insensitive duplicate: " + name)
            entries[norm] = {"path": name, "archive_path": item.filename,
                             "size": item.file_size, "compressed": item.compress_size,
                             "crc32": "%08x" % item.CRC}
        # Real SHA256 of EACH decompressed file; CRC/size alone are insufficient.
        for k, v in entries.items():
            digest = hashlib.sha256()
            with z.open(v["archive_path"]) as f:
                for block in iter(lambda: f.read(2**20), b""):
                    digest.update(block)
            v["sha256"] = digest.hexdigest()
    return entries

def peinfo(z, filename):
    with z.open(filename) as f:
        head = f.read(128*1024)
    if len(head) < 256 or head[:2] != b"MZ":
        return None
    pe = int.from_bytes(head[60:64], "little")
    if pe < 64 or pe+24 >= len(head) or head[pe:pe+4] != b"PE\0\0":
        return None
    machine, sections = struct.unpack_from("<HH", head, pe+4)
    sizeopt, flags = struct.unpack_from("<HH", head, pe+20)
    opt = pe+24
    if opt+64 > len(head):
        return None
    image = struct.unpack_from("<I", head, opt+56)[0]
    magic = struct.unpack_from("<H", head, opt)[0]
    section_table = opt+sizeopt
    segments = []
    for i in range(min(sections, 60)):
        start = section_table + 40*i
        if start+40 > len(head):
            break
        name = head[start:start+8].split(b"\0")[0].decode("ascii", errors="replace")
        virtual, virtual_address, rawsize, rawptr = struct.unpack_from("<IIII", head, start+8)
        segments.append({"section": name, "virtual_size": virtual,
                         "virtual_address": virtual_address,
                         "raw_size": rawsize, "raw_ptr": rawptr})
    return {"machine": hex(machine), "size_of_image": image, "pe_magic": hex(magic),
            "coff_characteristics": hex(flags), "debug_stripped_flag": bool(flags & 0x200),
            "section_count": sections, "sections": segments}

def detect_lfs_pointers(archive, entries):
    found = []
    with zipfile.ZipFile(archive) as z:
        for entry in entries.values():
            if entry["size"] > 400:
                continue
            with z.open(entry["archive_path"]) as stream:
                content = stream.read(400)
            if not content.startswith(b"version https://git-lfs.github.com/spec/v1"):
                continue
            fields = content.decode("utf-8",errors="replace").splitlines()
            declared = None
            for line in fields:
                if line.startswith("size "):
                    try:
                        declared = int(line[5:])
                    except ValueError:
                        pass
            found.append({"path":entry["path"],"actual_bytes":entry["size"],
                          "intended_lfs_bytes":declared,
                          "reference_prefix":fields[0] if fields else ""})
    return sorted(found,key=lambda e:e["intended_lfs_bytes"] or 0,reverse=True)

def fmt(n):
    return f"{n / 1048576:,.2f} MiB"

def main(official_path, mali_path, dest):
    out = pathlib.Path(dest)
    out.mkdir(parents=True, exist_ok=True)
    official_path = pathlib.Path(official_path)
    mali_path = pathlib.Path(mali_path)
    hashes = {"official": filehash(official_path), "mali": filehash(mali_path)}
    if hashes != {"official": OFFICIAL_SHA, "mali": MALI_SHA}:
        raise RuntimeError("Refusing to compare: ZIP SHA256 mismatch " + str(hashes))
    official = inventory(official_path, "official")
    mali = inventory(mali_path, "mali")
    keys = sorted(set(official) | set(mali))
    rows = []
    folderdiff = collections.Counter()
    extdiff = collections.Counter()
    statuses = collections.Counter()
    for k in keys:
        a, b = official.get(k), mali.get(k)
        sa, sb = a["size"] if a else 0, b["size"] if b else 0
        folder = (b or a)["path"].split("/")[0]
        ext = pathlib.PurePosixPath((b or a)["path"]).suffix.lower() or "[no extension]"
        folderdiff[folder] += sb-sa
        extdiff[ext] += sb-sa
        status = "identical" if a and b and a["sha256"] == b["sha256"] else (
            "changed" if a and b else ("mali_only" if b else "official_only"))
        statuses[status] += 1
        rows.append({"path": (b or a)["path"], "status": status,
                     "official_size": sa, "mali_size": sb, "delta": sb-sa,
                     "official_compressed": a["compressed"] if a else 0,
                     "mali_compressed": b["compressed"] if b else 0,
                     "official_sha256": a["sha256"] if a else "",
                     "mali_sha256": b["sha256"] if b else ""})
    with (out / "all_files_diff.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)

    pe = {}
    for label, path, table in (("official", official_path, official),
                                ("mali", mali_path, mali)):
        with zipfile.ZipFile(path) as z:
            for p, x in sorted(table.items()):
                if (p.endswith(".exe") or p.endswith(".dll")):
                    info = peinfo(z, x["archive_path"])
                    if info:
                        pe.setdefault(p,{})[label] = info

    lfs = {"official":detect_lfs_pointers(official_path,official),
           "mali":detect_lfs_pointers(mali_path,mali)}
    totals = {label: {"archive_bytes":path.stat().st_size,
                      "expanded_bytes":sum(e["size"] for e in tbl.values()),
                      "file_count":len(tbl),
                      "compressed_sum_bytes":sum(e["compressed"] for e in tbl.values())}
              for label,path,tbl in (("official",official_path,official),
                                      ("mali",mali_path,mali))}
    sorted_delta=sorted(rows,key=lambda r:r["delta"],reverse=True)
    largest_custom=sorted((r for r in rows if r["status"]=="mali_only"),
                          key=lambda r:r["mali_size"],reverse=True)
    largest_changed=sorted((r for r in rows if r["status"]=="changed"),
                           key=lambda r:abs(r["delta"]),reverse=True)
    pe_blender = pe.get("blender.exe",{})
    result={"hashes":hashes,"totals":totals,"status_counts":dict(statuses),
            "folder_delta_bytes":dict(folderdiff.most_common()),
            "extension_delta_bytes":dict(extdiff.most_common()),
            "blender_exe_pe":pe_blender,"lfs_pointers":lfs,
            "largest_delta":sorted_delta[:45],
            "largest_mali_only":largest_custom[:35],
            "largest_changed":largest_changed[:40],
            "pe_metadata":pe}
    (out/"report.json").write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")

    def table(rs):
        return "\n".join("| `"+r["path"].replace("|","\\|")+"` | "+
                         r["status"]+" | "+fmt(r["official_size"])+" | "+
                         fmt(r["mali_size"])+" | "+fmt(r["delta"])+" |" for r in rs)
    head = [
        "# DRNAS Blender 4.5.14: official v1.1 vs custom Mali R64 — binary audit",
        "",
        "Exact original ZIPs hashed before comparison; official archive root normalized, "
        "no Blender, DLL, compiler, APK or Android runtime executed.",
        "",
        "| Metric | Official v1.1 | Mali R64 | Difference (Mali − official) |",
        "|---|---:|---:|---:|",
        f"| ZIP size | {fmt(totals['official']['archive_bytes'])} | {fmt(totals['mali']['archive_bytes'])} | {fmt(totals['mali']['archive_bytes']-totals['official']['archive_bytes'])} |",
        f"| Uncompressed files | {fmt(totals['official']['expanded_bytes'])} | {fmt(totals['mali']['expanded_bytes'])} | {fmt(totals['mali']['expanded_bytes']-totals['official']['expanded_bytes'])} |",
        f"| Files | {totals['official']['file_count']} | {totals['mali']['file_count']} | {totals['mali']['file_count']-totals['official']['file_count']} |",
        "", "## Byte-exact status (SHA256 of individual decompressed files)",
        json.dumps(statuses,indent=2), "",
        "## blender.exe",
        "| Property | Official | Mali R64 |", "|---|---:|---:|"
    ]
    if "blender.exe" in official and "blender.exe" in mali:
        for key in ("size","compressed","sha256"):
            left,right=official["blender.exe"][key],mali["blender.exe"][key]
            if key=="size" or key=="compressed":
                left,right=fmt(left),fmt(right)
            head.append(f"| {key} | `{left}` | `{right}` |")
    for key in ("machine","size_of_image","debug_stripped_flag","section_count"):
        a=pe_blender.get("official",{}).get(key,"missing")
        b=pe_blender.get("mali",{}).get(key,"missing")
        head.append(f"| PE {key} | {a} | {b} |")
    head.extend(["", "## Git LFS pointer files accidentally shipped instead of real assets",
                 "| | Official | Mali R64 |", "|---|---:|---:|",
                 f"| LFS pointer entries | {len(lfs['official'])} | {len(lfs['mali'])} |",
                 f"| Total original asset bytes referenced by pointers | {fmt(sum(x['intended_lfs_bytes'] or 0 for x in lfs['official']))} | {fmt(sum(x['intended_lfs_bytes'] or 0 for x in lfs['mali']))} |",
                 "", "### Largest unresolved Git LFS resource placeholders (Mali)",
                 "| Path | Actual bytes | Intended asset size |","|---|---:|---:|"])
    head.extend(f"| \`{x['path']}\` | {x['actual_bytes']} | {fmt(x['intended_lfs_bytes'] or 0)} |" for x in lfs['mali'][:28])
    head.extend(["", "## Difference by top-level folder",
                 "| Folder | Size difference |","|---|---:|"])
    head.extend("| `"+f+"` | "+fmt(delta)+" |" for f,delta in folderdiff.most_common(30))
    head.extend(["", "## Difference by file extension",
                 "| Extension | Size difference |","|---|---:|"])
    head.extend("| `"+f+"` | "+fmt(delta)+" |" for f,delta in extdiff.most_common(20))
    head.extend(["", "## Largest positive size changes",
                 "| File | Status | Official | Mali | Difference |",
                 "|---|---|---:|---:|---:|", table(sorted_delta[:35])])
    head.extend(["", "## Largest custom-only files",
                 "| File | Status | Official | Mali | Difference |",
                 "|---|---|---:|---:|---:|",table(largest_custom[:25])])
    head.extend(["", "## Largest changed files by absolute delta",
                 "| File | Status | Official | Mali | Difference |",
                 "|---|---|---:|---:|---:|",table(largest_changed[:20])])
    for label in ("official","mali"):
        sec=pe_blender.get(label,{}).get("sections",[])
        head.extend(["",f"## blender.exe PE sections ({label})",
                    "| Section | Raw size | Virtual size |","|---|---:|---:|"])
        head.extend(f"| `{x['section']}` | {fmt(x['raw_size'])} | {fmt(x['virtual_size'])} |" for x in sec)
    head.extend(["", "Complete file hashes/sizes in `all_files_diff.csv`; machine-readable PE details in `report.json`.",
                 "SHA256 official: `"+OFFICIAL_SHA+"`",
                 "SHA256 custom: `"+MALI_SHA+"`"])
    text = "\n".join(head)+"\n"
    (out/"summary.md").write_text(text,encoding="utf-8")
    print(text[:24000],flush=True)
    print("DRNAS_R64_ARCHIVE_DIFF_OK files="+str(len(rows))+
          " official="+str(totals["official"]["expanded_bytes"])+
          " mali="+str(totals["mali"]["expanded_bytes"]),flush=True)

if __name__=="__main__":
    if len(sys.argv)!=4:
        sys.exit("usage: audit_blender_archives.py official.zip mali.zip output_dir")
    main(sys.argv[1],sys.argv[2],sys.argv[3])

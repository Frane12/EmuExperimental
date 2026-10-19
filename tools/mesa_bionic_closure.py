"""Fail-closed closure for Mesa ELF DT_NEEDED libraries from pinned Bionic rootfs.

Only standalone ELF files or in-tree symlinks under usr/lib are accepted.
No arbitrary library from the host running GitHub Actions can satisfy a
dependency: all Android libraries must be included or platform-allowlisted.
"""
import hashlib
import re
import shutil
import tarfile
import tempfile
from contextlib import contextmanager
from pathlib import Path

# Same immutable GameNative imagefs pin as tools/package_gui_runtime.py.
BIONIC_SHA256 = "368db62bfc58b72c97e5169bda9aa64d4246f07964c447e27c79a065e7e9c48b"
ANDROID_SYSTEM = {
    "libc.so", "libdl.so", "libm.so", "liblog.so",
    "libandroid.so", "libnativewindow.so",
}
VALID_LIBRARY = re.compile(r"lib[A-Za-z0-9_.+-]+\\.so(?:\\.[0-9]+)*\\Z")
MAX_INPUT_LIB = 64 * 1024 * 1024
MAX_BUNDLE_SIZE = 112 * 1024 * 1024
MAX_BUNDLE_LIBS = 72


def digest_file(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def check_name(name):
    if not VALID_LIBRARY.fullmatch(name):
        raise ValueError("Unsafe ELF SONAME/dependency: " + repr(name))
    return name


def resolve_needed(seed, fetch, needs, system=ANDROID_SYSTEM):
    """Return full transitive dependency map; fetch(name)->bytes or raises.

    Validate ALL names, including already-packaged seed entries. A missing
    library fails the preflight instead of reaching Android as a silent crash.
    """
    packages = dict(seed)
    pending = list(packages)
    checked = set()
    while pending:
        name = check_name(pending.pop())
        if name in checked:
            continue
        checked.add(name)
        for dep in needs(name, packages[name]):
            check_name(dep)
            if dep in system:
                continue
            if dep not in packages:
                packages[dep] = fetch(dep)
                if len(packages) > MAX_BUNDLE_LIBS:
                    raise ValueError("Mesa dependency closure has too many libraries")
            pending.append(dep)
    if sum(map(len, packages.values())) > MAX_BUNDLE_SIZE:
        raise ValueError("Mesa dependency closure exceeds the size limit")
    return packages


@contextmanager
def verified_imagefs(path):
    path = Path(path)
    if not path.is_file() or digest_file(path) != BIONIC_SHA256:
        raise ValueError("Pinned GameNative Bionic rootfs SHA-256 mismatch")

    with tempfile.TemporaryDirectory(prefix="drnas-r66-rootfs-") as temp:
        root = Path(temp)
        entries = {}
        with tarfile.open(path, mode="r|xz") as archive:
            for entry in archive:
                raw = entry.name.removeprefix("./")
                candidate = Path(raw)
                if candidate.parent != Path("usr/lib") or not VALID_LIBRARY.fullmatch(candidate.name):
                    continue
                name = candidate.name
                if name in entries:
                    raise ValueError("Duplicate rootfs member: " + name)
                if entry.isfile():
                    if not (0 < entry.size <= MAX_INPUT_LIB):
                        raise ValueError("Rootfs ELF too large: " + name)
                    target = root / name
                    with archive.extractfile(entry) as reader, target.open("wb") as writer:
                        shutil.copyfileobj(reader, writer, 65536)
                    entries[name] = ("file", target)
                elif entry.issym():
                    target = entry.linkname
                    if not VALID_LIBRARY.fullmatch(target):
                        raise ValueError("Unsafe Bionic library symlink: " + name)
                    entries[name] = ("link", target)
                elif entry.islnk():
                    target = Path(entry.linkname.removeprefix("./"))
                    if target.parent != Path("usr/lib") or not VALID_LIBRARY.fullmatch(target.name):
                        raise ValueError("Unsafe Bionic library hardlink: " + name)
                    entries[name] = ("link", target.name)

        def fetch(name):
            check_name(name)
            visited = set()
            while True:
                if name in visited:
                    raise ValueError("Cyclic Bionic library alias: " + name)
                visited.add(name)
                if name not in entries:
                    raise FileNotFoundError("Rootfs missing Mesa DT_NEEDED " + name)
                kind, value = entries[name]
                if kind == "file":
                    data = value.read_bytes()
                    if len(data) < 20 or data[:5] != b"\\x7fELF\\x02" or int.from_bytes(data[18:20], "little") != 183:
                        raise ValueError("Not an ARM64 ELF library: " + name)
                    return data
                name = value

        yield fetch

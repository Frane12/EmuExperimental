#!/usr/bin/env python3
"""Embed the exact pinned Blender distribution; never re-compress a ZIP in the APK."""
import hashlib
import pathlib
import shutil
import sys
import subprocess
import zipfile

VERSION = '4.5.14'
NAME = f'blender-{VERSION}-windows-arm64.zip'
URL = f'https://download.blender.org/release/Blender4.5/{NAME}'
SHA256 = '0153ecefc96a0e23985e6edcdbe04e909ba2078258f4ff6a05c21d4b7c756911'

def package(assets, source=None):
    assets = pathlib.Path(assets)
    folder = assets / 'blender'
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / NAME
    temporary = target.with_suffix('.partial')
    try:
        if source:
            with open(source, 'rb') as src, temporary.open('wb') as dst:
                shutil.copyfileobj(src, dst, 128 * 1024)
        else:
            subprocess.run(['curl', '--fail', '--location', '--retry', '3',
                            '--retry-all-errors', '--connect-timeout', '25', '--max-time', '360',
                            '--user-agent', 'Mozilla/5.0 DRNAS-Emu-Blender/4.5.14',
                            '--output', str(temporary), URL], check=True)
        with temporary.open('rb') as f:
            actual = hashlib.file_digest(f, 'sha256').hexdigest()
        if actual != SHA256:
            raise ValueError('Blender distribution SHA-256 mismatch')
        with zipfile.ZipFile(temporary) as z:
            exe = z.read(f'blender-{VERSION}-windows-arm64/blender.exe')
            pe = int.from_bytes(exe[60:64], 'little')
            if exe[pe:pe+6] != b'PE\0\0\x64\xaa':
                raise ValueError('Expected native ARM64 Blender PE')
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
    print(f'Blender {VERSION} ARM64 verified and bundled ({target.stat().st_size} bytes)')

if __name__ == '__main__':
    package(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)


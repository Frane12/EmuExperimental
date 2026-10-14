#!/usr/bin/env python3
"""Pin the Android AAudio PulseAudio build and package only its server modules."""
import hashlib, io, json, subprocess, sys, tarfile, tempfile, urllib.request, zipfile
from pathlib import Path
COMMIT = '95b9fcabd8533aa6e598587566618461b8b1c698'
BASE = f'https://raw.githubusercontent.com/utkarshdalal/GameNative/{COMMIT}/'
FILES = {
 'libsndfile.so':'f7ab9d12b1a5c60c7d4f11c535c6d0312ac74f92',
 'libltdl.so':'5cd08c89d30222a867ad3b0fcdc119d5e328c00b',
 'libpulse.so':'3443f4d4c87da7fc1249a0dad248282783e82104',
 'libpulseaudio.so':'bccfd71c8aa052f91d2901c9bceaa3ae4f3bb9de',
 'libpulsecommon-13.0.so':'f09b73d0fd0d9459b91fc2c3f43258eafa7f4dbb',
 'libpulsecore-13.0.so':'9f932277b331b8e40fa263beaea61d2a2369aed8',
}
def download(path, expected):
    data = urllib.request.urlopen(BASE+path, timeout=90).read()
    if hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest() != expected:
        raise RuntimeError('Upstream blob changed: '+path)
    return data
def main(root):
    root=Path(root); native=root/'jniLibs/arm64-v8a'; native.mkdir(parents=True,exist_ok=True)
    for name,sha in FILES.items():
        data=download('app/src/main/jniLibs/arm64-v8a/'+name,sha)
        if data[:5]!=b'\x7fELF\x02' or int.from_bytes(data[18:20],'little')!=183:
            raise RuntimeError('Not ARM64: '+name)
        (native/name).write_bytes(data)
    data=download('app/src/main/assets/pulseaudio-gamenative-20260919.tzst',
                  '9786c7562cbc6b902a583c5e8100f162a2971c1a')
    tarbytes=subprocess.run(['zstd','-d','-c'],input=data,stdout=subprocess.PIPE,check=True).stdout
    modules={}
    with tarfile.open(fileobj=io.BytesIO(tarbytes)) as tf:
        for m in tf:
            p=Path(m.name)
            if m.isfile() and p.parent.name=='modules' and p.name.startswith('module-') and p.suffix=='.so':
                modules[p.name]=tf.extractfile(m).read()
    required={'module-native-protocol-unix.so','module-aaudio-sink.so'}
    if not required.issubset(modules): raise RuntimeError('Required PulseAudio modules missing: '+str(modules.keys()))
    out=root/'assets/audio-runtime-v1.zip'; out.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in sorted(modules.items()): z.writestr('modules/'+name,data)
        z.writestr('manifest.json',json.dumps({'commit':COMMIT,'files':{n:hashlib.sha256(d).hexdigest() for n,d in modules.items()}}))
    license_dir=root/'assets/licenses'; license_dir.mkdir(parents=True,exist_ok=True)
    (license_dir/'GameNative-GPL-3.0.txt').write_bytes(download('LICENSE','5ae980bfc612213f0f8a6457978575b3ead3eae2'))
    (license_dir/'PulseAudio-NOTICE.txt').write_text('PulseAudio Android binaries and AAudio modules from GameNative commit '+COMMIT+'\nSource: https://github.com/utkarshdalal/GameNative/tree/'+COMMIT+'\nPulseAudio: LGPL-2.1-or-later; upstream https://www.freedesktop.org/wiki/Software/PulseAudio/\nGameNative modifications: GPL-3.0; see bundled license and source repository.\n')
    # Wine may dlopen either SONAME; use the same Android client ABI as the server.
    gui=root/'assets/gui-runtime-v1.zip'
    with zipfile.ZipFile(gui) as z: entries={n:z.read(n) for n in z.namelist()}
    manifest=json.loads(entries['manifest.json'])
    pulse=(native/'libpulse.so').read_bytes()
    entries['lib/libpulse.so.0']=pulse
    manifest['files']['libpulse.so.0']=hashlib.sha256(pulse).hexdigest()
    entries['manifest.json']=json.dumps(manifest,indent=2).encode()
    with zipfile.ZipFile(gui,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in entries.items(): z.writestr(name,data)
    print('Bundled PulseAudio + AAudio:',len(modules),'modules')
if __name__=='__main__': main(sys.argv[1])

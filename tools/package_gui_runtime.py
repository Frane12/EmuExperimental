#!/usr/bin/env python3
"""Package only X11/font client libraries from a pinned Bionic rootfs.
No Wine, Microsoft fonts, GPU drivers or redirect shims are copied.
"""
import hashlib,json,re,subprocess,sys,tarfile,tempfile,urllib.request,zipfile
from pathlib import Path
URL='https://downloads.gamenative.app/imagefs_bionic.txz'
FALLBACK_URL='https://pub-9fcd5294bd0d4b85a9d73615bf98f3b5.r2.dev/imagefs_bionic.txz'
SHA256='368db62bfc58b72c97e5169bda9aa64d4246f07964c447e27c79a065e7e9c48b'
ROOTS=['libX11.so.6','libXext.so.6','libXrender.so.1','libXrandr.so.2',
       'libXcursor.so.1','libXi.so.6','libXinerama.so.1','libXfixes.so.3',
       'libXcomposite.so.1','libXxf86vm.so.1','libfreetype.so.6','libfontconfig.so.1',
       'libvulkan.so.1']
SYSTEM={'libc.so','libdl.so','libm.so','liblog.so','libandroid.so'}
def package(archive,output):
    archive=Path(archive)
    if not archive.exists():
        part=archive.with_suffix('.download')
        for url in (URL,FALLBACK_URL):
            result=subprocess.run(['curl','--fail','--location','--retry','2','--max-time','180','--user-agent','DRNAS-Console-build/0.5.9','--output',str(part),url])
            if result.returncode==0 and hashlib.sha256(part.read_bytes()).hexdigest()==SHA256:
                part.replace(archive);break
            part.unlink(missing_ok=True)
        else:raise RuntimeError('Neither public upstream mirror supplied the pinned rootfs')
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=SHA256:
        raise RuntimeError('Bionic rootfs checksum changed: refuse unreviewed runtime')
    with tempfile.TemporaryDirectory() as temp,tarfile.open(archive,'r:xz') as tf:
        members={m.name.removeprefix('./'):m for m in tf.getmembers()}
        def content(name,seen=None):
            seen=set() if seen is None else seen
            if name in seen:raise RuntimeError('cyclic archive link')
            seen.add(name);m=members[name]
            if m.issym():
                target=str(Path(name).parent/m.linkname)
                if '..' in Path(target).parts or target.startswith('/'):raise RuntimeError('unsafe link')
                return content(target,seen)
            if m.islnk():return content(m.linkname.removeprefix('./'),seen)
            if not m.isfile():raise RuntimeError('not a regular library')
            return tf.extractfile(m).read()
        libs={};pending=list(ROOTS)
        while pending:
            soname=pending.pop()
            if soname in SYSTEM or soname in libs:continue
            data=content('usr/lib/'+soname)
            if data[:5]!=b'\x7fELF\x02' or int.from_bytes(data[18:20],'little')!=183:
                raise RuntimeError('not AArch64 ELF: '+soname)
            path=Path(temp)/soname;path.write_bytes(data)
            dynamic=subprocess.check_output(['readelf','-d',str(path)],text=True)
            deps=re.findall(r'\(NEEDED\).*?\[(.*?)\]',dynamic)
            libs[soname]=data;pending.extend(deps)
        output=Path(output);output.parent.mkdir(parents=True,exist_ok=True)
        manifest={name:hashlib.sha256(data).hexdigest() for name,data in sorted(libs.items())}
        with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
            for name,data in sorted(libs.items()):z.writestr('lib/'+name,data)
            z.writestr('manifest.json',json.dumps({'source':URL,'source_sha256':SHA256,'files':manifest},indent=2))
        print('GUI runtime: '+str(len(libs))+' ARM64 libraries, '+str(output.stat().st_size)+' bytes')
if __name__=='__main__':package(sys.argv[1],sys.argv[2])

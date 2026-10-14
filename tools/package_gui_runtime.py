#!/usr/bin/env python3
"""Package only X11/font client libraries from a pinned Bionic rootfs.
No Wine, Microsoft fonts, GPU drivers or redirect shims are copied.
"""
import hashlib,json,re,shutil,subprocess,sys,tarfile,tempfile,zipfile
from pathlib import Path
URL='https://downloads.gamenative.app/imagefs_bionic.txz'
FALLBACK_URL='https://pub-9fcd5294bd0d4b85a9d73615bf98f3b5.r2.dev/imagefs_bionic.txz'
SHA256='368db62bfc58b72c97e5169bda9aa64d4246f07964c447e27c79a065e7e9c48b'
ROOTS=['libX11.so.6','libXext.so.6','libXrender.so.1','libXrandr.so.2',
       'libXcursor.so.1','libXi.so.6','libXinerama.so.1','libXfixes.so.3',
       'libXcomposite.so.1','libXxf86vm.so.1','libfreetype.so.6','libfontconfig.so.1',
       'libvulkan.so.1','libpulse.so.0']
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
    # One streaming pass: tar.xz random seeks otherwise decompress the entire
    # image repeatedly for each dependency. Stage only top-level ELF libraries.
    with tempfile.TemporaryDirectory() as temp:
        members={}
        with tarfile.open(archive,'r|xz') as tf:
            for m in tf:
                name=m.name.removeprefix('./')
                path=Path(name)
                if path.parent!=Path('usr/lib') or not re.fullmatch(r'lib[^/]+\.so(?:\.[^/]+)?',path.name):continue
                if m.issym():
                    if '/' in m.linkname or m.linkname in ('.','..'):continue
                    members[path.name]=('link',m.linkname)
                elif m.isfile():
                    if m.size>96*1024*1024:continue
                    dest=Path(temp)/path.name
                    with tf.extractfile(m) as source,dest.open('wb') as out:shutil.copyfileobj(source,out)
                    members[path.name]=('file',dest)
                elif m.islnk():
                    target=Path(m.linkname.removeprefix('./'))
                    if target.parent!=Path('usr/lib'):continue
                    members[path.name]=('link',target.name)
        def content(name,seen=None):
            seen=set() if seen is None else seen
            if name in seen:raise RuntimeError('cyclic archive link')
            seen.add(name);kind,value=members[name]
            return content(value,seen) if kind=='link' else value.read_bytes()
        libs={};pending=list(ROOTS)
        while pending:
            soname=pending.pop()
            if soname in SYSTEM or soname in libs:continue
            data=content(soname)
            if data[:5]!=b'\x7fELF\x02' or int.from_bytes(data[18:20],'little')!=183:
                raise RuntimeError('not AArch64 ELF: '+soname)
            path=Path(temp)/('probe-'+soname);path.write_bytes(data)
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


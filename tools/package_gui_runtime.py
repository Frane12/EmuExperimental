#!/usr/bin/env python3
"""Package only X11/font client libraries from a pinned Bionic rootfs.
No Wine, Microsoft fonts, GPU drivers or redirect shims are copied.
"""
import hashlib,io,json,re,shutil,subprocess,sys,tarfile,tempfile,zipfile
from pathlib import Path
URL='https://downloads.gamenative.app/imagefs_bionic.txz'
FALLBACK_URL='https://pub-9fcd5294bd0d4b85a9d73615bf98f3b5.r2.dev/imagefs_bionic.txz'
SHA256='368db62bfc58b72c97e5169bda9aa64d4246f07964c447e27c79a065e7e9c48b'
ROOTS=['libX11.so.6','libXext.so.6','libXrender.so.1','libXrandr.so.2',
       'libXcursor.so.1','libXi.so.6','libXinerama.so.1','libXfixes.so.3',
       'libXcomposite.so.1','libXxf86vm.so.1','libfreetype.so.6','libfontconfig.so.1',
       'libvulkan.so.1']
SYSTEM={'libc.so','libdl.so','libm.so','liblog.so','libandroid.so','libnativewindow.so'}
# Immutable upstream Mesa Bionic WSI wrapper + AdrenoTools hooks. Keep the
# Khronos loader separate: its ICD is this wrapper, whose backend is Turnip.
WRAPPER_URL='https://raw.githubusercontent.com/The412Banner/Bannerlator/e91d93bb23b0f2dde953864484b6f620f72d9cad/app/src/main/assets/graphics_driver/wrapper.tzst'
WRAPPER_SHA256='2005169d30ab7558b0c404ff8a3d781cddfde2f012531e097ccc95c423a706a5'
def wrapper_libraries():
    with tempfile.TemporaryDirectory() as folder:
        archive=Path(folder)/'wrapper.tzst'
        subprocess.run(['curl','-fL','--retry','3','--max-time','180',WRAPPER_URL,'-o',str(archive)],check=True)
        if hashlib.sha256(archive.read_bytes()).hexdigest()!=WRAPPER_SHA256:
            raise RuntimeError('Vulkan wrapper checksum mismatch')
        # zstd is installed on the build runner; never extract archive paths.
        data=subprocess.check_output(['zstd','-dc',str(archive)])
        result={}
        with tarfile.open(fileobj=io.BytesIO(data),mode='r:') as tf:
            for item in tf:
                path=Path(item.name.removeprefix('./'))
                if path.parent==Path('usr/lib') and item.isfile() and re.fullmatch(r'lib[A-Za-z0-9_+-]+\.so',path.name):
                    result[path.name]=tf.extractfile(item).read()
        expected={'libvulkan_wrapper.so','libadrenotools.so','libmain_hook.so','libhook_impl.so','libfile_redirect_hook.so','libgsl_alloc_hook.so'}
        if set(result)!=expected:raise RuntimeError('Unexpected wrapper payload')
        return result
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
        wrapped=wrapper_libraries()
        for name,data in wrapped.items():
            dest=Path(temp)/('wrapper-'+name);dest.write_bytes(data)
            members[name]=('file',dest)
        # GLX cannot be substituted with GLES: Wine expects real libGL.so.1.
        # Some pinned GameNative rootfs archives contain a dangling libGL
        # symlink (libGL.so.1 -> libGL.so.1.5.0). Never package a broken
        # library or silently pretend OpenGL is functional.
        optional_gl=[]
        if 'libGL.so.1' in members:
            try:
                content('libGL.so.1')
            except KeyError as error:
                print('WARNING: upstream libGL.so.1 is dangling ('+
                      str(error)+'); GLX remains unavailable; DXVK Vulkan is unaffected.')
            else:
                optional_gl=['libGL.so.1']
        else:
            print('WARNING: upstream Bionic libGL.so.1 absent; GLX remains unavailable.')
        libs={};pending=list(ROOTS)+list(wrapped)+optional_gl
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
        # Wine 11 can use EGL instead of GLX for WGL, provided real Bionic
        # EGL + desktop OpenGL libraries and an EGL-capable X11 platform exist.
        # Do not alias GLES as desktop GL: Blender needs full OpenGL 4.3+.
        # Import both roots atomically so a partial/dangling rootfs cannot
        # break the known-good X11/Vulkan bundle or the permanent APK build.
        def extra_egl():
            extra={};queue=['libEGL.so.1','libOpenGL.so.0']
            while queue:
                name=queue.pop()
                if name in SYSTEM or name in libs or name in extra:continue
                data=content(name)
                if data[:5]!=b'\x7fELF\x02' or int.from_bytes(data[18:20],'little')!=183:
                    raise RuntimeError('not Bionic AArch64 ELF: '+name)
                if len(data)>64*1024*1024:
                    raise RuntimeError('oversized optional EGL dependency: '+name)
                elf=Path(temp)/('egl-probe-'+name);elf.write_bytes(data)
                dynamic=subprocess.check_output(['readelf','-d',str(elf)],text=True)
                extra[name]=data
                queue.extend(re.findall(r'\(NEEDED\).*?\[(.*?)\]',dynamic))
            return extra
        try:
            egl_libs=extra_egl()
            if 'libEGL.so.1' not in libs and 'libEGL.so.1' not in egl_libs:
                raise RuntimeError('EGL entrypoint unavailable')
            if 'libOpenGL.so.0' not in libs and 'libOpenGL.so.0' not in egl_libs:
                raise RuntimeError('desktop OpenGL entrypoint unavailable')
        except (KeyError,RuntimeError,subprocess.CalledProcessError) as error:
            print('WARNING: pinned Bionic EGL desktop GL unavailable: '+str(error)+
                  '; native Android GLES test remains separate from Wine WGL.')
            egl_libs={}
        else:
            if len(libs)+len(egl_libs)>60 or (
                    sum(len(v) for v in libs.values())+
                    sum(len(v) for v in egl_libs.values())>55*1024*1024):
                raise RuntimeError('Optional EGL dependencies exceed GUI runtime safety limits')
            libs.update(egl_libs)
            print('Wine EGL candidate: '+str(len(egl_libs))+
                  ' dependency libraries staged (real EGL context unverified)')
        output=Path(output);output.parent.mkdir(parents=True,exist_ok=True)
        manifest={name:hashlib.sha256(data).hexdigest() for name,data in sorted(libs.items())}
        with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
            for name,data in sorted(libs.items()):z.writestr('lib/'+name,data)
            z.writestr('manifest.json',json.dumps({'source':URL,'source_sha256':SHA256,
                'wrapper_source':WRAPPER_URL,'wrapper_sha256':WRAPPER_SHA256,'files':manifest},indent=2))
        print('GUI runtime: '+str(len(libs))+' ARM64 libraries, '+str(output.stat().st_size)+' bytes')
if __name__=='__main__':package(sys.argv[1],sys.argv[2])



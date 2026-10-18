package com.drnas.console;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Transactional first-use extraction. No Android or Wine APIs; tested on JVM. */
final class BundledBlenderArchive {
    static final String VERSION="4.5.14";
    static final String SHA256="0153ecefc96a0e23985e6edcdbe04e909ba2078258f4ff6a05c21d4b7c756911";
    static final String NAME="blender-"+VERSION+"-windows-arm64.zip";
    static final String DIRECTORY="Blender-"+VERSION;
    static final String WINDOWS_EXE="C:\\DRNAS\\Apps\\"+DIRECTORY+"\\blender.exe";
    static final String WINDOWS_SETUP="C:\\DRNAS\\Apps\\"+DIRECTORY+"\\drnas-startup.py";
    static final long MAX_EXPANDED=1024L*1024*1024;
    static final String SETUP="import bpy, os\n"+
            "# D: is a real selected Android folder, validated before Wine starts.\n"+
            "if os.path.isdir('D:\\\\'):\n"+
            "    os.chdir('D:\\\\')\n"+
            "    def drnas_file_browser_directory():\n"+
            "        for window in bpy.context.window_manager.windows:\n"+
            "            for area in window.screen.areas:\n"+
            "                if area.type == 'FILE_BROWSER':\n"+
            "                    space = area.spaces.active\n"+
            "                    key = area.as_pointer()\n"+
            "                    if space.params and key not in drnas_file_browsers:\n"+
            "                        directory = os.fsdecode(space.params.directory)\n"+
            "                        if not bpy.data.filepath and (not directory or directory.lower().startswith('c:')):\n"+
            "                            space.params.directory = b'D:\\\\'\n"+
            "                        drnas_file_browsers.add(key)\n"+
            "        return 0.5\n"+
            "    drnas_file_browsers = set()\n"+
            "    bpy.app.timers.register(drnas_file_browser_directory, first_interval=0.5, persistent=True)\n"+
            "# First-run portable preferences only. Preserve every later user edit.\n"+
            "if not os.path.isfile(bpy.utils.user_resource('CONFIG', path='userpref.blend')):\n"+
            "    bpy.context.preferences.view.show_splash = False\n"+
            "    bpy.context.preferences.inputs.use_emulate_numpad = True\n"+
            "    bpy.context.preferences.inputs.use_mouse_emulate_3_button = True\n"+
            "    bpy.ops.wm.save_userpref()\n";

    static boolean ready(Path target) throws IOException {
        Path marker=target.resolve(".drnas-bundle-sha256");
        return !Files.isSymbolicLink(target) && Files.isRegularFile(target.resolve("blender.exe"),LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)
                && Files.size(marker)==SHA256.length()
                && SHA256.equals(new String(Files.readAllBytes(marker),StandardCharsets.US_ASCII));
    }

    static void extract(InputStream input, Path target, String expectedSha, Consumer<String> log) throws Exception {
        Path parent=target.toAbsolutePath().getParent();
        // Every ancestor is app-owned and must not redirect extraction outside the prefix.
        for(Path p=parent;p!=null;p=p.getParent())
            if(Files.isSymbolicLink(p))throw new IOException("Unsafe Blender parent");
        Files.createDirectories(parent);
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("Incomplete Blender install; refusing to overwrite user data");
        Path stage=Files.createTempDirectory(parent,".blender-install-");
        boolean committed=false;
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[128*1024];
            long total=0;int entries=0,lastPercent=-1;
            String root="blender-"+VERSION+"-windows-arm64/";
            try(DigestInputStream checked=new DigestInputStream(input,digest);
                ZipInputStream zip=new ZipInputStream(checked)) {
                ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    if(++entries>20000)throw new IOException("Too many Blender entries");
                    String name=entry.getName();
                    if(!name.startsWith(root)||name.indexOf('\\')>=0)throw new IOException("Invalid Blender archive root");
                    String relative=name.substring(root.length());
                    if(relative.isEmpty())continue;
                    Path output=stage.resolve(relative).normalize();
                    if(!output.startsWith(stage)||output.equals(stage))throw new IOException("Unsafe Blender archive path");
                    if(entry.isDirectory())Files.createDirectories(output);
                    else {
                        Files.createDirectories(output.getParent());
                        try(OutputStream out=Files.newOutputStream(output,StandardOpenOption.CREATE_NEW)) {
                            int n;
                            while((n=zip.read(buffer))!=-1) {
                                total+=n;
                                if(total>MAX_EXPANDED)throw new IOException("Blender unpacked size exceeds limit");
                                out.write(buffer,0,n);
                            }
                        }
                    }
                    int percent=(int)Math.min(99,total*100/707171882L);
                    if(percent/10!=lastPercent/10 || lastPercent<0) {
                        lastPercent=percent;log.accept("[Startup] Blender unpacking "+percent+"%");
                    }
                }
                // ZipInputStream stops at the central directory. Hash its remaining
                // bytes too, including any trailing data, before committing files.
                while(checked.read(buffer)!=-1) { }
            }
            StringBuilder actual=new StringBuilder(64);
            for(byte value:digest.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            if(!expectedSha.equals(actual.toString()))throw new IOException("Blender archive hash mismatch");
            Path exe=stage.resolve("blender.exe");
            try(RandomAccessFile pe=new RandomAccessFile(exe.toFile(),"r")) {
                if(pe.readUnsignedByte()!='M'||pe.readUnsignedByte()!='Z')throw new IOException("Invalid Blender executable");
                pe.seek(60);int offset=Integer.reverseBytes(pe.readInt());
                if(offset<64 || offset>pe.length()-6)throw new IOException("Invalid Blender PE offset");
                pe.seek(offset);
                if(pe.readInt()!=0x50450000 || pe.readUnsignedByte()!=0x64 || pe.readUnsignedByte()!=0xaa)
                    throw new IOException("Blender must be Windows ARM64");
            }
            Files.createDirectories(stage.resolve("4.5/config"));
            Files.write(stage.resolve("drnas-startup.py"),SETUP.getBytes(StandardCharsets.UTF_8));
            Files.write(stage.resolve(".drnas-bundle-sha256"),expectedSha.getBytes(StandardCharsets.US_ASCII));
            // Same filesystem, atomic commit. A killed extraction never appears ready.
            Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);
            committed=true;
            log.accept("[Startup] Blender "+VERSION+" ARM64 ready 100%");
        } finally {
            if(!committed)deleteStage(stage);
        }
    }
    private static void deleteStage(Path stage) throws IOException {
        Files.walkFileTree(stage,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                Files.delete(file);return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir,IOException error) throws IOException {
                if(error!=null)throw error;Files.delete(dir);return FileVisitResult.CONTINUE;
            }
        });
    }
    private BundledBlenderArchive(){}
}



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
            // A/B r48: preserve D: startup; do not load the experimental r46 Win32 window hook.
            "# Filter Blender's Volumes panel only; Wine still needs its internal C: disk.\n"+
            "from bl_ui import space_filebrowser as drnas_file_ui\n"+
            "class DRNAS_UL_project_volumes(bpy.types.UIList):\n"+
            "    draw_item = drnas_file_ui.FILEBROWSER_UL_dir.draw_item\n"+
            "    def filter_items(self, context, data, propname):\n"+
            "        return ([self.bitflag_filter_item if item.path.replace('/', '\\\\').upper().rstrip('\\\\') == 'D:' else 0 for item in getattr(data, propname)], [])\n"+
            "if not hasattr(bpy.types, 'DRNAS_UL_project_volumes'):\n"+
            "    bpy.utils.register_class(DRNAS_UL_project_volumes)\n"+
            "def drnas_draw_project_volumes(self, context):\n"+
            "    space = context.space_data\n"+
            "    if space.system_folders:\n"+
            "        self.layout.template_list('DRNAS_UL_project_volumes', 'system_folders', space, 'system_folders', space, 'system_folders_active', item_dyntip_propname='path', rows=1, maxrows=1)\n"+
            "drnas_file_ui.FILEBROWSER_PT_bookmarks_volumes.draw = drnas_draw_project_volumes\n"+
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
            "    bpy.ops.wm.save_userpref()\n"+
            "# r55: restore r50 startup; no Win32 window mutation on Blender UI thread.\n"+
            "print('DRNAS_BLENDER_LAYOUT_DISABLED r57 r50-window-policy', flush=True)\n"+
            "# r57: replace the dangerous native fullscreen menu entry, NOT the guest HWND.\n"+
            "# Android already crops the title strip using a matched compositor/input transform.\n"+
            "try:\n"+
            "    import bl_ui.space_topbar as drnas_topbar\n"+
            "    class DRNAS_OT_safe_fullscreen(bpy.types.Operator):\n"+
            "        bl_idname = \"drnas.safe_fullscreen\"\n"+
            "        bl_label = \"Toggle Window Fullscreen (DRNAS)\"\n"+
            "        bl_description = \"Toggle Android fullscreen safely, without Wine window mutation\"\n"+
            "        def execute(self, context):\n"+
            "            try:\n"+
            "                signal = os.path.join(os.path.dirname(__file__), 'drnas-host-fullscreen.signal')\n"+
            "                seq = bpy.app.driver_namespace.get('drnas_host_fullscreen_seq', 0) + 1\n"+
            "                bpy.app.driver_namespace['drnas_host_fullscreen_seq'] = seq\n"+
            "                tmp = signal + '.tmp'\n"+
            "                with open(tmp, 'w', encoding='ascii') as output:\n"+
            "                    output.write('r57:%d' % seq)\n"+
            "                os.replace(tmp, signal)\n"+
            "                print('DRNAS_BLENDER_SAFE_FULLSCREEN_REQUEST seq=%d' % seq, flush=True)\n"+
            "                self.report({'INFO'}, \"Android fullscreen toggle requested\")\n"+
            "                return {'FINISHED'}\n"+
            "            except Exception as error:\n"+
            "                print('DRNAS_BLENDER_SAFE_FULLSCREEN_REQUEST_FAILED', repr(error), flush=True)\n"+
            "                self.report({'WARNING'}, \"Fullscreen request failed; native Wine fullscreen avoided\")\n"+
            "                return {'CANCELLED'}\n"+
            "    if not hasattr(bpy.types, 'DRNAS_OT_safe_fullscreen'):\n"+
            "        bpy.utils.register_class(DRNAS_OT_safe_fullscreen)\n"+
            "    def drnas_window_menu(self, context):\n"+
            "        layout = self.layout\n"+
            "        layout.operator(\"wm.window_new\", text=\"New Window\")\n"+
            "        layout.operator(\"wm.window_new_main\", text=\"New Main Window\")\n"+
            "        layout.separator()\n"+
            "        layout.operator(\"drnas.safe_fullscreen\", text=\"Toggle Window Fullscreen (DRNAS)\", icon='FULLSCREEN_ENTER')\n"+
            "        layout.separator()\n"+
            "        layout.operator(\"screen.workspace_cycle\", text=\"Next Workspace\").direction = 'NEXT'\n"+
            "        layout.operator(\"screen.workspace_cycle\", text=\"Previous Workspace\").direction = 'PREV'\n"+
            "        layout.separator()\n"+
            "        layout.prop(context.screen, \"show_statusbar\", text=\"Show Status Bar\")\n"+
            "        layout.separator()\n"+
            "        layout.operator(\"screen.screenshot\", text=\"Save Screenshot\")\n"+
            "        layout.operator(\"screen.screenshot_area\", text=\"Save Screenshot (Editor)\")\n"+
            "        layout.separator()\n"+
            "        layout.operator(\"wm.console_toggle\", text=\"Toggle System Console\")\n"
+
            "    drnas_topbar.TOPBAR_MT_window.draw = drnas_window_menu\n"+
            "    print(\"DRNAS_BLENDER_SAFE_FULLSCREEN_MENU installed=true r57\", flush=True)\n"+
            "except Exception as exc:\n"+
            "    print(\"DRNAS_BLENDER_SAFE_FULLSCREEN_MENU installed=false\", repr(exc), flush=True)\n"+
            "# r60: truly smooth camera distance, no Win32 window resizing or fullscreen.\n"+
            "# A 40 Hz Blender main-thread timer is used only for normal VIEW_3D RNA updates.\n"+
            "try:\n"+
            "    import math\n"+
            "    _drnas_zoom_signal = os.path.join(os.path.dirname(__file__), 'drnas-smooth-zoom.signal')\n"+
            "    _drnas_zoom_seq = 0\n"+
            "    _drnas_zoom_total = 0\n"+
            "    _drnas_zoom_pending = 0\n"+
            "    _drnas_zoom_target = None\n"+
            "    _drnas_zoom_warnings = 0\n"+
            "    def drnas_smooth_zoom_tick():\n"+
            "        global _drnas_zoom_seq, _drnas_zoom_total, _drnas_zoom_pending\n"+
            "        global _drnas_zoom_target, _drnas_zoom_warnings\n"+
            "        try:\n"+
            "            try:\n"+
            "                with open(_drnas_zoom_signal, 'r', encoding='ascii') as command:\n"+
            "                    raw = command.read(64).strip().split(':')\n"+
            "                if len(raw) == 3 and raw[0] == 'r60':\n"+
            "                    seq = int(raw[1])\n"+
            "                    total = int(raw[2])\n"+
            "                    if seq > _drnas_zoom_seq:\n"+
            "                        change = max(-250, min(250, total - _drnas_zoom_total))\n"+
            "                        _drnas_zoom_seq = seq\n"+
            "                        _drnas_zoom_total = total\n"+
            "                        _drnas_zoom_pending = max(-500, min(500, _drnas_zoom_pending + change))\n"+
            "            except OSError:\n"+
            "                pass\n"+
            "            area = None\n"+
            "            if _drnas_zoom_pending or _drnas_zoom_target is not None:\n"+
            "                score = -1\n"+
            "                for window in bpy.context.window_manager.windows:\n"+
            "                    for candidate in window.screen.areas:\n"+
            "                        if candidate.type == 'VIEW_3D':\n"+
            "                            measure = candidate.width * candidate.height\n"+
            "                            if measure > score:\n"+
            "                                score = measure\n"+
            "                                area = candidate\n"+
            "            if area is not None:\n"+
            "                region = area.spaces.active.region_3d\n"+
            "                current = max(0.00001, float(region.view_distance))\n"+
            "                if _drnas_zoom_pending:\n"+
            "                    change = max(-100, min(100, _drnas_zoom_pending))\n"+
            "                    _drnas_zoom_pending -= change\n"+
            "                    base = _drnas_zoom_target if _drnas_zoom_target is not None else current\n"+
            "                    _drnas_zoom_target = max(0.08, min(20000.0, base * math.exp(-0.023 * change)))\n"+
            "                target = _drnas_zoom_target\n"+
            "                if target is not None:\n"+
            "                    remaining = math.log(target / current)\n"+
            "                    if abs(remaining) < 0.001:\n"+
            "                        region.view_distance = target\n"+
            "                        _drnas_zoom_target = None\n"+
            "                    else:\n"+
            "                        region.view_distance = current * math.exp(remaining * 0.32)\n"+
            "                    area.tag_redraw()\n"+
            "            return 0.025 if (_drnas_zoom_target is not None or _drnas_zoom_pending) else 0.065\n"+
            "        except Exception as error:\n"+
            "            if _drnas_zoom_warnings < 4:\n"+
            "                print('DRNAS_SMOOTH_ZOOM_TICK_ERROR', repr(error), flush=True)\n"+
            "            _drnas_zoom_warnings += 1\n"+
            "            return 0.16\n"+
            "    bpy.app.timers.register(drnas_smooth_zoom_tick, first_interval=0.16, persistent=True)\n"+
            "    print('DRNAS_SMOOTH_ZOOM_TIMER registered=true r60 interval_active_ms=25 easing=0.32', flush=True)\n"+
            "except Exception as error:\n"+
            "    print('DRNAS_SMOOTH_ZOOM_TIMER registered=false r60', repr(error), flush=True)\n";

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




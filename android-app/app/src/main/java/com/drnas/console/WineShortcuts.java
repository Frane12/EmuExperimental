package com.drnas.console;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Real Wine launchpad: known Wine system commands and observed .lnk files.
 * No fake preinstalled games; shortcuts are added only when Wine writes them.
 * Relative link identifiers are revalidated at launch and never passed to a shell.
 */
final class WineShortcuts {
    static final class Entry {
        final String id, title, glyph, status;
        Entry(String id,String title,String glyph,String status) {
            this.id=id;this.title=title;this.glyph=glyph;this.status=status;
        }
    }
    private static final String[][] BUILTINS = {
        {"notepad","Notepad","▤","notepad.exe"},
        {"wordpad","WordPad","▣","wordpad.exe"},
        {"paint","DRNAS Paint","✎","C:\\DRNAS\\Tools\\DRNASPaint.exe"},
        {"retropad","DRNAS Pad","▤","C:\\DRNAS\\Tools\\DRNASPad.exe"},
        {"tasks","Task Manager","▥","taskmgr.exe"},
        {"calc","Calculator","▦","calc.exe"},
        {"control","Control Panel","⚙","control.exe"},
        {"cmd","CMD","⌘","cmd.exe"},
        {"winecfg","Wine Settings","⚒","winecfg.exe"},
        {"files","File Manager","▣","winefile.exe"}
    };
    static String builtinExecutable(String id) {
        if(id==null)return null;
        for(String[] spec:BUILTINS)if(spec[0].equals(id))return spec[3];
        return null;
    }
    static boolean isLaunchRequest(String app) {
        if(app==null)return false;
        if(app.startsWith("builtin:"))return builtinExecutable(app.substring(8))!=null;
        return app.startsWith("link:") && app.length()<600 && app.endsWith(".lnk");
    }
    static File prefix(Context context) {
        return new File(context.getFilesDir(),"containers/default/wineboot-probe/template/.wine");
    }
    static List<Entry> list(Context context) {
        List<Entry> entries=new ArrayList<>();
        for(String[] spec:BUILTINS)entries.add(new Entry("builtin:"+spec[0],spec[1],spec[2],"Wine"));
        File drive=new File(prefix(context),"drive_c");
        if(!drive.isDirectory())return entries;
        File users=new File(drive,"users");
        File[] homes=users.listFiles();
        if(homes==null)return entries;
        List<Entry> discovered=new ArrayList<>();
        Set<String> visited=new HashSet<>();
        // Built-in Windows icon locations are included only after Wine initializes.
        for(File home:homes) {
            if(!home.isDirectory() || home.getName().startsWith(".") || visited.size()>160)continue;
            discover(drive,new File(home,"Desktop"),0,visited,discovered);
            discover(drive,new File(home,"Start Menu/Programs"),0,visited,discovered);
            discover(drive,new File(home,"AppData/Roaming/Microsoft/Windows/Start Menu/Programs"),
                    0,visited,discovered);
        }
        Collections.sort(discovered,Comparator.comparing(x->x.title.toLowerCase(Locale.ROOT)));
        entries.addAll(discovered);
        return entries;
    }
    private static void discover(File drive,File directory,int depth,Set<String> visited,
                                 List<Entry> result) {
        if(depth>4 || result.size()>=100 || visited.size()>=250 || !directory.isDirectory())return;
        try {
            File canonical=directory.getCanonicalFile();
            if(!canonical.toPath().startsWith(drive.getCanonicalFile().toPath()))return;
            if(!visited.add(canonical.getAbsolutePath()))return;
            File[] children=directory.listFiles();
            if(children==null)return;
            for(File child:children) {
                if(result.size()>=100 || visited.size()>=250)break;
                if(Files.isSymbolicLink(child.toPath()))continue;
                if(child.isDirectory()) {discover(drive,child,depth+1,visited,result);continue;}
                if(!child.isFile() || child.length()>1024*1024 ||
                        !child.getName().toLowerCase(Locale.ROOT).endsWith(".lnk"))continue;
                String relative=drive.getCanonicalFile().toPath()
                        .relativize(child.getCanonicalFile().toPath()).toString()
                        .replace(File.separatorChar,'/');
                if(relative.length()>480 || relative.contains(".."))continue;
                String filename=child.getName();
                String name=filename.substring(0,filename.length()-4);
                if(name.trim().isEmpty())continue;
                result.add(new Entry("link:"+relative,name,"↗","Wine shortcut"));
            }
        } catch(IOException ignored) { // damaged links never break the launchpad
        }
    }
    static File resolveLink(File prefix,String relative) throws IOException {
        if(relative==null || relative.length()>480 || !relative.endsWith(".lnk") ||
                relative.startsWith("/") || relative.contains("..") || relative.indexOf((char)0)>=0)
            throw new IOException("Invalid Wine shortcut");
        File drive=new File(prefix,"drive_c").getCanonicalFile();
        File link=new File(drive,relative).getCanonicalFile();
        if(!link.toPath().startsWith(drive.toPath()) || !link.isFile() ||
                link.length()>1024*1024)
            throw new IOException("Wine shortcut no longer exists");
        return link;
    }
    private WineShortcuts(){}
}

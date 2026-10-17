package com.drnas.console;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Narrow Crysis DXVK fullscreen-compatibility workaround.
 *
 * The embedded X server presents a fixed 1280x720 desktop and has no RandR
 * mode switching. Crysis 1 can request 960x540 exclusive fullscreen during
 * renderer initialization; configure windowed mode in the guest's own
 * Documents/My Games/Crysis/game.cfg instead of pretending RandR works.
 *
 * Other titles and original game files in R: are never modified.
 */
final class CrysisWindowedGuard {
    static final String BEGIN="-- DRNAS Crysis 1 X11 compatibility begin";
    static final String END="-- DRNAS Crysis 1 X11 compatibility end";
    private static final String OVERRIDE="\n"+BEGIN+"\n"+
            "r_Fullscreen = 0\nr_Width = 1280\nr_Height = 720\n"+END+"\n";

    static void prepareIfInstalled(File shared,File prefix,Consumer<String> log)
            throws IOException {
        if(!isInstalled(shared)) {
            log.accept("Crysis fullscreen guard: no Crysis/Bin32/Crysis.exe on R:, not applied.");
            return;
        }
        prepareKnownInstall(prefix,log);
    }

    static boolean isInstalled(File shared) {
        return containsCrysisExecutable(shared,0,new int[]{20000});
    }

    /** Known install: no walk of R:, only the existing C: user configuration. */
    static void prepareKnownInstall(File prefix,Consumer<String> log) throws IOException {
        File docs=new File(prefix,"drive_c/users/xuser/Documents/My Games");
        if(!Files.isDirectory(docs.toPath(),LinkOption.NOFOLLOW_LINKS) ||
                Files.isSymbolicLink(docs.toPath()))
            throw new IOException("Unsafe or absent xuser My Games directory");
        File game=findChildIgnoreCase(docs,"Crysis");
        if(game==null)game=new File(docs,"Crysis");
        if(Files.isSymbolicLink(game.toPath()))throw new IOException("Crysis profile is a symlink");
        if(!game.exists())Files.createDirectory(game.toPath());
        if(!Files.isDirectory(game.toPath(),LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Crysis profile is not a directory");
        File cfg=findChildIgnoreCase(game,"game.cfg");
        if(cfg==null)cfg=new File(game,"game.cfg");
        if(Files.isSymbolicLink(cfg.toPath()) || cfg.exists()&&!cfg.isFile())
            throw new IOException("Unsafe Crysis game.cfg");
        String old=cfg.isFile()?new String(Files.readAllBytes(cfg.toPath()),StandardCharsets.ISO_8859_1):"";
        // Replace only our own managed block, retaining ALL of the user's
        // original settings. The last assignments win for CryEngine config.
        String cleaned=old.replaceAll("(?s)\\n?"+java.util.regex.Pattern.quote(BEGIN)+
                ".*?"+java.util.regex.Pattern.quote(END)+"\\r?\\n?","");
        String updated=cleaned+OVERRIDE;
        if(updated.equals(old)) {
            log.accept("Crysis fullscreen guard: existing windowed 1280x720 preset verified.");
            return;
        }
        if(cfg.isFile()) {
            Path backup=new File(game,cfg.getName()+".drnas-original").toPath();
            if(Files.isSymbolicLink(backup))
                throw new IOException("Unsafe Crysis config backup");
            if(!Files.exists(backup,LinkOption.NOFOLLOW_LINKS))
                Files.copy(cfg.toPath(),backup);
        }
        Path tmp=Files.createTempFile(game.toPath(),".drnas-crysis-",".tmp");
        try {
            Files.write(tmp,updated.getBytes(StandardCharsets.ISO_8859_1));
            Files.move(tmp,cfg.toPath(),StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(tmp);}
        log.accept("Crysis fullscreen guard: game.cfg prepared at C:\\users\\xuser\\Documents\\My Games\\Crysis; windowed 1280x720, original config backed up. R: game untouched.");
    }

    private static File findChildIgnoreCase(File folder,String name) {
        File[] children=folder.listFiles();
        if(children==null)return null;
        for(File child:children)if(child.getName().equalsIgnoreCase(name))return child;
        return null;
    }
    private static boolean containsCrysisExecutable(File path,int depth,int[] budget) {
        if(depth>7 || budget[0]--<=0 || Files.isSymbolicLink(path.toPath()))return false;
        if(!Files.isDirectory(path.toPath(),LinkOption.NOFOLLOW_LINKS))return false;
        File bin=findChildIgnoreCase(path,"Bin32");
        if(bin!=null && Files.isDirectory(bin.toPath(),LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(bin.toPath())) {
            File exe=findChildIgnoreCase(bin,"Crysis.exe");
            if(exe!=null && Files.isRegularFile(exe.toPath(),LinkOption.NOFOLLOW_LINKS))
                return true;
        }
        File[] children=path.listFiles();
        if(children!=null)for(File child:children) {
            if(Files.isDirectory(child.toPath(),LinkOption.NOFOLLOW_LINKS) &&
                    containsCrysisExecutable(child,depth+1,budget))return true;
        }
        return false;
    }
    private CrysisWindowedGuard(){}
}

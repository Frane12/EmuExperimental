package com.drnas.console;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Repair known Windows profile folders without ever replacing a user's files. */
final class WinePrefixLayout {
    private static final String[] FOLDERS={
            "drive_c/users", "drive_c/users/xuser",
            "drive_c/users/xuser/Documents", "drive_c/users/xuser/Documents/My Games",
            "drive_c/users/xuser/Desktop", "drive_c/users/xuser/Downloads",
            "drive_c/users/xuser/Pictures", "drive_c/users/xuser/Music",
            "drive_c/users/xuser/Videos", "drive_c/users/xuser/Saved Games",
            "drive_c/users/xuser/AppData", "drive_c/users/xuser/AppData/Local",
            "drive_c/users/xuser/AppData/LocalLow",
            "drive_c/users/xuser/AppData/Roaming",
            "drive_c/users/xuser/AppData/Local/Temp",
            "drive_c/ProgramData", "drive_c/Program Files", "drive_c/Program Files (x86)"
    };

    static void ensure(File prefix,Consumer<String> log) throws IOException {
        Path base=prefix.getCanonicalFile().toPath();
        Path drive=base.resolve("drive_c");
        if(!Files.isDirectory(drive,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Wine drive_c is missing or unsafe");
        int made=0;
        for(String folder:FOLDERS) {
            Path relative=java.nio.file.Paths.get(folder);
            Path cursor=base;
            for(Path part:relative) {
                cursor=cursor.resolve(part);
                if(Files.isSymbolicLink(cursor))
                    throw new IOException("Refuse symlinked Windows profile directory: "+folder);
            }
            if(Files.exists(cursor,LinkOption.NOFOLLOW_LINKS)) {
                if(!Files.isDirectory(cursor,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Windows profile path is not a directory: "+folder);
            } else {
                Files.createDirectories(cursor);
                made++;
            }
        }
        // The imported Winlator/Proton prefix can contain an absolute c: symlink
        // that the archive extractor correctly defers for sandbox safety.
        // Wine's Windows C: must be mapped back to OUR staged drive_c.
        ensureSystemDriveMapping(base, log);
        log.accept("Wine profile repaired: C:\\users\\xuser\\Documents (+ My Games), created "+made+" folders.");
    }


    /** Restore only the local Wine C: mapping, never remap or overwrite R:. */
    private static void ensureSystemDriveMapping(Path base, Consumer<String> log) throws IOException {
        Path drive=base.resolve("drive_c");
        Path dos=base.resolve("dosdevices");
        if(Files.isSymbolicLink(dos))
            throw new IOException("Wine dosdevices is an unsafe symlink");
        if(Files.exists(dos,LinkOption.NOFOLLOW_LINKS)) {
            if(!Files.isDirectory(dos,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Wine dosdevices is not a directory");
        } else {
            Files.createDirectories(dos);
        }
        Path c=dos.resolve("c:");
        if(Files.isSymbolicLink(c)) {
            Path target=Files.readSymbolicLink(c);
            Path resolved=(target.isAbsolute()?target:dos.resolve(target)).normalize();
            if(!resolved.equals(drive))
                throw new IOException("C: points outside this Wine drive_c; refusing to overwrite existing mapping");
        } else if(Files.exists(c,LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("C: already exists as a non-symlink; refusing to overwrite it");
        } else {
            Files.createSymbolicLink(c,java.nio.file.Paths.get("../drive_c"));
            log.accept("Restored Wine C: -> ../drive_c (R: stays shared Android storage)");
        }
    }

    private WinePrefixLayout(){}
}

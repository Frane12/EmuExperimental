package com.drnas.console;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Atomic app-private Blender render close request. Never sends blind Alt+F4. */
final class BlenderRenderExitSignal {
    static Path path(File winePrefix) {
        return winePrefix.toPath().resolve("drive_c/DRNAS/Apps/")
                .resolve(BundledBlenderArchive.DIRECTORY)
                .resolve("drnas-render-exit.signal");
    }
    static String encode(int sequence) {
        if(sequence<1||sequence>9999999)throw new IllegalArgumentException("Render sequence");
        return "r62:"+sequence;
    }
    static void write(Path target,int sequence) throws IOException {
        Path parent=target.getParent();
        if(parent==null || !Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(parent) || Files.isSymbolicLink(target))
            throw new IOException("Blender integration directory missing or unsafe");
        Path tmp=target.resolveSibling(target.getFileName().toString()+".tmp");
        if(Files.isSymbolicLink(tmp))throw new IOException("Unsafe render command temp link");
        Files.deleteIfExists(tmp);
        try {
            Files.write(tmp,encode(sequence).getBytes(StandardCharsets.US_ASCII),
                    StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            try {
                Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch(AtomicMoveNotSupportedException ignored) {
                Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {Files.deleteIfExists(tmp);}
    }
    private BlenderRenderExitSignal(){}
}

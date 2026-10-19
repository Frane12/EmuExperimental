package com.drnas.console;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Strict, bounded app-private Blender smooth zoom state. */
final class BlenderSmoothZoomSignal {
    static Path path(File winePrefix) {
        return winePrefix.toPath().resolve("drive_c/DRNAS/Apps/")
                .resolve(BundledBlenderArchive.DIRECTORY)
                .resolve("drnas-smooth-zoom.signal");
    }
    static String encode(int sequence,int cumulativeDelta) {
        if(sequence<1||sequence>9999999)throw new IllegalArgumentException("Bad sequence");
        if(Math.abs((long)cumulativeDelta)>1000000L)
            throw new IllegalArgumentException("Out-of-range zoom delta");
        return "r60:"+sequence+":"+cumulativeDelta;
    }
    static void clear(Path target) throws IOException {
        Files.deleteIfExists(target);
    }
    static void write(Path target,int sequence,int cumulativeDelta) throws IOException {
        Path parent=target.getParent();
        if(parent==null||!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Blender folder not installed yet");
        Path temp=target.resolveSibling(target.getFileName().toString()+".tmp");
        Files.deleteIfExists(temp);
        byte[] bytes=encode(sequence,cumulativeDelta).getBytes(StandardCharsets.US_ASCII);
        try {
            try(java.io.OutputStream out=Files.newOutputStream(temp,
                    StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                out.write(bytes);
            }
            try {
                Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch(AtomicMoveNotSupportedException ignored) {
                Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {Files.deleteIfExists(temp);}
    }
    private BlenderSmoothZoomSignal(){}
}

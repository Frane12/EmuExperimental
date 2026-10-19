package com.drnas.console;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Blender-to-Android fullscreen command. No X11 geometry or Wine calls. */
final class BlenderFullscreenSignal {
    private static final String SIGNAL="drnas-host-fullscreen.signal";
    static Path path(File winePrefix) {
        return winePrefix.toPath().resolve("drive_c/DRNAS/Apps/")
                .resolve(BundledBlenderArchive.DIRECTORY).resolve(SIGNAL);
    }
    static void clearStale(Path path) throws IOException {
        // Deleting a symlink removes only the link; never follow arbitrary targets.
        Files.deleteIfExists(path);
    }
    static int readSequence(Path path) throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return 0;
        if(Files.size(path)>32)throw new IOException("Fullscreen command too long");
        String raw=new String(Files.readAllBytes(path),StandardCharsets.US_ASCII).trim();
        if(!raw.matches("r57:[1-9][0-9]{0,6}"))
            throw new IOException("Invalid Blender fullscreen command");
        return Integer.parseInt(raw.substring(4));
    }
    private BlenderFullscreenSignal(){}
}

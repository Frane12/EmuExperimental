package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class OpenGlAlternativeTest {
    @Test public void incompleteWineEglPairMustBeReportedBlocked() throws Exception {
        java.nio.file.Path folder=Files.createTempDirectory("drnas-egl-");
        try {
            Files.createDirectory(folder.resolve("lib"));
            List<String> messages=new ArrayList<>();
            OpenGlAlternative.reportWineEglCandidates(folder.toFile(),messages::add);
            assertTrue(messages.stream().anyMatch(x->x.contains("EGL=MISSING")));
            assertTrue(messages.stream().anyMatch(x->x.contains("DRNAS_WINE_EGL_BLOCKED")));
            Files.write(folder.resolve("lib/libEGL.so.1"),new byte[]{1});
            messages.clear();
            OpenGlAlternative.reportWineEglCandidates(folder.toFile(),messages::add);
            assertTrue(messages.stream().anyMatch(x->x.contains("desktopOpenGL=MISSING")));
            assertTrue(messages.stream().anyMatch(x->x.contains("DRNAS_WINE_EGL_BLOCKED")));
            Files.write(folder.resolve("lib/libOpenGL.so.0"),new byte[]{1});
            messages.clear();
            OpenGlAlternative.reportWineEglCandidates(folder.toFile(),messages::add);
            assertTrue(messages.stream().anyMatch(x->x.contains("DRNAS_WINE_EGL_UNVERIFIED")));
        } finally {
            try(java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(folder)){
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(x->{
                    try{Files.deleteIfExists(x);}catch(Exception ignored){}
                });
            }
        }
    }
}

package com.drnas.console;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameLogSnapshotTest {
    @Test public void keepsCrashTailAndDoesNotFollowSymlinksOrCopyOtherDocuments() throws Exception {
        Path root=Files.createTempDirectory("game-logs");
        try {
            Path game=root.resolve("windows-drives/shared/Crysis");Files.createDirectories(game);
            try(RandomAccessFile f=new RandomAccessFile(game.resolve("Game.log").toFile(),"rw")) {
                f.setLength(3L*1024*1024);f.seek(f.length());f.write("FINAL CRASH REASON".getBytes(StandardCharsets.UTF_8));
            }
            Files.write(root.resolve("private.txt"),"PRIVATE CONTENT".getBytes(StandardCharsets.UTF_8));
            Files.createSymbolicLink(game.resolve("error.log"),root.resolve("private.txt"));
            Files.write(game.resolve("notes.txt"),"UNRELATED NOTES".getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream out=new ByteArrayOutputStream();GameLogSnapshot.write(root.toFile(),out);
            String text=new String(out.toByteArray(),StandardCharsets.UTF_8);
            assertTrue(text.contains("FINAL CRASH REASON"));assertTrue(text.contains("retaining crash tail"));
            assertFalse(text.contains("PRIVATE CONTENT"));assertFalse(text.contains("UNRELATED NOTES"));
            assertTrue(out.size()<2*1024*1024+1024);
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(root)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ignored){}});
            }
        }
    }
}

package com.drnas.console;

import org.junit.Test;
import java.nio.file.*;
import static org.junit.Assert.*;

public class AndroidCrashDiagnosticsTest {
    @Test public void storesOriginalCauseAndThreadSeparatelyFromWineLog() throws Exception {
        Path logs=Files.createTempDirectory("crash-report");
        try {
            Path wine=logs.resolve("latest-wine-gui-raw.txt");Files.write(wine,"Wine started".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            RuntimeException cause=new RuntimeException("bitmap already recycled");
            AndroidCrashDiagnostics.record(logs.toFile(),"last-renderer-error.txt",new Thread("Android UI"),cause);
            String text=new String(Files.readAllBytes(logs.resolve("last-renderer-error.txt")),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.contains("Android UI"));assertTrue(text.contains("bitmap already recycled"));
            assertTrue(text.contains("AndroidCrashDiagnosticsTest"));
            Files.write(wine,"New Wine session".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(text,new String(Files.readAllBytes(logs.resolve("last-renderer-error.txt")),java.nio.charset.StandardCharsets.UTF_8));
        } finally {try(java.util.stream.Stream<Path> walk=Files.walk(logs)){for(Path p:(Iterable<Path>)walk.sorted(java.util.Comparator.reverseOrder())::iterator)Files.delete(p);}}
    }
    @Test public void boundsPersistedReportAndCommitsWithoutPartialFiles() throws Exception {
        Path logs=Files.createTempDirectory("crash-bounded");
        try {
            AndroidCrashDiagnostics.record(logs.toFile(),"last-android-crash.txt",Thread.currentThread(),new RuntimeException("x".repeat(300000)));
            assertTrue(Files.size(logs.resolve("last-android-crash.txt"))<=256*1024);
            try(java.util.stream.Stream<Path> files=Files.list(logs)){assertEquals(1,files.count());}
        }finally{try(java.util.stream.Stream<Path> walk=Files.walk(logs)){for(Path p:(Iterable<Path>)walk.sorted(java.util.Comparator.reverseOrder())::iterator)Files.delete(p);}}
    }
}

package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class InitializationLogExportTest {
    @Test public void includesBootFailureAcrossRestartAndRawTrace() throws Exception {
        Path root=Files.createTempDirectory("drnas-support-");
        try{
            Path logs=Files.createDirectories(root.resolve("logs"));
            Path old=Files.write(logs.resolve("DRNAS-console-old.txt"),"BOOT BLOCKED: test failure".getBytes(StandardCharsets.UTF_8));
            old.toFile().setLastModified(1000);
            Path now=Files.write(logs.resolve("DRNAS-console-now.txt"),"new app start".getBytes(StandardCharsets.UTF_8));
            now.toFile().setLastModified(2000);
            Files.write(logs.resolve(RawWineTrace.LATEST),"Wine raw failure".getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            AndroidCrashDiagnostics.appendInitializationLogs(root.toFile(),output);
            String text=output.toString("UTF-8");
            assertTrue(text.contains("BOOT BLOCKED: test failure"));
            assertTrue(text.contains("new app start"));
            assertTrue(text.contains("Wine raw failure"));
        }finally{
            try(java.util.stream.Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(p);
            }
        }
    }
}

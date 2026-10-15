package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class DiagnosticLogBundleTest {
    @Test public void snapshotsLiveAndClosedGuiTracesWithBootAndDxvkEvidence() throws Exception {
        Path root=Files.createTempDirectory("drnas-log-bundle");
        try {
            File logs=root.resolve("logs").toFile();
            try(RawWineTrace raw=new RawWineTrace(logs,"latest-wine-gui-raw.txt")) {
                raw.write("GUI","Desktop stopped: test failure");
                Files.write(new File(logs,RawWineTrace.LATEST).toPath(),"wineboot evidence".getBytes(StandardCharsets.UTF_8));
                Path dx=root.resolve("containers/default/gui-tmp");Files.createDirectories(dx);
                Files.write(dx.resolve("Crysis_d3d9.log"),"DXVK adapter evidence".getBytes(StandardCharsets.UTF_8));
                ByteArrayOutputStream first=new ByteArrayOutputStream();
                DiagnosticLogBundle.write(root.toFile(),raw,first);
                String text=new String(first.toByteArray(),StandardCharsets.UTF_8);
                assertTrue(text.contains("Desktop stopped: test failure"));
                assertTrue(text.contains("wineboot evidence"));
                assertTrue(text.contains("DXVK adapter evidence"));
                raw.close();
                ByteArrayOutputStream closed=new ByteArrayOutputStream();
                DiagnosticLogBundle.write(root.toFile(),raw,closed);
                assertArrayEquals(first.toByteArray(),closed.toByteArray());
            }
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(root)){
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception ignored){}});
            }
        }
    }
}

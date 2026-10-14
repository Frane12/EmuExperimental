package com.drnas.console;

import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RawWineTraceTest {
    @Test public void preservesEveryLineBeyond80LineUiFilter() throws Exception {
        Path dir=Files.createTempDirectory("drnas-raw-trace");
        try {
            try(RawWineTrace trace=new RawWineTrace(dir.toFile())) {
                for(int i=0;i<25000;i++)trace.write("wineboot",
                        "002c:trace:module:line-"+i+" wineboot.exe");
                assertFalse(trace.wasTruncated());
                assertTrue(trace.size()>750000);
            }
            File result=dir.resolve(RawWineTrace.LATEST).toFile();
            String text=new String(Files.readAllBytes(result.toPath()),StandardCharsets.UTF_8);
            assertTrue(text.contains("line-0"));
            assertTrue(text.contains("line-24999"));
            assertTrue(text.contains("RAW Wine trace started"));
        } finally {
            WineBootProbe.removeWithoutFollowingLinks(dir.toFile());
        }
    }
    @Test public void boundsDiskUsageAndMarksTruncation() throws Exception {
        Path dir=Files.createTempDirectory("drnas-raw-capped");
        try {
            try(RawWineTrace trace=new RawWineTrace(dir.toFile())) {
                String payload="x".repeat(8192);
                for(int i=0;i<4000;i++)trace.write("server",payload);
                assertTrue(trace.wasTruncated());
                assertTrue(trace.size()<=RawWineTrace.MAX_BYTES+1024);
                assertTrue(trace.issue()==null);
            }
            byte[] bytes=Files.readAllBytes(dir.resolve(RawWineTrace.LATEST));
            assertTrue(new String(bytes,Math.max(0,bytes.length-180),Math.min(180,bytes.length),
                    StandardCharsets.UTF_8).contains("LIMIT"));
        } finally {
            WineBootProbe.removeWithoutFollowingLinks(dir.toFile());
        }
    }
}

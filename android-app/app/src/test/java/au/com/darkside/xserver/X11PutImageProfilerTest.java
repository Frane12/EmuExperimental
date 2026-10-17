package au.com.darkside.xserver;

import org.junit.Test;
import static org.junit.Assert.*;

public class X11PutImageProfilerTest {
    @Test public void noLogUntilFixedBoundedBatch() {
        X11PutImageProfiler profiler=new X11PutImageProfiler();
        String summary=null;
        for(int i=0;i<256;i++) {
            String response=profiler.record(400000,950000,1500000,1280*707*4,1_000_000_000L+i*25_000_000L);
            if(i<255)assertNull(response);
            else summary=response;
        }
        assertNotNull(summary);
        assertTrue(summary.contains("count=256"));
        assertTrue(summary.contains("read_ms(p50/p95)"));
        assertTrue(summary.contains("decode_ms(p50/p95)"));
        assertTrue(summary.contains("upload_ms(p50/p95)"));
        assertTrue(summary.contains("rateMiBps="));
        assertNull(profiler.record(0,0,0,64,8_000_000_000L));
    }
    @Test public void bucketsBoundExtremeDelays() {
        assertEquals(0,X11PutImageProfiler.bucket(-1L));
        assertEquals(0,X11PutImageProfiler.bucket(1L));
        assertEquals(16,X11PutImageProfiler.bucket(300_000_000L));
    }
}

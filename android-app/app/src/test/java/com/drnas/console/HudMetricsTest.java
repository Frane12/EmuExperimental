package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
public class HudMetricsTest {
 @Test public void cpuDataAndDeltaMath() {
   long[] s=HudMetrics.parseCpuStat("cpu 100 10 40 700 20 5 0 0 0");
   assertNotNull(s);
   assertEquals(875,s[0]);
   assertEquals(720,s[1]);
   assertEquals("50%",HudMetrics.number(150,100,200,100));
   assertEquals("--",HudMetrics.number(150,100,100,100));
 }
}

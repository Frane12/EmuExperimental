package com.drnas.console;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class FexPerformanceProfileTest {
    @Test public void switchesInheritedCompatibilityProfileWithoutChangingDriver() {
        Map<String,String> env = new HashMap<>();
        env.put("FEX_TSOENABLED", "1");
        env.put("FEX_MULTIBLOCK", "0");
        env.put("TU_AUTOTUNE_ALGO", "profiled");
        FexPerformanceProfile.apply(env);
        assertEquals("0", env.get("FEX_TSOENABLED"));
        assertEquals("0", env.get("FEX_VECTORTSOENABLED"));
        assertEquals("0", env.get("FEX_MEMCPYSETTSOENABLED"));
        assertEquals("0", env.get("FEX_HALFBARRIERTSOENABLED"));
        assertEquals("1", env.get("FEX_X87REDUCEDPRECISION"));
        assertEquals("1", env.get("FEX_MULTIBLOCK"));
        assertEquals("profiled", env.get("TU_AUTOTUNE_ALGO"));
    }
}

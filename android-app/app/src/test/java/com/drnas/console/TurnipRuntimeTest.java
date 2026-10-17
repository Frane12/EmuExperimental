package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TurnipRuntimeTest {
    @Test public void profiledAutotunerSelectedWithoutForcingGpuMemoryMode() {
        java.util.Map<String,String> env=new java.util.HashMap<>();
        java.util.List<String> logs=new java.util.ArrayList<>();
        TurnipRuntime.enableProfiledAutotuner(env,logs::add);
        assertEquals("profiled",env.get("TU_AUTOTUNE_ALGO"));
        assertFalse(env.containsKey("TU_FRANE_GMEM_MODE"));
        assertFalse(env.containsKey("TU_DEBUG"));
        assertTrue(logs.get(0).contains("TU_AUTOTUNE_ALGO=profiled"));
    }
    @Test public void deviceStringMatchesExactGpuFamily() {
        assertEquals("A810",TurnipRuntime.gpuTarget("Adreno (TM) 810"));
        assertEquals("A830",TurnipRuntime.gpuTarget("QUALCOMM Adreno (TM) 830"));
        assertEquals("A840",TurnipRuntime.gpuTarget("Adreno 840"));
        assertEquals("A740",TurnipRuntime.gpuTarget("Adreno 740"));
        assertEquals("",TurnipRuntime.gpuTarget("Adreno 7400"));
        assertEquals("",TurnipRuntime.gpuTarget("Adreno 730"));
        assertEquals("",TurnipRuntime.gpuTarget("Adreno 8400"));
        assertEquals("",TurnipRuntime.gpuTarget(""));
        assertEquals("",TurnipRuntime.gpuTarget(null));
    }
}

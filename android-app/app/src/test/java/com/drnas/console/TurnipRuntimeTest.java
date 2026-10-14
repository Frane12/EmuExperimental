package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TurnipRuntimeTest {
    @Test public void deviceStringMatchesExactGpuFamily() {
        assertEquals("A810",TurnipRuntime.gpuTarget("Adreno (TM) 810"));
        assertEquals("A830",TurnipRuntime.gpuTarget("QUALCOMM Adreno (TM) 830"));
        assertEquals("A840",TurnipRuntime.gpuTarget("Adreno 840"));
        assertEquals("",TurnipRuntime.gpuTarget("Adreno 740"));
        assertEquals("",TurnipRuntime.gpuTarget("Adreno 8400"));
        assertEquals("",TurnipRuntime.gpuTarget(""));
        assertEquals("",TurnipRuntime.gpuTarget(null));
    }
}

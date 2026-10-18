package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
public final class TurnipDevicePolicyTest {
    @Test public void upstreamFamilyNeverMatchesMaliOrA8xx() {
        for(String gpu:new String[]{"610","618","619","630","640","650","660","680","690","710","720","722","730","740","750"}) {
            String target=TurnipDevicePolicy.target("Qualcomm Adreno (TM) "+gpu);
            assertEquals("A"+gpu,target);
            assertTrue(TurnipDevicePolicy.matches(TurnipDevicePolicy.LEGACY,target));
        }
        for(String name:new String[]{"Mali-G52","Adreno 7400","Adreno 613","Adreno 840","Adreno 830","Adreno 810","PowerVR 740"})
            assertFalse(TurnipDevicePolicy.matches(TurnipDevicePolicy.LEGACY,TurnipDevicePolicy.target(name)));
    }
}

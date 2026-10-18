package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MaliPanvkRuntimeTest {
    @Test public void acceptsObservedRedmi13ModelWhenVulkanDoesNotEnumerateGpu() {
        assertTrue(MaliPanvkRuntime.isTargetGpu("24040RN64Y", ""));
    }
    @Test public void acceptsRecognizedMaliG52Device() {
        assertTrue(MaliPanvkRuntime.isTargetGpu("unknown", "ARM Mali-G52 MC2"));
    }
    @Test public void neverOffersMaliDriverToAdrenoOrUnidentifiedDevice() {
        assertFalse(MaliPanvkRuntime.isTargetGpu("Poco F8 Ultra","Adreno 840"));
        assertFalse(MaliPanvkRuntime.isTargetGpu("mystery",""));
    }
}

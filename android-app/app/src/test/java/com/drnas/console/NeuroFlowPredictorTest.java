package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NeuroFlowPredictorTest {
    @Test public void emptyOrInvalidSamplesAreIgnored() {
        NeuroFlowPredictor n=new NeuroFlowPredictor();
        assertNull(n.observe(0f,0f));
        assertNull(n.observe(Float.NaN,10f));
        assertNull(n.observe(60f,Float.POSITIVE_INFINITY));
        assertNull(n.observe(-2f,10f));
    }
    @Test public void stableDisplaySamplesStayBoundedAndBecomeStable() {
        NeuroFlowPredictor n=new NeuroFlowPredictor();
        NeuroFlowPredictor.Result last=null;
        for(int i=0;i<18;i++)last=n.observe(60f,16.7f);
        assertNotNull(last);
        assertEquals(16,last.count);
        assertEquals("DISPLAY_STABLE",last.regime);
        assertEquals(16.7,last.predictedMs,0.02);
        assertEquals(0.0,last.absoluteErrorMs,0.03);
    }
    @Test public void pressureDetectionDoesNotChangeAnyDriver() {
        NeuroFlowPredictor n=new NeuroFlowPredictor();
        NeuroFlowPredictor.Result r=null;
        for(int i=0;i<8;i++)r=n.observe(20f,50f);
        assertEquals("DISPLAY_PRESSURE",r.regime);
        assertTrue(Double.isFinite(r.predictedMs));
        assertTrue(r.predictedMs<=250);
    }
    @Test public void warmupDoesNotClaimModelConfidence() {
        NeuroFlowPredictor n=new NeuroFlowPredictor();
        assertEquals("WARMUP",n.observe(45f,22f).regime);
    }
}

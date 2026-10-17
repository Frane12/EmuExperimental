package com.drnas.console;
import java.util.HashMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class DxvkFrameCapTest {
    @Test public void zeroMeansNoDxvkLimiter() {
        HashMap<String,String> env=new HashMap<>();
        DxvkFrameCap.configure(env,0);
        assertEquals("0",env.get("DXVK_FRAME_RATE"));
        assertTrue(env.get("DXVK_CONFIG").contains("d3d9.maxFrameRate = 0"));
        assertTrue(env.get("DXVK_CONFIG").contains("dxgi.maxFrameRate = 0"));
    }
    @Test public void capWorksForBothD3dApisAndKeepsPriorSettings() {
        HashMap<String,String> env=new HashMap<>();
        env.put("DXVK_CONFIG","dxgi.syncInterval = 1");
        DxvkFrameCap.configure(env,40);
        assertEquals("40",env.get("DXVK_FRAME_RATE"));
        assertTrue(env.get("DXVK_CONFIG").contains("dxgi.syncInterval = 1"));
        assertTrue(env.get("DXVK_CONFIG").contains("dxgi.maxFrameRate = 40"));
        assertEquals(240,DxvkFrameCap.clamp(9999));
        assertEquals(0,DxvkFrameCap.clamp(-1));
    }
}

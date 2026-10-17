package com.drnas.console;

import java.util.Map;

/** DXVK 1.x frame cap, applied to Wine's inherited environment BEFORE start.
 * This limits DXVK Present, not Android Canvas redraws. 0 = no DXVK cap.
 */
final class DxvkFrameCap {
    static int clamp(int frames) {
        return Math.max(0,Math.min(240,frames));
    }
    static void configure(Map<String,String> env,int requested) {
        int cap=clamp(requested);
        // 1.10.3 supports this env variable. Zero disables DXVK's rate cap.
        env.put("DXVK_FRAME_RATE",Integer.toString(cap));
        // Also pass per-D3D API config keys. Avoid overwriting existing DXVK
        // configuration such as the cube's presentInterval settings.
        String limiter="dxgi.maxFrameRate = "+cap+"; d3d9.maxFrameRate = "+cap;
        String previous=env.get("DXVK_CONFIG");
        env.put("DXVK_CONFIG",previous==null||previous.trim().isEmpty()?
                limiter:previous.trim()+"; "+limiter);
    }
    private DxvkFrameCap(){}
}

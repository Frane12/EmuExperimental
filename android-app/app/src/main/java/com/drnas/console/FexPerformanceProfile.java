package com.drnas.console;

import java.util.Map;

/** Bannerlator-compatible Performance profile, applied after prerequisite setup. */
final class FexPerformanceProfile {
    static void apply(Map<String, String> env) {
        env.put("FEX_TSOENABLED", "0");
        env.put("FEX_VECTORTSOENABLED", "0");
        env.put("FEX_MEMCPYSETTSOENABLED", "0");
        env.put("FEX_HALFBARRIERTSOENABLED", "0");
        env.put("FEX_X87REDUCEDPRECISION", "1");
        env.put("FEX_MULTIBLOCK", "1");
    }
    private FexPerformanceProfile() {}
}

package com.drnas.console;

/**
 * The first CPU Assist experiment is deliberately CPU-only.
 *
 * No Qualcomm binaries are shipped. No DSP user-PD, FastRPC remote
 * invocation, Hexagon kernel or NPU backend is active in v0.1.
 *
 * The probe and benchmark are diagnostics; invoke baseline() off the UI
 * thread and never inside a Wine/game real-time path.
 */
public final class CpuAssist {
    public static String probe() {
        if (!NativeHost.available()) return "CPU Assist native host unavailable: " + NativeHost.loadError();
        return NativeHost.acceleratorProbe();
    }
    public static String baseline() {
        if (!NativeHost.available()) return "CPU Assist native host unavailable: " + NativeHost.loadError();
        return NativeHost.cpuAssistBaseline();
    }
    private CpuAssist() {}
}

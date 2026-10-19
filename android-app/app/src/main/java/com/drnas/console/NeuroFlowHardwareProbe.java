package com.drnas.console;

/**
 * Informational NNAPI enumeration only, explicitly NOT proof that QNN,
 * NeuroPilot, LiteRT or any NPU inference successfully ran. Modern Android
 * may hide NNAPI drivers; "none" never means physical NPU hardware absent.
 */
final class NeuroFlowHardwareProbe {
    private static native String queryNnapiDevices();
    static String describe() {
        try {
            System.loadLibrary("drnas_npu_probe");
            String devices=queryNnapiDevices();
            return devices==null?"NNAPI probe returned null":devices;
        } catch(LinkageError|SecurityException failure) {
            return "NNAPI probe unavailable ("+failure.getClass().getSimpleName()+")";
        }
    }
    private NeuroFlowHardwareProbe() {}
}

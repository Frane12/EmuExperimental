package com.drnas.console;

/** Small bounded input evidence must survive quiet-mode log filtering. */
final class InputDiagnosticPolicy {
    static boolean keep(String line) {
        return line.startsWith("DRNAS_X11_INPUT_") ||
                line.startsWith("DRNAS_X11_BUTTON ") ||
                line.startsWith("DRNAS_X11_LIFECYCLE ");
    }
    private InputDiagnosticPolicy() {}
}

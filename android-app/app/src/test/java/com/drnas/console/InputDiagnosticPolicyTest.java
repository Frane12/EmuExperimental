package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputDiagnosticPolicyTest {
    @Test public void inputEvidenceSurvivesQuietMode() {
        String[] evidence={"DRNAS_X11_INPUT_QUEUED seq=1", "DRNAS_X11_INPUT_DONE seq=1",
                "DRNAS_X11_INPUT_STALL seq=2", "DRNAS_X11_INPUT_EXCEPTION seq=3",
                "DRNAS_X11_INPUT_QUEUE_OVERFLOW pending=192", "DRNAS_X11_BUTTON button=1",
                "DRNAS_X11_LIFECYCLE onPause"};
        for(String line:evidence)assertTrue(line,InputDiagnosticPolicy.keep(line));
    }
    @Test public void routineFrameAndNativeSpamStayOptional() {
        assertFalse(InputDiagnosticPolicy.keep("X11 PutImage drawable=1"));
        assertFalse(InputDiagnosticPolicy.keep("X11 frame audit CanvasDraw=120"));
        assertFalse(InputDiagnosticPolicy.keep("[GUI Wine] trace: unrelated routine message"));
    }
}

package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
public class ExitConfirmationTest {
    @Test public void oneOrHeldPressCannotConfirmExit(){
        ExitConfirmation.Gate gate=new ExitConfirmation.Gate();
        assertFalse(gate.press(100));
        assertTrue(gate.press(700));
        assertFalse(gate.press(800));
    }
    @Test public void lateSecondPressStartsNewPair(){
        ExitConfirmation.Gate gate=new ExitConfirmation.Gate();
        assertFalse(gate.press(100));
        assertFalse(gate.press(2201));
        assertTrue(gate.press(2400));
    }
    @Test public void decliningClearsArmedPair(){
        ExitConfirmation.Gate gate=new ExitConfirmation.Gate();
        assertFalse(gate.press(100));gate.reset();
        assertFalse(gate.press(500));assertTrue(gate.press(600));
    }
}

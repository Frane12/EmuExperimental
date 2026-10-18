package au.com.darkside.xserver;

import org.junit.Test;
import static org.junit.Assert.*;

public class X11MapStateTest {
    @Test public void newlyCreatedUnmappedPopupMustReportIsUnmapped() {
        assertEquals(X11MapState.IS_UNMAPPED,
                X11MapState.of(false,false));
        assertEquals(X11MapState.IS_UNMAPPED,
                X11MapState.of(false,true));
    }

    @Test public void mappedPopupWithVisibleParentMustReportIsViewable() {
        assertEquals(X11MapState.IS_VIEWABLE,
                X11MapState.of(true,true));
    }

    @Test public void mappedChildOfUnmappedAncestorMustReportIsUnviewable() {
        assertEquals(X11MapState.IS_UNVIEWABLE,
                X11MapState.of(true,false));
    }
}

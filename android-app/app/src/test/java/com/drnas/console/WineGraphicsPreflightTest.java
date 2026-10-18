package com.drnas.console;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class WineGraphicsPreflightTest {
    @Test public void libraryAloneCannotSolveMissingGlx() {
        assertEquals("BLOCKED_GLX",WineGraphicsPreflight.classifyOpenGl(true,false));
        assertEquals("BLOCKED_LIBGL_AND_GLX",WineGraphicsPreflight.classifyOpenGl(false,false));
        assertEquals("PREREQUISITES_PRESENT_CONTEXT_NOT_VERIFIED",
                WineGraphicsPreflight.classifyOpenGl(true,true));
    }
    @Test public void nativeVulkanDoesNotClaimOpenGlFixed() {
        List<String> lines=new ArrayList<>();
        WineGraphicsPreflight.report(false,false,true,lines::add);
        assertTrue(lines.stream().anyMatch(s->s.contains("WGL=BLOCKED_LIBGL_AND_GLX")));
        assertTrue(lines.stream().anyMatch(s->s.contains("WineVulkan requested independently")));
    }
}

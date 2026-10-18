package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;

public class AppLaunchSettingsTest {
    @Test public void inheritingProfileUsesCurrentGlobals() {
        AppLaunchSettings.Profile p=AppLaunchSettings.Profile.defaults();
        assertEquals(40,p.fps(40));assertEquals("async",p.dxvk("async"));
        assertTrue(p.stretch(true));assertEquals(7.5f,p.sensitivity(7.5f),0f);
        assertEquals("Far Cry",p.title("Far Cry"));
    }
    @Test public void separateImmutableDraftsDoNotChangeOtherAppsOrDefaults() {
        AppLaunchSettings.Profile base=AppLaunchSettings.Profile.defaults();
        AppLaunchSettings.Profile a=base.withFps(40).withDxvk("async").withStretch(false).withSensitivity(4f);
        AppLaunchSettings.Profile b=base.withFps(60).withDxvk("standard");
        assertEquals(40,a.fps(0));assertEquals(60,b.fps(0));assertEquals(0,base.fps(0));
        assertEquals("async",a.dxvk("standard"));assertEquals("standard",b.dxvk("async"));
        assertFalse(a.stretch(true));assertTrue(base.stretch(true));
        assertEquals(4f,a.sensitivity(9f),0f);assertEquals(9f,b.sensitivity(9f),0f);
    }
    @Test public void profileRoundtripPreservesUnlimitedAndExplicitFit() throws Exception {
        AppLaunchSettings.Profile p=new AppLaunchSettings.Profile("Moja igra",0,"async",0,2.5f);
        AppLaunchSettings.Profile restored=AppLaunchSettings.Profile.parse(p.json());
        assertEquals("Moja igra",restored.title("Original"));assertEquals(0,restored.fps(40));
        assertEquals("async",restored.dxvk("standard"));assertFalse(restored.stretch(true));
        assertEquals(2.5f,restored.sensitivity(8f),0f);
    }
    @Test public void openGlBackendIsPersistedWithoutChangingDefault() throws Exception {
        AppLaunchSettings.Profile p=AppLaunchSettings.Profile.defaults().withGraphicsApi("opengl");
        assertTrue(p.nativeOpenGl());assertFalse(p.nativeVulkan());
        AppLaunchSettings.Profile roundTrip=AppLaunchSettings.Profile.parse(p.json());
        assertEquals("opengl",roundTrip.graphicsApi);
        assertTrue(roundTrip.nativeOpenGl());
        assertFalse(AppLaunchSettings.Profile.defaults().nativeOpenGl());
        assertEquals("auto",AppLaunchSettings.Profile.defaults().graphicsApi);
    }
    @Test public void brokenOrOutOfRangeSettingsStayBounded() {
        AppLaunchSettings.Profile p=AppLaunchSettings.Profile.parse(
                "{\"fps\":9999,\"dxvk\":\"bad\",\"stretch\":99,\"sensitivity\":100}");
        assertEquals(240,p.fps(40));assertEquals("standard",p.dxvk("standard"));
        assertTrue(p.stretch(true));assertEquals(10f,p.sensitivity(7f),0f);
        assertEquals(40,AppLaunchSettings.Profile.parse("broken").fps(40));
        assertEquals(7f,new AppLaunchSettings.Profile("",-1,"default",-1,Float.NaN).sensitivity(7f),0f);
    }
}

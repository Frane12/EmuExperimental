package au.com.darkside.xserver;

import org.junit.Test;
import static org.junit.Assert.*;

public class BlenderPopupPolicyTest {
    @Test public void directLaunchRecordedOnA840CompletesStartup() {
        // r44: managed title=Blender, bounds [0,19][1288,743].
        // Startup detection must not require override_redirect as popup logic does.
        assertTrue(BlenderPopupPolicy.startupPrimary(true,false,"Blender",1288,724,1280,720));
        assertTrue(BlenderPopupPolicy.startupPrimary(true,false,"Blender",960,544,960,544));
        assertFalse(BlenderPopupPolicy.startupPrimary(false,false,"Blender",1288,724,1280,720));
        assertFalse(BlenderPopupPolicy.startupPrimary(true,true,"Blender",1288,724,1280,720));
        assertFalse(BlenderPopupPolicy.startupPrimary(true,false,"Default IME",1,1,1280,720));
        assertFalse(BlenderPopupPolicy.startupPrimary(true,false,"Preferences",668,547,1280,720));
    }
    @Test public void ownerRaiseKeepsPreferencesAboveAndUnrelatedAppAboveBoth() {
        java.util.List<String> order = new java.util.ArrayList<>(
                java.util.Arrays.asList("desktop", "preferences", "main", "other-app"));
        assertTrue(BlenderPopupPolicy.moveAbove(order,"preferences","main"));
        assertEquals(java.util.Arrays.asList("desktop","main","preferences","other-app"), order);
        assertFalse(BlenderPopupPolicy.moveAbove(order,"preferences","main"));
    }
    @Test public void twoDialogsRetainTheirOrderOnOwnerRaise() {
        java.util.List<String> order = new java.util.ArrayList<>(
                java.util.Arrays.asList("preferences", "file-dialog", "main"));
        BlenderPopupPolicy.moveAbove(order,"file-dialog","main");
        BlenderPopupPolicy.moveAbove(order,"preferences","main");
        assertEquals(java.util.Arrays.asList("main","preferences","file-dialog"), order);
        assertFalse(BlenderPopupPolicy.moveAbove(order,"deleted","main"));
    }
    @Test public void recordedPreferencesAndFileDialogQualify() {
        assertTrue(BlenderPopupPolicy.primary("* (Unsaved) - Blender 4.5.14 LTS",1288,728,1280,720));
        assertTrue(BlenderPopupPolicy.auxiliary("Preferences",668,547,1288,728));
        assertTrue(BlenderPopupPolicy.auxiliary("Blender",1068,627,1288,728));
    }
    @Test public void initialTitleRequiresAlmostFullDesktop() {
        assertTrue(BlenderPopupPolicy.primary("Blender",1288,728,1280,720));
        assertFalse(BlenderPopupPolicy.primary("Blender",668,547,1280,720));
        assertFalse(BlenderPopupPolicy.primary("Blender",1068,627,1280,720));
    }
    @Test public void unrelatedAndTinyWindowsNeverQualify() {
        assertFalse(BlenderPopupPolicy.auxiliary("Default IME",1,1,1288,728));
        assertFalse(BlenderPopupPolicy.auxiliary("Preferences",1,1,1288,728));
        assertFalse(BlenderPopupPolicy.auxiliary("Notepad",668,547,1288,728));
        assertFalse(BlenderPopupPolicy.primary("Notepad",1288,728,1280,720));
    }
    @Test public void anotherMainOrSameSizedWindowIsNotAuxiliary() {
        assertFalse(BlenderPopupPolicy.auxiliary("another.blend - Blender 4.5.14 LTS",668,547,1288,728));
        assertFalse(BlenderPopupPolicy.auxiliary("Blender",1288,728,1288,728));
        assertFalse(BlenderPopupPolicy.auxiliary("Preferences",1400,600,1288,728));
    }
}


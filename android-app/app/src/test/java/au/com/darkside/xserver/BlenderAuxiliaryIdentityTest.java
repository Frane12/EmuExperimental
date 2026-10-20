package au.com.darkside.xserver;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlenderAuxiliaryIdentityTest {
    @Test public void renderAndPreferencesCannotBecomeFullscreenPrimary() {
        for(String title:new String[]{"Render Result - Blender 4.5 LTS",
                "Render - Blender 4.5 LTS","Preferences - Blender 4.5 LTS",
                "Blender Preferences"})
            assertFalse(title,BlenderPopupPolicy.primary(title,1280,720,1280,720));
        assertTrue(BlenderPopupPolicy.primary("scene.blend - Blender 4.5 LTS",1280,720,1280,720));
    }
}

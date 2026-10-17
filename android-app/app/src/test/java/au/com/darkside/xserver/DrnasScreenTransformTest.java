package au.com.darkside.xserver;
import org.junit.Test;
import static org.junit.Assert.*;

public class DrnasScreenTransformTest {
    @Test public void widescreenFillUsesEntirePhoneWithSameGuestResolution(){
        DrnasScreenTransform t=DrnasScreenTransform.forSize(2400,1080,1280,720,true);
        assertTrue(t.fill);
        assertEquals(1.875f,t.scaleX,0.0001f);
        assertEquals(1.5f,t.scaleY,0.0001f);
        assertEquals(0f,t.offsetX,0f);
        assertEquals(0f,t.offsetY,0f);
        assertEquals(1280,t.guestW);
        assertEquals(720,t.guestH);
        assertEquals(640,t.guestX(1200));
        assertEquals(360,t.guestY(540));
        assertEquals(1279,t.guestX(2400));
        assertEquals(719,t.guestY(1080));
        assertEquals(40f,t.deltaX(75),.01f);
        assertEquals(40f,t.deltaY(60),.01f);
    }
    @Test public void fitModeKeepsProportionsAndCenteredBlackMargins(){
        DrnasScreenTransform t=DrnasScreenTransform.forSize(2400,1080,1280,720,false);
        assertEquals(1.5f,t.scaleX,.0001f);
        assertEquals(1.5f,t.scaleY,.0001f);
        assertEquals(240f,t.offsetX,.0001f);
        assertEquals(0f,t.offsetY,.0001f);
        assertEquals(0,t.guestX(240));
        assertEquals(1279,t.guestX(2160));
        assertEquals(0,t.guestX(-1));
    }
    @Test public void tallerViewAlsoStretchesVertically(){
        DrnasScreenTransform t=DrnasScreenTransform.forSize(1280,1000,1280,720,true);
        assertEquals(1f,t.scaleX,.0001f);
        assertEquals(1000f/720f,t.scaleY,.0001f);
        assertEquals(500,t.guestY(694.4444f));
    }
}

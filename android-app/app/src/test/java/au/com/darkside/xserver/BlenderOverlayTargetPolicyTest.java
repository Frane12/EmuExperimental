package au.com.darkside.xserver;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlenderOverlayTargetPolicyTest {
    static final class Fake {
        final Object client;final boolean primary,eligible;
        Fake(Object c,boolean p,boolean e){client=c;primary=p;eligible=e;}
    }
    static final BlenderOverlayTargetPolicy.Info<Fake> INFO=
            new BlenderOverlayTargetPolicy.Info<Fake>() {
        public boolean eligible(Fake w){return w.eligible;}
        public boolean primary(Fake w){return w.primary;}
        public Object client(Fake w){return w.client;}
    };
    @Test public void renderWindowAboveMainReceivesOverlayKeys() {
        Object blender=new Object(),other=new Object();
        Fake main=new Fake(blender,true,true),render=new Fake(blender,false,true);
        Fake foreign=new Fake(other,false,true);
        assertSame(render,BlenderOverlayTargetPolicy.choose(Arrays.asList(main,render,foreign),INFO));
    }
    @Test public void closedRenderAndInputOnlyWindowsDoNotStealTarget() {
        Object blender=new Object();
        Fake main=new Fake(blender,true,true),closed=new Fake(blender,false,false);
        assertSame(main,BlenderOverlayTargetPolicy.choose(Arrays.asList(main,closed),INFO));
    }
    @Test public void ambiguousBlenderClientsLeaveExistingFocusAlone() {
        assertNull(BlenderOverlayTargetPolicy.choose(Arrays.asList(
                new Fake(new Object(),true,true),new Fake(new Object(),true,true)),INFO));
    }
    @Test public void noConfirmedMainLeavesExistingFocusAlone() {
        assertNull(BlenderOverlayTargetPolicy.choose(Arrays.asList(new Fake(new Object(),false,true)),INFO));
    }
    @Test public void multipleWindowsFromSameClientAreAllowed() {
        Object blender=new Object();
        Fake first=new Fake(blender,true,true),second=new Fake(blender,true,true);
        assertSame(second,BlenderOverlayTargetPolicy.choose(Arrays.asList(first,second),INFO));
    }
    @Test public void clickingMainDoesNotActivateOverlappingRenderElsewhere() {
        Object client=new Object();
        Fake main=new Fake(client,true,true),render=new Fake(client,false,true);
        assertSame(main,BlenderOverlayTargetPolicy.atPointer(Arrays.asList(main,render),INFO,main));
        assertSame(render,BlenderOverlayTargetPolicy.atPointer(Arrays.asList(main,render),INFO,render));
    }
    @Test public void foreignOrUnavailablePointerTargetDoesNotStealFocus() {
        Object client=new Object();
        Fake main=new Fake(client,true,true),foreign=new Fake(new Object(),false,true);
        Fake hidden=new Fake(client,false,false);
        assertNull(BlenderOverlayTargetPolicy.atPointer(Arrays.asList(main,foreign,hidden),INFO,foreign));
        assertNull(BlenderOverlayTargetPolicy.atPointer(Arrays.asList(main,hidden),INFO,hidden));
        assertNull(BlenderOverlayTargetPolicy.atPointer(Arrays.asList(main),INFO,null));
    }
    @Test public void menuGrabDragAndKeyChordEachPreventActivation() {
        for(int state=0;state<16;state++)
            assertEquals(state==0,BlenderOverlayTargetPolicy.mayActivate(
                (state&1)!=0,(state&2)!=0,(state&4)!=0,(state&8)!=0));
    }
}

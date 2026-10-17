package au.com.darkside.xserver;
import org.junit.Test;
import static org.junit.Assert.*;

public class X11PresentedFrameMaskTest {
  @Test public void neverChangesGamePixelsAndOnlyMasksConfirmedBottomGap(){
    X11PresentedFrameMask m=new X11PresentedFrameMask();
    for(int i=0;i<7;i++)m.report(1280,707,0,0,1280,720,true,1000000000L+i*10000000L);
    assertEquals(0,m.visibleRows(1100000000L));
    m.report(1280,707,0,0,1280,720,true,1100000000L);
    assertEquals(13,m.visibleRows(1200000000L));
    assertEquals(0,m.visibleRows(4500000000L));
    m.clear();
    assertEquals(0,m.visibleRows(1200000000L));
  }
  @Test public void refusesPartialAndMismatchedWidth(){
    X11PresentedFrameMask m=new X11PresentedFrameMask();
    for(int i=0;i<20;i++)m.report(640,707,0,0,1280,720,true,1000000000L+i*1000L);
    assertEquals(0,m.visibleRows(1200000000L));
    for(int i=0;i<20;i++)m.report(1280,707,0,1,1280,720,true,1000000000L+i*1000L);
    assertEquals(0,m.visibleRows(1200000000L));
  }
}

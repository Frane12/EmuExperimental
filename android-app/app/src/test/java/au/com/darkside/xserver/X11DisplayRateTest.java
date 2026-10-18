package au.com.darkside.xserver;
import org.junit.Test;
import static org.junit.Assert.*;
public final class X11DisplayRateTest {
    @Test public void reportsRealDrawCallbacksAndReturnsToZeroWhileIdle() {
        X11DisplayRate rate=new X11DisplayRate();rate.reset(1_000_000_000L);
        for(int i=0;i<30;i++)rate.draw();
        assertEquals(60f,rate.sample(1_500_000_000L),.001f);
        assertEquals(0f,rate.sample(2_000_000_000L),.001f);
        rate.draw();assertEquals(2f,rate.sample(2_500_000_000L),.001f);
    }
    @Test public void pollingDoesNotInventFramesOrDivideByZero() {
        X11DisplayRate rate=new X11DisplayRate();rate.reset(1);
        assertEquals(0f,rate.sample(1),0);
        rate.draw();assertEquals(0f,rate.sample(10),0);
        assertEquals(2f,rate.sample(500_000_001L),.001f);
        rate.reset(900_000_000L);assertEquals(0f,rate.current(),0);
    }
}

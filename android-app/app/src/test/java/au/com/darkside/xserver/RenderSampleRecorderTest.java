package au.com.darkside.xserver;
import org.junit.Test;
import static org.junit.Assert.*;

public class RenderSampleRecorderTest {
    @Test public void fiveSecondSampleIsBoundedAndNeverPretendsDXVKFPS() {
        RenderSampleRecorder r=new RenderSampleRecorder();
        r.start(1_000_000_000L);
        for(int i=0;i<100;i++) {
            r.put(300_000,200_000,450_000,1024,7,1280,707,1280,720);
            r.display(1_000_000_000L+i*50_000_000L);
        }
        String result=r.finish(6_000_000_000L);
        assertTrue(result.contains("Duration: 5.00 s"));
        assertTrue(result.contains("source 1280x707 target 1280x720"));
        assertTrue(result.contains("p99="));
        assertTrue(result.contains("Engine FPS / DXVK Present timestamps: NOT OBSERVED"));
        assertFalse(r.isRecording());
    }
}

package com.drnas.console;

import android.os.Handler;
import android.os.HandlerThread;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Coalesced asynchronous Android UI -> Blender 3D view target updates.
 * File IO never occurs on Android's UI thread or under the XServer lock.
 */
final class BlenderSmoothZoomBridge {
    private final Path signal;
    private final Consumer<String> log;
    private final AtomicInteger serial=new AtomicInteger();
    private final AtomicInteger total=new AtomicInteger();
    private final AtomicBoolean queued=new AtomicBoolean();
    private HandlerThread thread;
    private Handler worker;
    private volatile boolean stopped;
    private boolean announced;
    private int errors;
    BlenderSmoothZoomBridge(Path signal,Consumer<String> log) {
        this.signal=signal;this.log=log;
    }
    void start() {
        if(thread!=null)return;
        thread=new HandlerThread("drnas-smooth-zoom-io");
        thread.start();
        worker=new Handler(thread.getLooper());
        worker.post(()->{
            try {BlenderSmoothZoomSignal.clear(signal);}
            catch(IOException e){log.accept("DRNAS_SMOOTH_ZOOM_CLEAR_ERROR "+e.getMessage());}
        });
    }
    void offerDelta(int delta) {
        if(stopped||delta==0||worker==null)return;
        int change=Math.max(-100,Math.min(100,delta));
        total.updateAndGet(old->Math.max(-1000000,Math.min(1000000,old+change)));
        serial.incrementAndGet();
        schedule();
    }
    private void schedule() {
        if(!stopped&&queued.compareAndSet(false,true))
            worker.postDelayed(this::flush,18);
    }
    private void flush() {
        if(stopped)return;
        int latest=serial.get();
        int cumulative=total.get();
        try {
            BlenderSmoothZoomSignal.write(signal,latest,cumulative);
            if(!announced) {
                announced=true;
                log.accept("DRNAS_SMOOTH_ZOOM_BRIDGE_READY r60 seq="+latest);
            }
        } catch(IOException | SecurityException exception) {
            if(errors++<4)log.accept("DRNAS_SMOOTH_ZOOM_BRIDGE_ERROR "+exception.getMessage());
        } finally {
            queued.set(false);
            if(serial.get()!=latest)schedule();
        }
    }
    void stop() {
        stopped=true;
        HandlerThread t=thread;
        if(t!=null)t.quitSafely();
    }
}

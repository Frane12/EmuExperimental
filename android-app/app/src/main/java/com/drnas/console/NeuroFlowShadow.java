package com.drnas.console;

import android.content.Context;
import android.os.SystemClock;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Experimental read-only NeuroFlow sampler. A single background thread owns
 * the predictor; UI/X11 code only replaces an immutable pointer (nonblocking).
 * NO renderer hooks, shader changes, NPU inference or driver switching.
 */
final class NeuroFlowShadow implements AutoCloseable {
    private static final class Sample {
        final long atMs;
        final float displayedFps,displayedMs;
        Sample(long atMs,float fps,float ms){this.atMs=atMs;displayedFps=fps;displayedMs=ms;}
    }
    private final AtomicReference<Sample> pending=new AtomicReference<>();
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{
        Thread worker=new Thread(r,"drnas-neuroflow-shadow");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        return worker;
    });
    private final NeuroFlowPredictor predictor=new NeuroFlowPredictor();
    private final Consumer<String> log;
    private volatile boolean closed;
    private long lastObservedMs,lastReportMs;

    NeuroFlowShadow(Context context,Consumer<String> log) {
        this.log=log;
        executor.execute(()->{
            if(closed)return;
            log.accept("DRNAS_NPU_SHADOW_START mode=read-only predictor=CPU-heuristic source=Android/X11-display (NOT Blender render FPS)");
            log.accept("DRNAS_NPU_SHADOW_HARDWARE "+NeuroFlowHardwareProbe.describe()+
                    " (NNAPI enumeration only; LiteRT/QNN inference NOT running)");
        });
        executor.scheduleWithFixedDelay(this::tick,2,2,TimeUnit.SECONDS);
    }
    /** Called from UI at 650 ms intervals. Never waits on background work. */
    void offer(float displayedFps,float displayedMs) {
        if(!closed && NeuroFlowPredictor.valid(displayedFps,displayedMs))
            pending.lazySet(new Sample(SystemClock.elapsedRealtime(),displayedFps,displayedMs));
    }
    private void tick() {
        if(closed)return;
        try {
            Sample sample=pending.getAndSet(null);
            if(sample==null || sample.atMs<=lastObservedMs || 
                    SystemClock.elapsedRealtime()-sample.atMs>5000)return;
            lastObservedMs=sample.atMs;
            long started=System.nanoTime();
            NeuroFlowPredictor.Result result=predictor.observe(sample.displayedFps,sample.displayedMs);
            long micros=(System.nanoTime()-started)/1000;
            if(result!=null && (lastReportMs==0||sample.atMs-lastReportMs>=15000)) {
                lastReportMs=sample.atMs;
                log.accept(String.format(Locale.ROOT,
                    "DRNAS_NPU_SHADOW_DISPLAY samples=%d ui_fps=%.1f mean_ms=%.2f "+
                    "forecast_ms=%.2f error_ewma_ms=%.2f regime=%s cpu_inference_us=%d "+
                    "npu_inference=NOT_CONFIGURED action=NONE",
                    result.count,sample.displayedFps,result.observedMs,result.predictedMs,
                    result.absoluteErrorMs,result.regime,micros));
            }
        } catch(RuntimeException failure) {
            // An experimental observer must never take down Wine/Blender.
            log.accept("DRNAS_NPU_SHADOW_ERROR "+failure.getClass().getSimpleName());
        }
    }
    @Override public void close() {
        closed=true;
        pending.set(null);
        executor.shutdownNow(); // never block the UI thread on shutdown
    }
}

package com.drnas.console;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded best-effort diagnostics. Producers never wait for disk or log export. */
final class AsyncDiagnosticQueue implements AutoCloseable {
    private final AtomicInteger dropped=new AtomicInteger();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,
            TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(128),task->{
                Thread thread=new Thread(task,"drnas-diagnostic-io");
                thread.setDaemon(true);return thread;
            },new ThreadPoolExecutor.AbortPolicy());
    void submit(Runnable task) {
        try {worker.execute(task);}
        catch(RejectedExecutionException full) {dropped.incrementAndGet();}
    }
    int droppedCount(){return dropped.get();}
    @Override public void close() {
        worker.shutdown();
        try {
            if(!worker.awaitTermination(2,TimeUnit.SECONDS))worker.shutdownNow();
        } catch(InterruptedException interrupted) {
            worker.shutdownNow();Thread.currentThread().interrupt();
        }
    }
}

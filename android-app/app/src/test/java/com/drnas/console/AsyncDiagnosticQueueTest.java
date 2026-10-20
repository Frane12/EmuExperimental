package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncDiagnosticQueueTest {
    @Test public void blockedSinkDoesNotBlockOrGrowProducerQueue() throws Exception {
        AsyncDiagnosticQueue queue=new AsyncDiagnosticQueue();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        queue.submit(()->{
            entered.countDown();
            try {release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
        });
        assertTrue(entered.await(2,TimeUnit.SECONDS));
        try {
            for(int i=0;i<500;i++)queue.submit(()->{});
            assertEquals(372,queue.droppedCount());
        } finally {release.countDown();queue.close();}
    }
    @Test public void closeDrainsAcceptedMessagesInOrder() {
        AsyncDiagnosticQueue queue=new AsyncDiagnosticQueue();
        AtomicInteger sequence=new AtomicInteger(),errors=new AtomicInteger();
        for(int i=0;i<40;i++) {
            final int expected=i;
            queue.submit(()->{if(sequence.getAndIncrement()!=expected)errors.incrementAndGet();});
        }
        queue.close();
        assertEquals(40,sequence.get());assertEquals(0,errors.get());
        assertEquals(0,queue.droppedCount());
    }
}

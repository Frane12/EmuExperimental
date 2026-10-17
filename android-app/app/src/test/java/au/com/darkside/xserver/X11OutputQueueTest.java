package au.com.darkside.xserver;

import org.junit.Test;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class X11OutputQueueTest {
    @Test public void bufferedClickAndMotionFlushOffCallerThreadInWireOrder() throws Exception {
        Thread caller=Thread.currentThread();ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Set<Thread> writers=Collections.synchronizedSet(new HashSet<>());
        CountDownLatch received=new CountDownLatch(1);
        OutputStream target=new OutputStream(){
            @Override public void write(int b){throw new AssertionError();}
            @Override public synchronized void write(byte[] b,int off,int n){
                assertNotSame("Socket I/O must not run on the input/UI thread",caller,Thread.currentThread());
                writers.add(Thread.currentThread());bytes.write(b,off,n);
                if(bytes.size()==64)received.countDown();
            }
        };
        X11OutputQueue queue=new X11OutputQueue(target,()->{});
        try {
            BufferedOutputStream buffered=new BufferedOutputStream(queue,16);
            byte[] event=new byte[32];Arrays.fill(event,(byte)6); // motion
            buffered.write(event);buffered.flush();
            Arrays.fill(event,(byte)4); // button, reuse the same caller buffer
            buffered.write(event);buffered.flush();Arrays.fill(event,(byte)99);
            assertTrue(received.await(2,TimeUnit.SECONDS));
            byte[] expected=new byte[64];Arrays.fill(expected,0,32,(byte)6);Arrays.fill(expected,32,64,(byte)4);
            assertArrayEquals(expected,bytes.toByteArray());assertEquals(1,writers.size());
        } finally {queue.close();}
    }

    @Test public void slowPeerDoesNotBlockFlushAndBacklogDisconnectsInsteadOfDroppingBytes() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicBoolean aborted=new AtomicBoolean();
        OutputStream slow=new OutputStream(){
            @Override public void write(int b){}
            @Override public void write(byte[] b,int off,int n)throws IOException {
                entered.countDown();try{release.await();}catch(InterruptedException stopped){throw new IOException(stopped);}
            }
        };
        X11OutputQueue queue=new X11OutputQueue(slow,()->{aborted.set(true);release.countDown();},8,4);
        try {
            queue.write(new byte[4]);assertTrue(entered.await(2,TimeUnit.SECONDS));
            // The writer is blocked, but enqueue/flush still return to input.
            queue.write(new byte[4]);queue.flush();
            try {queue.write(1);fail("Unbounded backlog accepted");}catch(IOException expected){}
            assertTrue(aborted.get());
            try {queue.flush();fail("Closed/failed connection accepted");}catch(IOException expected){}
        } finally {queue.close();release.countDown();}
    }

    @Test public void closeCancelsBlockedWriterWithoutJoiningIt()throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        OutputStream slow=new OutputStream(){
            @Override public void write(int b){}
            @Override public void write(byte[] b,int off,int n)throws IOException {
                entered.countDown();try{release.await();}catch(InterruptedException e){throw new IOException(e);}finally{finished.countDown();}
            }
        };
        X11OutputQueue queue=new X11OutputQueue(slow,release::countDown);
        queue.write(1);assertTrue(entered.await(2,TimeUnit.SECONDS));queue.close();
        assertTrue(finished.await(2,TimeUnit.SECONDS));queue.close();
    }
}

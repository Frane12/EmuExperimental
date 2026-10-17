package au.com.darkside.xserver;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

/** One ordered socket writer per X11 connection. Producers never perform I/O. */
final class X11OutputQueue extends OutputStream {
    private final OutputStream target;
    private final Runnable abortTransport;
    private final ArrayBlockingQueue<byte[]> queue;
    private final int byteLimit;
    private final Object state=new Object();
    private final Thread writer;
    private int pendingBytes;
    private boolean closed;
    private IOException failure;

    X11OutputQueue(OutputStream target,Runnable abortTransport) {
        this(target,abortTransport,8*1024*1024,512);
    }
    X11OutputQueue(OutputStream target,Runnable abortTransport,int byteLimit,int chunks) {
        if(byteLimit<=0 || chunks<=0)throw new IllegalArgumentException("Invalid X11 queue bounds");
        this.target=target;this.abortTransport=abortTransport;this.byteLimit=byteLimit;
        queue=new ArrayBlockingQueue<>(chunks);
        writer=new Thread(this::drain,"drnas-x11-output");
        writer.setDaemon(true);writer.start();
    }

    private void checkOpen() throws IOException {
        if(failure!=null)throw failure;
        if(closed)throw new IOException("X11 output closed");
    }
    @Override public void write(int value) throws IOException {write(new byte[]{(byte)value},0,1);}
    @Override public void write(byte[] bytes,int offset,int length) throws IOException {
        if(bytes==null)throw new NullPointerException("bytes");
        if(offset<0 || length<0 || offset>bytes.length-length)throw new IndexOutOfBoundsException();
        IOException overflow=null;
        synchronized(state) {
            checkOpen();
            if(length==0)return;
            if(length>byteLimit-pendingBytes || queue.remainingCapacity()==0) {
                overflow=new IOException("X11 output backlog exceeded; disconnecting slow client");
                failure=overflow;
            } else {
                // Own the bytes: callers may reuse their 16 KiB buffer as soon
                // as write/flush returns. Never retain a mutable caller array.
                byte[] copy=Arrays.copyOfRange(bytes,offset,offset+length);
                pendingBytes+=length;
                queue.add(copy);
            }
        }
        if(overflow!=null){close();throw overflow;}
    }
    @Override public void flush() throws IOException {
        // BufferedOutputStream has already enqueued its bytes. Delivery runs
        // in order on writer; do not join, wait for a socket, or relax StrictMode.
        synchronized(state){checkOpen();}
    }

    private void drain() {
        try {
            while(true) {
                byte[] bytes=queue.take();
                synchronized(state){if(closed)return;}
                try {target.write(bytes);target.flush();}
                finally {synchronized(state){pendingBytes=Math.max(0,pendingBytes-bytes.length);}}
            }
        } catch(InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } catch(IOException error) {
            synchronized(state){if(!closed)failure=error;}
        } finally {
            close();
            try {target.close();}catch(IOException ignored){}
        }
    }

    @Override public void close() {
        synchronized(state) {
            if(closed)return;
            closed=true;queue.clear();pendingBytes=0;
        }
        // Socket.close cancels a blocked write; no waiting for a peer/UI thread.
        try {abortTransport.run();}catch(RuntimeException ignored){}
        writer.interrupt();
    }
}

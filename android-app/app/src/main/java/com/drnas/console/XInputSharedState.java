package com.drnas.console;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Android producer for the independent DRNAS native XInput PE DLL.
 * Protocol v1, 64-byte little-endian, file-backed MAP_SHARED, no polling
 * thread or network service. Each touch event writes at most one snapshot.
 * Every XInputGetState in the guest is a memory read, not a file operation.
 */
final class XInputSharedState implements AutoCloseable {
    static final int MAGIC=0x49585244; // "DRXI"
    static final int VERSION=1;
    static final int SIZE=64;

    static final int DPAD_UP=0x0001, DPAD_DOWN=0x0002,
            DPAD_LEFT=0x0004, DPAD_RIGHT=0x0008,
            START=0x0010, BACK=0x0020,
            LTHUMB=0x0040, RTHUMB=0x0080,
            LB=0x0100, RB=0x0200,
            A=0x1000, B=0x2000, X=0x4000, Y=0x8000;

    private final MappedByteBuffer buffer;
    private final AtomicInteger writerFence=new AtomicInteger();
    private int sequence;
    private int packet;
    private int previousHash=Integer.MIN_VALUE;
    private boolean virtualEnabled;
    private boolean physicalConnected;
    private boolean closed;

    private int touchLX,touchLY,touchRX,touchRY;
    private int physicalLX,physicalLY,physicalRX,physicalRY;
    private int touchButtons,physicalButtons;
    private int touchLT,touchRT,physicalLT,physicalRT;

    XInputSharedState(File file) throws IOException {
        File parent=file.getParentFile();
        if(parent==null || (!parent.isDirectory() && !parent.mkdirs()))
            throw new IOException("XInput state directory unavailable");
        if(java.nio.file.Files.isSymbolicLink(file.toPath()))
            throw new IOException("XInput state must not be a symlink");
        try(RandomAccessFile random=new RandomAccessFile(file,"rw")) {
            if(random.length()!=SIZE)random.setLength(SIZE);
            buffer=random.getChannel().map(FileChannel.MapMode.READ_WRITE,0,SIZE);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
        }
        synchronized(this) { publish(true); }
    }

    static int axis(float normalized) {
        float v=Math.max(-1f,Math.min(1f,normalized));
        if(Math.abs(v)<0.08f)v=0f;
        return v<0 ? (int)(v*32768f) : (int)(v*32767f);
    }

    synchronized void setVirtualEnabled(boolean enabled) {
        if(closed)return;
        virtualEnabled=enabled;
        if(!enabled)clearTouch();
        publish(false);
    }
    synchronized void setTouchLeft(float x,float y) {
        if(closed)return;
        touchLX=axis(x);
        touchLY=axis(-y);
        publish(false);
    }
    synchronized void setTouchRight(float x,float y) {
        if(closed)return;
        touchRX=axis(x);
        touchRY=axis(-y);
        publish(false);
    }
    synchronized void setTouchButton(int bit,boolean pressed) {
        if(closed)return;
        if(pressed)touchButtons|=bit;else touchButtons&=~bit;
        publish(false);
    }
    synchronized void setTouchTrigger(boolean left,int amount) {
        if(closed)return;
        int v=Math.max(0,Math.min(255,amount));
        if(left)touchLT=v;else touchRT=v;
        publish(false);
    }
    synchronized void setPhysicalAxis(float lx,float ly,float rx,float ry,
                                     float lt,float rt) {
        if(closed)return;
        physicalConnected=true;
        physicalLX=axis(lx);physicalLY=axis(-ly);
        physicalRX=axis(rx);physicalRY=axis(-ry);
        physicalLT=(int)(Math.max(0f,Math.min(1f,lt))*255f);
        physicalRT=(int)(Math.max(0f,Math.min(1f,rt))*255f);
        publish(false);
    }
    synchronized void setPhysicalButton(int bit,boolean pressed) {
        if(closed)return;
        physicalConnected=true;
        if(pressed)physicalButtons|=bit;else physicalButtons&=~bit;
        publish(false);
    }
    synchronized void disconnectPhysical() {
        if(closed)return;
        physicalConnected=false;
        physicalButtons=0;
        physicalLX=physicalLY=physicalRX=physicalRY=physicalLT=physicalRT=0;
        publish(false);
    }
    synchronized void releaseTouch() {
        if(closed)return;
        clearTouch();
        publish(false);
    }
    private void clearTouch() {
        touchLX=touchLY=touchRX=touchRY=touchButtons=touchLT=touchRT=0;
    }
    private void publish(boolean force) {
        int connected=(virtualEnabled||physicalConnected)?1:0;
        int buttons=touchButtons|physicalButtons;
        int lx=touchLX!=0?touchLX:physicalLX;
        int ly=touchLY!=0?touchLY:physicalLY;
        int rx=touchRX!=0?touchRX:physicalRX;
        int ry=touchRY!=0?touchRY:physicalRY;
        int lt=Math.max(touchLT,physicalLT);
        int rt=Math.max(touchRT,physicalRT);
        int hash=connected;
        hash=hash*31+buttons;hash=hash*31+lx;hash=hash*31+ly;
        hash=hash*31+rx;hash=hash*31+ry;hash=hash*31+lt;hash=hash*31+rt;
        if(!force && hash==previousHash)return;
        previousHash=hash;
        // Seqlock: odd while writing, even after publishing. Readers never
        // spin/block; 3 retries maximum, return disconnected on torn state.
        int odd=sequence+1;
        buffer.putInt(8,odd);
        writerFence.incrementAndGet(); // full Java memory fence
        buffer.putInt(0,MAGIC);
        buffer.putInt(4,VERSION);
        buffer.putShort(12,(short)buttons);
        buffer.put(14,(byte)lt);
        buffer.put(15,(byte)rt);
        buffer.putShort(16,(short)lx);
        buffer.putShort(18,(short)ly);
        buffer.putShort(20,(short)rx);
        buffer.putShort(22,(short)ry);
        buffer.putInt(24,connected);
        buffer.putInt(28,++packet);
        writerFence.incrementAndGet(); // publish prior stores before even seq
        sequence=odd+1;
        buffer.putInt(8,sequence);
        // No force()/fsync(), since both processes have MAP_SHARED views.
    }
    synchronized ByteBuffer snapshotForTest() {
        ByteBuffer result=ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer read=buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        read.position(0);read.limit(SIZE);result.put(read);result.flip();
        return result;
    }
    @Override public synchronized void close() {
        if(closed)return;
        virtualEnabled=false;physicalConnected=false;
        clearTouch();
        physicalButtons=physicalLX=physicalLY=physicalRX=physicalRY=physicalLT=physicalRT=0;
        publish(true);
        closed=true;
    }
}

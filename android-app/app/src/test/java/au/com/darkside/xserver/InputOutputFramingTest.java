package au.com.darkside.xserver;

import org.junit.Test;
import java.io.EOFException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class InputOutputFramingTest {
    @Test(timeout=5000) public void directPixelDecodePreservesNextRequestAndClearsReceiveTiming() throws Exception {
        try(ServerSocket listener=new ServerSocket(0);
            Socket peer=new Socket("127.0.0.1",listener.getLocalPort());
            Socket accepted=listener.accept()) {
            InputOutput io=new InputOutput(accepted);
            try {
                byte[] body=new byte[20+8+1];
                body[20]=1;body[21]=2;body[22]=3;
                body[24]=4;body[25]=5;body[26]=6;body[28]=99;
                peer.getOutputStream().write(body);
                io.beginRequest(28,true);io.readSkip(20);
                int[] colors=new int[2];io.readBgrx(colors,2,true);
                assertArrayEquals(new int[]{0xff030201,0xff060504},colors);
                try {io.readBgrx(colors,1,false);fail("crossed framed body");}
                catch(EOFException expected){}
                assertEquals(0,io.endRequest());assertEquals(0L,io.requestReadNanos());
                assertEquals(99,io.readByte());
            } finally {io.close();}
        }
    }
    @Test(timeout=5000) public void skipWaitsForFragmentedPadding() throws Exception {
        try (ServerSocket listener = new ServerSocket(0);
             Socket peer = new Socket("127.0.0.1", listener.getLocalPort());
             Socket accepted = listener.accept()) {
            InputOutput io = new InputOutput(accepted);
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                peer.getOutputStream().write(17);
                assertEquals(17, io.readByte());
                Future<Integer> result = worker.submit(() -> { io.readSkip(8); return io.readByte(); });
                try { result.get(100, TimeUnit.MILLISECONDS); fail("skip returned before its bytes arrived"); }
                catch (TimeoutException expected) { }
                peer.getOutputStream().write(new byte[]{0,0,0,0,0,0,0,0,42});
                assertEquals(42, (int)result.get(2, TimeUnit.SECONDS));
            } finally { io.close(); worker.shutdownNow(); }
        }
    }

    @Test(timeout=5000) public void handlerCannotConsumeFollowingRequest() throws Exception {
        try (ServerSocket listener = new ServerSocket(0);
             Socket peer = new Socket("127.0.0.1", listener.getLocalPort());
             Socket accepted = listener.accept()) {
            InputOutput io = new InputOutput(accepted);
            try {
                peer.getOutputStream().write(new byte[]{1,2,3,99});
                io.beginRequest(3);
                assertEquals(1, io.readByte());
                io.readSkip(2);
                try { io.readByte(); fail("handler crossed request boundary"); }
                catch (EOFException expected) { }
                assertEquals(0, io.endRequest());
                assertEquals(99, io.readByte());
            } finally { io.close(); }
        }
    }

    @Test(timeout=5000) public void incompleteBodyAndUnreadBodyStayDistinct() throws Exception {
        try (ServerSocket listener = new ServerSocket(0);
             Socket peer = new Socket("127.0.0.1", listener.getLocalPort());
             Socket accepted = listener.accept()) {
            InputOutput io = new InputOutput(accepted);
            try {
                OutputStream out = peer.getOutputStream();
                out.write(new byte[]{1,2,3,99});
                io.beginRequest(3);
                assertEquals(1, io.readByte());
                assertEquals(2, io.endRequest());
                assertEquals(99, io.readByte());
                out.write(7); peer.shutdownOutput();
                try { io.beginRequest(2); fail("accepted truncated body"); }
                catch (EOFException expected) { }
            } finally { io.close(); }
        }
    }
}


package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;

public class X11ProbeTest {
    @Test public void rejectsListenerThatIsNotX11() throws Exception {
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            Thread peer=new Thread(()->{
                try(Socket s=server.accept()) {
                    new DataInputStream(s.getInputStream()).readFully(new byte[12]);
                    s.getOutputStream().write(new byte[8]);
                }catch(IOException ignored){}
            });peer.start();
            assertThrows(IOException.class,()->X11Probe.check(server.getLocalPort()));
            peer.join(3000);assertFalse(peer.isAlive());
        }
    }
    @Test public void acceptsCompleteProtocolElevenSetup() throws Exception {
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            Thread peer=new Thread(()->{
                try(Socket s=server.accept()) {
                    new DataInputStream(s.getInputStream()).readFully(new byte[12]);
                    s.getOutputStream().write(new byte[]{1,0,11,0,0,0,8,0});
                    s.getOutputStream().write(new byte[32]);
                }catch(IOException ignored){}
            });peer.start();X11Probe.check(server.getLocalPort());
            peer.join(3000);assertFalse(peer.isAlive());
        }
    }
    @Test public void queryGlxReturnsActualServerExtensionBit() throws Exception {
        assertFalse(serveQueryExtension(false));
        assertTrue(serveQueryExtension(true));
    }
    private static boolean serveQueryExtension(boolean present) throws Exception {
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            final java.util.concurrent.atomic.AtomicReference<Throwable> failure=
                    new java.util.concurrent.atomic.AtomicReference<>();
            Thread peer=new Thread(()->{
                try(Socket s=server.accept()) {
                    DataInputStream input=new DataInputStream(s.getInputStream());
                    input.readFully(new byte[12]);
                    s.getOutputStream().write(new byte[]{1,0,11,0,0,0,8,0});
                    s.getOutputStream().write(new byte[32]);
                    byte[] request=new byte[12];input.readFully(request);
                    if(request[0]!=98 || request[2]!=3 || request[4]!=3 ||
                       request[8]!='G' || request[9]!='L' || request[10]!='X')
                        throw new IOException("Bad QueryExtension packet");
                    byte[] reply=new byte[32];
                    reply[0]=1;reply[8]=(byte)(present?1:0);
                    s.getOutputStream().write(reply);
                }catch(Throwable e){failure.set(e);}
            });peer.start();
            boolean result=X11Probe.hasExtension(server.getLocalPort(),"GLX");
            peer.join(3000);
            assertFalse(peer.isAlive());
            if(failure.get()!=null)throw new AssertionError(failure.get());
            return result;
        }
    }
    @Test public void invalidExtensionNameNeverConnects() {
        assertThrows(IOException.class,()->X11Probe.hasExtension(6012,"GLX\n"));
    }
}

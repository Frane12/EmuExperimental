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
}

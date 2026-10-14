package com.drnas.console;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Check the X11 protocol handshake, not just an open TCP port. */
final class X11Probe {
    static void check(int port) throws IOException {
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1",port),2000);
            socket.setSoTimeout(2000);
            OutputStream out=socket.getOutputStream();
            out.write(new byte[]{'l',0,11,0,0,0,0,0,0,0,0,0});out.flush();
            DataInputStream in=new DataInputStream(socket.getInputStream());
            byte[] head=new byte[8];in.readFully(head);
            int length=(head[6]&255)|((head[7]&255)<<8);
            if(head[0]!=1 || head[2]!=11 || head[3]!=0 || length<8 || length>16384)
                throw new IOException("X11 setup rejected or invalid response");
            byte[] setup=new byte[length*4];in.readFully(setup);
        }
    }
    private X11Probe(){}
}

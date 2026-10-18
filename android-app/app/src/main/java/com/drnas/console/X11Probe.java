package com.drnas.console;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Validate X11 protocol, not merely TCP connectivity; query real extensions. */
final class X11Probe {
    private static DataInputStream handshake(Socket socket,int port) throws IOException {
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
        return in;
    }
    static void check(int port) throws IOException {
        try(Socket socket=new Socket()) {handshake(socket,port);}
    }
    /**
     * Send X11 core QueryExtension (opcode 98), little endian.
     * Presence only means a server advertises an extension; not GPU readiness.
     */
    static boolean hasExtension(int port,String name) throws IOException {
        if(name==null || !name.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IOException("Invalid X11 extension name");
        byte[] ascii=name.getBytes(StandardCharsets.US_ASCII);
        int pad=(-ascii.length)&3;
        int words=2+(ascii.length+pad)/4;
        try(Socket socket=new Socket()) {
            DataInputStream in=handshake(socket,port);
            OutputStream out=socket.getOutputStream();
            out.write(new byte[]{98,0,(byte)words,(byte)(words>>8),
                    (byte)ascii.length,(byte)(ascii.length>>8),0,0});
            out.write(ascii);
            for(int i=0;i<pad;i++)out.write(0);
            out.flush();
            byte[] reply=new byte[32];in.readFully(reply);
            int extra=(reply[4]&255)|((reply[5]&255)<<8)|
                    ((reply[6]&255)<<16)|((reply[7]&255)<<24);
            if(reply[0]!=1 || extra!=0)
                throw new IOException("Invalid X11 QueryExtension reply");
            // The "present" field occupies byte 8, not reply detail byte 1.
            return reply[8]!=0;
        }
    }
    private X11Probe(){}
}

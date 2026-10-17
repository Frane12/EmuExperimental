package com.drnas.console;

import android.content.Context;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.function.Consumer;

/** Installs the source-built D3D9 cube into this prefix and Wine Start menu. */
final class WineGraphicsTests {
    static void install(Context context,File prefix,Consumer<String> log) throws Exception {
        byte[] exe;
        try(InputStream in=context.getAssets().open("graphics-tests/TestD3D.exe")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){if(out.size()+n>4*1024*1024)throw new IOException("Cube executable too large");out.write(buf,0,n);}
            exe=out.toByteArray();
        }
        String hash;
        try(InputStream in=context.getAssets().open("graphics-tests/TestD3D.sha256")) {
            byte[] data=new byte[65];int filled=0,n;
            while(filled<data.length && (n=in.read(data,filled,data.length-filled))!=-1)filled+=n;
            hash=new String(data,0,filled,StandardCharsets.US_ASCII).trim();
        }
        install(prefix,exe,hash);
        log.accept("Start > Programs > System Tools > Test Direct3D (DRNAS cube) installed. D3D9 HAL -> selected DXVK/Vulkan driver; rendering still requires device test.");
    }

    static void install(File prefix,byte[] exe,String expectedHash) throws Exception {
        if(exe.length<256 || exe[0]!='M' || exe[1]!='Z')throw new IOException("Invalid cube PE");
        int offset=ByteBuffer.wrap(exe,60,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if(offset<64 || offset>exe.length-6 || exe[offset]!='P' || exe[offset+1]!='E' ||
                exe[offset+2]!=0 || exe[offset+3]!=0 || (exe[offset+4]&255)!=0x4c ||
                (exe[offset+5]&255)!=1)throw new IOException("Expected x86 PE cube (FEX WOW64 path)");
        StringBuilder actual=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(exe))actual.append(String.format(Locale.ROOT,"%02x",b&255));
        if(!expectedHash.matches("[0-9a-f]{64}") || !expectedHash.equals(actual.toString()))throw new IOException("Cube asset checksum mismatch");
        Path root=prefix.getCanonicalFile().toPath();
        Path graphics=directory(root,"drive_c/DRNAS/Graphics");
        Path menu=directory(root,"drive_c/ProgramData/Microsoft/Windows/Start Menu/Programs/System Tools");
        write(graphics.resolve("TestD3D.exe"),exe);
        write(menu.resolve("Test Direct3D (DRNAS cube).lnk"),shortcut());
    }

    private static Path directory(Path root,String relative) throws IOException {
        Path current=root;
        for(Path part:Paths.get(relative)) {
            current=current.resolve(part);
            if(Files.isSymbolicLink(current))throw new IOException("Unsafe graphics test directory");
            if(!Files.exists(current,LinkOption.NOFOLLOW_LINKS))Files.createDirectory(current);
            if(!Files.isDirectory(current,LinkOption.NOFOLLOW_LINKS))throw new IOException("Graphics test directory unavailable");
        }
        return current;
    }
    private static void write(Path target,byte[] data) throws IOException {
        if(Files.isSymbolicLink(target))throw new IOException("Unsafe graphics test target");
        Path temporary=Files.createTempFile(target.getParent(),".drnas-test-",".tmp");
        try {Files.write(temporary,data);Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(temporary);}
    }

    /** MS-SHLLINK: local LinkInfo + Unicode working directory; no shell commands. */
    static byte[] shortcut() throws IOException {
        byte[] path="C:\\DRNAS\\Graphics\\TestD3D.exe\0".getBytes(StandardCharsets.US_ASCII);
        String work="C:\\DRNAS\\Graphics";
        int linkSize=28+17+path.length+1;
        ByteBuffer b=ByteBuffer.allocate(76+linkSize+2+work.length()*2+4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(76).put(new byte[]{1,0x14,2,0,0,0,0,0,(byte)0xc0,0,0,0,0,0,0,0x46});
        b.putInt(0x92).putInt(0x80); // HasLinkInfo, HasWorkingDir, IsUnicode
        b.putLong(0).putLong(0).putLong(0).putInt(0).putInt(0).putInt(1);
        b.putShort((short)0).putShort((short)0).putInt(0).putInt(0);
        b.putInt(linkSize).putInt(28).putInt(1).putInt(28).putInt(45).putInt(0).putInt(45+path.length);
        b.putInt(17).putInt(3).putInt(0).putInt(16).put((byte)0); // local fixed drive
        b.put(path).put((byte)0);
        b.putShort((short)work.length()).put(work.getBytes(StandardCharsets.UTF_16LE)).putInt(0);
        return b.array();
    }
    private WineGraphicsTests(){}
}

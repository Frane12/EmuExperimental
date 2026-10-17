package com.drnas.console;

import org.junit.Test;
import java.io.File;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import static org.junit.Assert.*;

public class WineGraphicsTestsTest {
    private byte[] pe(){byte[] b=new byte[256];b[0]='M';b[1]='Z';b[60]=64;b[64]='P';b[65]='E';b[68]=0x4c;b[69]=1;return b;}
    private String hash(byte[] b)throws Exception{StringBuilder s=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format("%02x",x&255));return s.toString();}
    private void clean(Path root)throws Exception{try(java.util.stream.Stream<Path> walk=Files.walk(root)){for(Path p:(Iterable<Path>)walk.sorted(java.util.Comparator.reverseOrder())::iterator)Files.delete(p);}}
    @Test public void installsExactPeAndWorkingStartMenuLinkIdempotently()throws Exception{
        Path root=Files.createTempDirectory("cube-prefix");
        try{
            Files.createDirectory(root.resolve("drive_c"));byte[] exe=pe();
            WineGraphicsTests.install(root.toFile(),exe,hash(exe));
            Path target=root.resolve("drive_c/DRNAS/Graphics/TestD3D.exe");
            assertArrayEquals(exe,Files.readAllBytes(target));
            Path link=root.resolve("drive_c/ProgramData/Microsoft/Windows/Start Menu/Programs/System Tools/Test Direct3D (DRNAS cube).lnk");
            byte[] bytes=Files.readAllBytes(link);ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(76,b.getInt());assertEquals(0x92,b.getInt(20));
            int info=76,local=info+b.getInt(info+16);
            String text=new String(bytes,local,bytes.length-local,StandardCharsets.ISO_8859_1);
            assertTrue(text.startsWith("C:\\DRNAS\\Graphics\\TestD3D.exe\0"));
            WineGraphicsTests.install(root.toFile(),exe,hash(exe));assertArrayEquals(bytes,Files.readAllBytes(link));
        }finally{clean(root);}
    }
    @Test public void refusesBadHashBeforeWritingAndRefusesSymlinkEscape()throws Exception{
        Path root=Files.createTempDirectory("cube-safe"),outside=Files.createTempDirectory("cube-outside");
        try{
            Files.createDirectory(root.resolve("drive_c"));
            try{WineGraphicsTests.install(root.toFile(),pe(),"0".repeat(64));fail();}catch(java.io.IOException expected){}
            assertFalse(Files.exists(root.resolve("drive_c/DRNAS")));
            Files.createSymbolicLink(root.resolve("drive_c/DRNAS"),outside);
            try{WineGraphicsTests.install(root.toFile(),pe(),hash(pe()));fail();}catch(java.io.IOException expected){}
            try(java.util.stream.Stream<Path> files=Files.list(outside)){assertEquals(0,files.count());}
        }finally{clean(root);clean(outside);}
    }
}

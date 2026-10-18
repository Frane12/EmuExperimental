package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;

public final class BlenderDrivePathsTest {
    @Test public void resolvesLocalFoldersIncludingUnicodeAndSdCards() throws Exception {
        File base=Files.createTempDirectory("drnas-storage-").toFile();
        assertEquals(new File(base,"Documents/Anđela Blender").getCanonicalFile(),
                BlenderDrivePaths.resolve("primary:Documents/Anđela Blender",base,base));
        assertEquals(new File(base,"1234-ABCD/Blender").getCanonicalFile(),
                BlenderDrivePaths.resolve("1234-ABCD:Blender",base,base));
    }
    @Test public void rejectsTraversalProviderIdsAndStorageRoots() throws Exception {
        File base=new File(System.getProperty("java.io.tmpdir"));
        for(String id:new String[]{"primary:","primary:/etc","primary:../escape",
                "primary:a/../escape","primary:a//b","primary:a\\b","raw:/tmp",
                "../../:Blender","primary:a"+(char)0+"b"}) {
            try {BlenderDrivePaths.resolve(id,base,base);fail(id);}
            catch(IOException expected){}
        }
    }
    @Test public void directDriveSupportsSaveReopenAndOwnedRemap() throws Exception {
        Path base=Files.createTempDirectory("drnas-drive-");
        File prefix=Files.createDirectory(base.resolve("prefix")).toFile();
        File first=Files.createDirectory(base.resolve("first")).toFile();
        File second=Files.createDirectory(base.resolve("second")).toFile();
        BlenderDrivePaths.verify(first);
        assertEquals(0,first.list().length); // access probes leave no files behind
        BlenderDrivePaths.map(prefix,first);
        Path guest=prefix.toPath().resolve("dosdevices/d:/project.blend");
        byte[] project="BLENDER-v450-test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(guest,project);
        assertArrayEquals(project,Files.readAllBytes(first.toPath().resolve("project.blend")));
        BlenderDrivePaths.map(prefix,first);
        BlenderDrivePaths.map(prefix,second);
        assertEquals(second.getCanonicalFile().toPath(),Files.readSymbolicLink(prefix.toPath().resolve("dosdevices/d:")));
        assertArrayEquals(project,Files.readAllBytes(first.toPath().resolve("project.blend")));
    }
    @Test public void neverOverwritesUnownedDrives() throws Exception {
        Path base=Files.createTempDirectory("drnas-drive-owned-");
        File prefix=Files.createDirectory(base.resolve("prefix")).toFile();
        Path dos=Files.createDirectory(prefix.toPath().resolve("dosdevices"));
        Path other=Files.createDirectory(base.resolve("other"));
        Files.createSymbolicLink(dos.resolve("d:"),other);
        try {BlenderDrivePaths.map(prefix,base.toFile());fail("unowned drive");}
        catch(IOException expected){}
        assertEquals(other,Files.readSymbolicLink(dos.resolve("d:")));
    }
}

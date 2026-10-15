package com.drnas.console;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class WinePrefixLayoutTest {
    @Rule public TemporaryFolder tmp=new TemporaryFolder();

    @Test public void provisionsDocumentsAndKeepsExistingSaves() throws Exception {
        File prefix=tmp.newFolder("prefix");
        assertTrue(new File(prefix,"drive_c/windows").mkdirs());
        WinePrefixLayout.ensure(prefix,s->{});
        File docs=new File(prefix,"drive_c/users/xuser/Documents");
        assertTrue(docs.isDirectory());
        assertTrue(new File(prefix,"drive_c/users/xuser/Documents/My Games").isDirectory());
        assertTrue(new File(prefix,"drive_c/users/xuser/AppData/Roaming").isDirectory());
        Path cMapping=new File(prefix,"dosdevices/c:").toPath();
        assertTrue("C: must be visible to Wine", Files.isSymbolicLink(cMapping));
        assertEquals(new File(prefix,"drive_c").toPath().toAbsolutePath().normalize(),
                cMapping.getParent().resolve(Files.readSymbolicLink(cMapping)).normalize());
        File save=new File(docs,"save.dat");
        Files.write(save.toPath(),new byte[]{1,2,3});
        WinePrefixLayout.ensure(prefix,s->{});
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(save.toPath()));
    }

    @Test public void rejectsForeignCDriveMappingWithoutChangingIt() throws Exception {
        File prefix=tmp.newFolder("prefixForeign");
        assertTrue(new File(prefix,"drive_c/windows").mkdirs());
        Path dos=new File(prefix,"dosdevices").toPath();
        Files.createDirectories(dos);
        Path foreign=tmp.newFolder("foreignDrive").toPath();
        Path cMapping=dos.resolve("c:");
        Files.createSymbolicLink(cMapping,foreign);
        try {
            WinePrefixLayout.ensure(prefix,s->{});
            fail("Must not silently overwrite an existing external C: mapping");
        } catch(java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("C:"));
        }
        assertEquals(foreign,Files.readSymbolicLink(cMapping));
    }

    @Test public void rejectsSymlinkedUserFolder() throws Exception {
        File prefix=tmp.newFolder("prefix2");
        assertTrue(new File(prefix,"drive_c").mkdirs());
        Path external=tmp.newFolder("external").toPath();
        Files.createSymbolicLink(new File(prefix,"drive_c/users").toPath(),external);
        try {
            WinePrefixLayout.ensure(prefix,s->{});
            fail("Unsafe prefix symlink followed");
        } catch(java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("symlink"));
        }
    }
}

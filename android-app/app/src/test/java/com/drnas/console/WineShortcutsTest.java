package com.drnas.console;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import static org.junit.Assert.*;

public final class WineShortcutsTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void nativeUtilitiesHaveExplicitExecutables(){
        assertEquals("notepad.exe",WineShortcuts.builtinExecutable("notepad"));
        assertEquals("taskmgr.exe",WineShortcuts.builtinExecutable("tasks"));
        assertEquals("C:\\DRNAS\\Tools\\DRNASPaint.exe",WineShortcuts.builtinExecutable("paint"));
        assertEquals("C:\\DRNAS\\Tools\\DRNASPad.exe",WineShortcuts.builtinExecutable("retropad"));
        assertNull(WineShortcuts.builtinExecutable("bad;command"));
        assertTrue(WineShortcuts.isLaunchRequest("builtin:wordpad"));
        assertFalse(WineShortcuts.isLaunchRequest("builtin:notfound"));
    }
    @Test public void linkLaunchRejectsTraversalAndMissingFiles() throws Exception {
        File prefix=folder.newFolder("wine");
        File desktop=new File(prefix,"drive_c/users/xuser/Desktop");
        assertTrue(desktop.mkdirs());
        File link=new File(desktop,"Example Game.lnk");
        Files.write(link.toPath(),new byte[]{0x4c,0,0,0});
        assertEquals(link.getCanonicalPath(),WineShortcuts.resolveLink(
                prefix,"users/xuser/Desktop/Example Game.lnk").getCanonicalPath());
        try {WineShortcuts.resolveLink(prefix,"../private.lnk");fail("traversal accepted");}
        catch(java.io.IOException expected){}
        try {WineShortcuts.resolveLink(prefix,"users/xuser/Desktop/Gone.lnk");
            fail("missing link accepted");
        }catch(java.io.IOException expected){}
        try {WineShortcuts.resolveLink(prefix,"users/xuser/Desktop/Example Game.exe");
            fail("executable accepted");
        }catch(java.io.IOException expected){}
    }
}

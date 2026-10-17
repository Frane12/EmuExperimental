package com.drnas.console;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;

public class AppFilesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void copiesDirectoriesInBothDirectionsWithoutDeletingSource() throws Exception {
        File c=temp.newFolder("c"),r=temp.newFolder("r");
        File game=new File(c,"Game One/sub");assertTrue(game.mkdirs());
        byte[] bytes={1,2,3,4,5};
        Files.write(new File(game,"game.exe").toPath(),bytes);
        assertEquals(bytes.length,AppFiles.copy(c,"Game One",r,"Game One"));
        assertArrayEquals(bytes,Files.readAllBytes(new File(r,"Game One/sub/game.exe").toPath()));
        assertTrue(new File(c,"Game One/sub/game.exe").isFile());
        assertEquals(bytes.length,AppFiles.copy(r,"Game One",c,"Returned"));
        assertArrayEquals(bytes,Files.readAllBytes(new File(c,"Returned/sub/game.exe").toPath()));
        assertThrows(IOException.class,()->AppFiles.copy(c,"Game One",r,"Game One"));
    }
    @Test public void rejectsTraversalSymlinksAndCopyIntoSelf() throws Exception {
        File c=temp.newFolder("c"),r=temp.newFolder("r");
        Files.write(new File(c,"file.exe").toPath(),new byte[]{1});
        assertThrows(IOException.class,()->AppFiles.resolve(c,"../r"));
        assertThrows(IOException.class,()->AppFiles.resolve(c,"/etc"));
        Files.createSymbolicLink(new File(c,"escape").toPath(),r.toPath());
        assertThrows(IOException.class,()->AppFiles.resolve(c,"escape/file.exe"));
        File dir=new File(c,"Game");assertTrue(dir.mkdir());
        assertThrows(IOException.class,()->AppFiles.copy(c,"Game",c,"Game/Again"));
    }
    @Test public void userShortcutLaunchUsesOneUnixArgumentAndNotAShell() {
        String path="/data/user/0/app/shared/Game One/game;name.exe";
        java.util.List<String> cmd=WineGuiSession.command(new File("/system/bin/linker64"),
                new File("/guest/wine"),"exec:"+path);
        assertEquals("start.exe",cmd.get(cmd.size()-3));
        assertEquals("/unix",cmd.get(cmd.size()-2));
        assertEquals(path,cmd.get(cmd.size()-1));
        assertFalse(cmd.contains("/bin/sh"));
        assertTrue(WineShortcuts.isLaunchRequest("user:01234567-1234-1234-1234-0123456789ab"));
        assertFalse(WineShortcuts.isLaunchRequest("user:../../private"));
    }
}

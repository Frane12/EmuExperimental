package com.drnas.console;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class CrysisWindowedGuardTest {
    @Rule public TemporaryFolder tmp=new TemporaryFolder();
    private File docs(File prefix) {return new File(prefix,"drive_c/users/xuser/Documents/My Games");}
    private File cfg(File prefix) {return new File(docs(prefix),"Crysis/game.cfg");}

    @Test public void createsWindowedConfigOnlyForDetectedCrysisAndKeepsGameUntouched() throws Exception {
        File shared=tmp.newFolder("shared"),prefix=tmp.newFolder("prefix");
        Files.createDirectories(docs(prefix).toPath());
        File game=new File(shared,"Crysis/Bin32");
        Files.createDirectories(game.toPath());
        File exe=new File(game,"Crysis.exe");
        Files.write(exe.toPath(),new byte[]{1,2,3});
        ArrayList<String> messages=new ArrayList<>();
        CrysisWindowedGuard.prepareIfInstalled(shared,prefix,messages::add);
        String output=new String(Files.readAllBytes(cfg(prefix).toPath()),StandardCharsets.US_ASCII);
        assertTrue(output.contains("r_Fullscreen = 0"));
        assertTrue(output.contains("r_Width = 1280"));
        assertTrue(output.contains("r_Height = 720"));
        assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(exe.toPath()));
        CrysisWindowedGuard.prepareIfInstalled(shared,prefix,messages::add);
        assertEquals(output,new String(Files.readAllBytes(cfg(prefix).toPath()),StandardCharsets.US_ASCII));
    }

    @Test public void keepsOriginalSettingsAndBacksUpOnlyOnce() throws Exception {
        File shared=tmp.newFolder("games"),prefix=tmp.newFolder("guest");
        File bin=new File(shared,"Crysis-Retail/Crysis/Bin32");
        Files.createDirectories(bin.toPath());
        Files.createFile(new File(bin,"CRYSIS.EXE").toPath());
        File parent=cfg(prefix).getParentFile();
        Files.createDirectories(parent.toPath());
        String original="sys_spec_Texture = 2\r\nr_Fullscreen = 1\r\n";
        Files.write(cfg(prefix).toPath(),original.getBytes(StandardCharsets.ISO_8859_1));
        CrysisWindowedGuard.prepareIfInstalled(shared,prefix,s->{});
        String applied=new String(Files.readAllBytes(cfg(prefix).toPath()),StandardCharsets.ISO_8859_1);
        assertTrue(applied.startsWith(original));
        assertEquals(original,new String(Files.readAllBytes(
                new File(parent,"game.cfg.drnas-original").toPath()),StandardCharsets.ISO_8859_1));
        CrysisWindowedGuard.prepareIfInstalled(shared,prefix,s->{});
        assertEquals(applied,new String(Files.readAllBytes(cfg(prefix).toPath()),StandardCharsets.ISO_8859_1));
    }

    @Test public void leavesEverythingAloneWhenNoGameIsPresent() throws Exception {
        File shared=tmp.newFolder("empty-games"),prefix=tmp.newFolder("empty-prefix");
        Files.createDirectories(docs(prefix).toPath());
        CrysisWindowedGuard.prepareIfInstalled(shared,prefix,s->{});
        assertFalse(cfg(prefix).exists());
    }
}

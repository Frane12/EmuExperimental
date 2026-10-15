package com.drnas.console;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public final class WineDesktopWallpaperTest {
    @Test public void generatedBmpHasValidHeaderPixelsAndResolution() throws Exception {
        Path file=Files.createTempFile("drnas-waves-", ".bmp");
        try {
            WineDesktopWallpaper.writeBmp(file);
            byte[] bmp=Files.readAllBytes(file);
            assertEquals(WineDesktopWallpaper.BMP_BYTES,bmp.length);
            assertEquals('B',bmp[0]); assertEquals('M',bmp[1]);
            assertEquals(bmp.length,le32(bmp,2));
            assertEquals(54,le32(bmp,10));
            assertEquals(40,le32(bmp,14));
            assertEquals(1280,le32(bmp,18));
            assertEquals(720,le32(bmp,22));
            assertEquals(24,(bmp[28]&255) | ((bmp[29]&255)<<8));
            assertEquals(0,le32(bmp,30));
            assertNotEquals(bmp[54+300],bmp[54+1000]);
        } finally {Files.deleteIfExists(file);}
    }
    @Test public void registrySetsWallpaperAndDoesNotChangeScreenMode() {
        String reg=WineDesktopWallpaper.registryText();
        assertTrue(reg.contains("[HKEY_CURRENT_USER\\Control Panel\\Desktop]"));
        assertTrue(reg.contains("DRNAS-Waves.bmp"));
        assertTrue(reg.contains("\"WallpaperStyle\"=\"10\""));
        assertFalse(reg.contains("DisplaySettings"));
        assertFalse(reg.contains("ScreenSaveActive"));
    }
    private static int le32(byte[] b,int at) {
        return (b[at]&255)|((b[at+1]&255)<<8)|
               ((b[at+2]&255)<<16)|((b[at+3]&255)<<24);
    }
}

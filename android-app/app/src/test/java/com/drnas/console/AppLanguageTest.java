package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;
public class AppLanguageTest {
    @Test public void changesAppLabelsInBothDirections(){
        assertEquals("Početna",AppLanguage.translate("hr","Console"));
        assertEquals("Console",AppLanguage.translate("en","Početna"));
        assertEquals("Opcije",AppLanguage.translate("hr","Options"));
        assertEquals("Options",AppLanguage.translate("en","Opcije"));
        assertEquals("Accept",AppLanguage.translate("en","Prihvati"));
        assertEquals("Lijevi klik",AppLanguage.translate("hr","Left mouse"));
        assertEquals("▣  CHOOSE FOLDER",AppLanguage.translate("en","▣  ODABERI MAPU"));
    }
    @Test public void pathsAndUnknownUserTextRemainExact(){
        String path="C:/Games/My Game/game.exe";
        assertEquals(path,AppLanguage.translate("hr",path));
        assertEquals("Moja igra",AppLanguage.translate("en","Moja igra"));
        assertEquals("DRNAS_X11 error=3",AppLanguage.translate("hr","DRNAS_X11 error=3"));
    }
    @Test public void unknownPreferenceFallsBackToCroatian(){
        assertEquals("hr",AppLanguage.normalize(null));
        assertEquals("hr",AppLanguage.normalize("de"));
        assertEquals("en",AppLanguage.normalize("en"));
    }
}

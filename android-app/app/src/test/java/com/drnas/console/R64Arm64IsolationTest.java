package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class R64Arm64IsolationTest {
    @Test public void onlyKnownCpuModesAccepted() {
        assertTrue(R64Arm64Isolation.isCpuOnly("headless"));
        assertTrue(R64Arm64Isolation.isCpuOnly("cmd-normal"));
        assertTrue(R64Arm64Isolation.isCpuOnly("cmd-bootstrap"));
        assertFalse(R64Arm64Isolation.isCpuOnly("vulkan"));
        assertFalse(R64Arm64Isolation.isCpuOnly(null));
    }
    @Test public void cmdBootstrapABDiffersOnlyInOneKey() {
        Map<String,String> normal=new HashMap<>(),bootstrap=new HashMap<>();
        normal.put("DISPLAY","127.0.0.1:12.0");
        normal.put("WINEDLLOVERRIDES","winex11.drv=b;wineandroid.drv=d");
        normal.put("WINEBOOTSTRAPMODE","1");
        bootstrap.putAll(normal);
        R64Arm64Isolation.configure(normal,R64Arm64Isolation.CMD_NORMAL);
        R64Arm64Isolation.configure(bootstrap,R64Arm64Isolation.CMD_BOOTSTRAP);
        assertFalse(normal.containsKey("DISPLAY"));
        assertFalse(bootstrap.containsKey("DISPLAY"));
        assertFalse(normal.containsKey("WINEBOOTSTRAPMODE"));
        assertEquals("1",bootstrap.remove("WINEBOOTSTRAPMODE"));
        assertEquals(normal,bootstrap);
        assertTrue(normal.get("WINEDLLOVERRIDES").contains("winex11.drv=d"));
        assertEquals(25,R64Arm64Isolation.CMD_TIMEOUT_SECONDS);
    }
    @Test public void commandRequiresExactlyUniqueMarker() {
        String token=R64Arm64Isolation.newMarker();
        assertTrue(token.matches("DRNAS_R64_CMD_OK_[0-9a-f]{32}"));
        List<String> command=R64Arm64Isolation.cmdCommand(
                new File("/system/bin/linker64"),new File("/data/local/wine"),token);
        assertEquals(List.of("/system/bin/linker64","/data/local/wine","cmd.exe",
                "/d","/c","echo "+token),command);
        assertTrue(R64Arm64Isolation.matchesMarker(" "+token+" ",token));
        assertFalse(R64Arm64Isolation.matchesMarker("prefix "+token,token));
        assertFalse(R64Arm64Isolation.matchesMarker(token+"x",token));
        assertThrows(IllegalArgumentException.class,()->R64Arm64Isolation.cmdCommand(
                new File("/system/bin/linker64"),new File("/data/local/wine"),"unsafe"));
    }
    @Test public void unknownModeNeverModifiesCallerEnvironment() {
        Map<String,String> e=new HashMap<>();
        e.put("DISPLAY","original");
        assertThrows(IllegalArgumentException.class,()->R64Arm64Isolation.configure(e,"vulkan"));
        assertEquals("original",e.get("DISPLAY"));
    }
    @Test public void headlessAndVersionAreCpuOnlyAndVersionIsExact() {
        assertTrue(R64Arm64Isolation.isCpuOnly(R64Arm64Isolation.VERSION));
        assertFalse(R64Arm64Isolation.isCommand(R64Arm64Isolation.VERSION));
        assertEquals(40,R64Arm64Isolation.VERSION_TIMEOUT_SECONDS);
        assertTrue(R64Arm64Isolation.isBlenderVersionLine("Blender 4.5.14"));
        assertTrue(R64Arm64Isolation.isBlenderVersionLine("Blender 4.5.14 (hash abcdef)"));
        assertFalse(R64Arm64Isolation.isBlenderVersionLine("trace loaded Blender 4.5.14"));
        assertFalse(R64Arm64Isolation.isBlenderVersionLine("Blender 4.5.13"));
    }
    @Test public void cmdSuccessDoesNotDependOnInheritedStreamClosure() {
        assertTrue(R64Arm64Isolation.cmdPassed(true,0,true,true));
        assertFalse(R64Arm64Isolation.cmdPassed(true,1,true,true));
        assertFalse(R64Arm64Isolation.cmdPassed(true,0,false,true));
        assertFalse(R64Arm64Isolation.cmdPassed(false,0,true,true));
        assertFalse(R64Arm64Isolation.cmdPassed(true,0,true,false));
    }

}

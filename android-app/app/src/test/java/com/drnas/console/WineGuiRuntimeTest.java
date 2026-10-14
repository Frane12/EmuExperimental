package com.drnas.console;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;
public class WineGuiRuntimeTest {
    @Rule public TemporaryFolder tmp=new TemporaryFolder();
    @Test public void runtimePreservesWineSearchPathAndUsesLocalFontCache() throws Exception {
        File root=tmp.newFolder("gui"),prefix=tmp.newFolder("prefix & data");
        Map<String,String> env=new HashMap<>();env.put("LD_LIBRARY_PATH","/approved/proton/lib");
        WineGuiRuntime.configure(root,prefix,env);
        assertEquals(new File(root,"lib").getAbsolutePath()+":/approved/proton/lib:/system/lib64",env.get("LD_LIBRARY_PATH"));
        String config=new String(Files.readAllBytes(new File(root,"fonts.conf").toPath()),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(config.contains("/system/fonts"));assertTrue(config.contains("prefix &amp; data"));
        assertEquals(new File(root,"fonts.conf").getAbsolutePath(),env.get("FONTCONFIG_FILE"));
        assertTrue(new File(root,"font-cache").isDirectory());
        assertFalse(env.containsKey("VK_ICD_FILENAMES"));
    }
}

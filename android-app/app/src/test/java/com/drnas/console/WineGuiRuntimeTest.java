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
    @Test public void wineUnversionedNamesResolveToVerifiedVersionedFiles() throws Exception {
        File lib=tmp.newFolder("lib");byte[] bytes={1,2,3,4};
        for(String name:Arrays.asList("libXext.so.6","libfreetype.so.6","libfontconfig.so.1","libvulkan.so.1"))
            Files.write(new File(lib,name).toPath(),bytes);
        List<String> names=Arrays.asList("libXext.so.6","libfreetype.so.6","libfontconfig.so.1","libvulkan.so.1");
        assertEquals(3,WineGuiRuntime.installAliases(lib,names));
        for(String name:Arrays.asList("libXext.so","libfreetype.so","libfontconfig.so")){
            assertTrue(Files.isSymbolicLink(new File(lib,name).toPath()));
            assertArrayEquals(bytes,Files.readAllBytes(new File(lib,name).toPath()));
        }
        assertFalse(new File(lib,"libvulkan.so").exists());
        assertEquals(3,WineGuiRuntime.installAliases(lib,names));
    }
    @Test public void brokenAliasIsRepairedLocally() throws Exception {
        File lib=tmp.newFolder("repair");
        Files.write(new File(lib,"libXext.so.6").toPath(),new byte[]{7});
        Files.createSymbolicLink(new File(lib,"libXext.so").toPath(),java.nio.file.Paths.get("missing.so"));
        WineGuiRuntime.installAliases(lib,Collections.singletonList("libXext.so.6"));
        assertArrayEquals(new byte[]{7},Files.readAllBytes(new File(lib,"libXext.so").toPath()));
    }
    @Test(expected=java.io.IOException.class) public void aliasCannotEscapePrivateRuntime() throws Exception {
        WineGuiRuntime.aliasFor("../libXext.so.6");
    }
}

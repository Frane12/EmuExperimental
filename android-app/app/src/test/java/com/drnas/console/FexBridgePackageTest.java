package com.drnas.console;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import org.junit.Test;

import java.io.IOException;

/** Regression for FEX GameNative WCP archives containing a TAR root entry. */
public class FexBridgePackageTest {
    @Test public void gnuTarRootDirectoriesAreAccepted() throws Exception {
        assertEquals("", FexBridgePackage.normalize("."));
        assertEquals("", FexBridgePackage.normalize("./"));
        assertEquals("", FexBridgePackage.normalize("././"));
    }

    @Test public void ordinaryFilesAndDirectoriesAreCanonicalized() throws Exception {
        assertEquals("profile.json", FexBridgePackage.normalize("./profile.json"));
        assertEquals("system32", FexBridgePackage.normalize("./system32/"));
        assertEquals("system32/libwow64fex.dll",
                FexBridgePackage.normalize("./system32/libwow64fex.dll"));
    }

    private static org.json.JSONArray mappings(boolean includeUnix) throws Exception {
        org.json.JSONArray arr=new org.json.JSONArray();
        arr.put(new org.json.JSONObject().put("source","system32/libarm64ecfex.dll")
                .put("target","${system32}/libarm64ecfex.dll"));
        arr.put(new org.json.JSONObject().put("source","system32/libwow64fex.dll")
                .put("target","${system32}/libwow64fex.dll"));
        if(includeUnix) {
            arr.put(new org.json.JSONObject().put("source","aarch64-unix/libarm64ecfex.so")
                    .put("target","${libdir}/wine/aarch64-unix/libarm64ecfex.so"));
            arr.put(new org.json.JSONObject().put("source","aarch64-unix/libwow64fex.so")
                    .put("target","${libdir}/wine/aarch64-unix/libwow64fex.so"));
        }
        return arr;
    }

    @Test public void acceptsActualGameNative2609FourMappingProfile() throws Exception {
        // Actual public FEXCore-2609.wcp has four mappings; older O3 had two.
        assertEquals(true,FexBridgePackage.validateMappings(mappings(true)));
        assertEquals(false,FexBridgePackage.validateMappings(mappings(false)));
    }
    @Test public void refusesMalformedOrRetargetedFexMappings() throws Exception {
        org.json.JSONArray malformed=mappings(true);
        malformed.getJSONObject(3).put("target","${system32}/libwow64fex.so");
        assertThrows(IOException.class,()->FexBridgePackage.validateMappings(malformed));
        org.json.JSONArray missing=mappings(true);
        missing.remove(3);
        assertThrows(IOException.class,()->FexBridgePackage.validateMappings(missing));
        org.json.JSONArray duplicate=mappings(false);
        duplicate.put(duplicate.getJSONObject(0));
        assertThrows(IOException.class,()->FexBridgePackage.validateMappings(duplicate));
    }

    @Test public void dangerousArchivePathsAreRejected() {
        for (String path : new String[] {
                "", "/tmp/evil", "../escape", "./../../escape",
                "system32//payload.dll", "system32/../payload.dll",
                "system32\\evil.dll", "a\u0000b"
        }) {
            assertThrows("accepted path: " + path, IOException.class,
                    () -> FexBridgePackage.normalize(path));
        }
    }
}

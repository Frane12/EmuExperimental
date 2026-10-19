package com.drnas.console;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlenderGpuRoutingTest {
    @Test public void adrenoNeverReceivesPanvk() {
        for(String gpu:new String[]{"Adreno 610","Adreno 720","Adreno 740",
                "Adreno 810","Adreno 830","Adreno 840"}) {
            assertEquals("ADRENO_TURNIP_CANDIDATE_SYSTEM_FALLBACK",
                    BlenderGpuRouting.backend(gpu,35));
        }
        assertEquals("ADRENO_SYSTEM_VULKAN",BlenderGpuRouting.backend("Adreno 740",27));
    }
    @Test public void maliCandidatesAreConservativelyGated() {
        assertEquals("MALI_PANVK_CANDIDATE_SYSTEM_FALLBACK",
                BlenderGpuRouting.backend("Mali-G52 MC2",31));
        assertEquals("MALI_PANVK_CANDIDATE_SYSTEM_FALLBACK",
                BlenderGpuRouting.backend("Mali-G76",35));
        assertEquals("MALI_SYSTEM_VULKAN",BlenderGpuRouting.backend("Mali-G72",29));
        for(String gpu:new String[]{"Mali-G57","Mali-G610","Mali-G720"})
            assertEquals("MALI_SYSTEM_VULKAN",BlenderGpuRouting.backend(gpu,35));
    }
    @Test public void xclipseNeverReceivesTurnipOrPanvk() {
        for(String gpu:new String[]{"Samsung Xclipse 920","Xclipse 940 (AMD)",
                "AMD Xclipse 950"}) {
            assertEquals("XCLIPSE_FIRMWARE_VULKAN",BlenderGpuRouting.backend(gpu,35));
            assertTrue(GraphicsDriverCatalog.compatible(
                    TurnipDevicePolicy.target(gpu),35).isEmpty());
        }
        assertEquals("SYSTEM_VULKAN",BlenderGpuRouting.backend("Unknown GPU",35));
    }
}

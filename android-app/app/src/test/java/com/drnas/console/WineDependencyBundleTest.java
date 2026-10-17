package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.*;

public final class WineDependencyBundleTest {
    @Test public void ignoresDotNetAndOnlineWebInstallers() {
        assertFalse(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/dotNetFx40_Full_setup.exe"));
        assertFalse(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/dxwebsetup.exe"));
        assertFalse(WineDependencyBundle.isSupportedInstaller("_CommonRedist/dxwebsetup.exe"));
        assertFalse(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/unknown-installer.exe"));
    }
    @Test public void recognizesOfflineInstallersAndDirectXCabFiles() {
        assertTrue(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/DirectX/Jun2010/DXSETUP.exe"));
        assertTrue(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/DirectX/Jun2010/Jun2010_d3dx9_43_x86.cab"));
        assertTrue(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/vcredist/2022/VC_redist.x64.exe"));
        assertTrue(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/vcredist/2013/vcredist_x86.exe"));
        assertTrue(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/xnafx40_redist.msi"));
        assertTrue(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/oalinst.exe"));
        assertTrue(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/VulkanRT-1.1.108.0-Installer.exe"));
    }
    @Test public void rejectsTraversalOutsidePrivateStaging() {
        assertFalse(WineDependencyBundle.isAllowedArchiveMember("../_CommonRedist/oalinst.exe"));
        assertFalse(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/../oalinst.exe"));
        assertFalse(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist\\oalinst.exe"));
        assertFalse(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/DirectX/Jun2010/subfolder/evil.cab"));
        assertFalse(WineDependencyBundle.isAllowedArchiveMember("_CommonRedist/DirectX/Jun2010/installscript.vdf"));
    }
}

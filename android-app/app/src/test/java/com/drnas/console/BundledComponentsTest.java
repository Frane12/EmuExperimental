package com.drnas.console;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Regression for APK-bundled Turnip ZIP first-run installation. */
public final class BundledComponentsTest {
    @Test public void acceptsValidTurnipZipLocalFileHeader() {
        assertEquals("ZIP", BundledComponents.sniff(
                new byte[]{0x50, 0x4b, 0x03, 0x04, 0x14, 0x00}));
    }

    @Test public void retainsWcpCompressionRecognition() {
        assertEquals("ZSTD", BundledComponents.sniff(
                new byte[]{0x28, (byte)0xb5, 0x2f, (byte)0xfd, 0x00, 0x00}));
        assertEquals("XZ", BundledComponents.sniff(
                new byte[]{(byte)0xfd, 0x37, 0x7a, 0x58, 0x5a, 0x00}));
    }

    @Test public void refusesInvalidOrTruncatedSignatures() {
        assertEquals("unknown", BundledComponents.sniff(new byte[6]));
        assertEquals("unknown", BundledComponents.sniff(new byte[]{0x50, 0x4b, 0x03}));
        assertEquals("unknown", BundledComponents.sniff(null));
    }
}

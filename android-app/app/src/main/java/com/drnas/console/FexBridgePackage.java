package com.drnas.console;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Parse the *actual* FEXCore GameNative .wcp format (XZ-compressed TAR),
 * validate both PE bridge DLLs and store only those DLLs in isolated staging.
 *
 * This class NEVER loads Windows PE DLLs nor touches a Wine prefix.
 */
public final class FexBridgePackage {
    private static final String ARM64EC = "system32/libarm64ecfex.dll";
    private static final String WOW64 = "system32/libwow64fex.dll";
    private static final long MAX_ARCHIVE_EXPANDED = 64L * 1024 * 1024;
    private static final long MAX_SINGLE_ENTRY = 16L * 1024 * 1024;
    private static final int MAX_ENTRIES = 100;
    private static final long MAX_MANIFEST = 128 * 1024;

    /** Null = other (non-FEXCore) package; non-null = verified FEXCore bridges. */
    public static JSONObject inspectXz(File archive, File vault, String sha256) throws Exception {
        File tmp = new File(vault, ".fex-verify-" + sha256.substring(0, 12) +
                "-" + Long.toHexString(System.nanoTime()));
        if (!tmp.mkdir()) throw new IOException("Could not prepare FEX validation staging");
        boolean committed = false;
        try {
            JSONObject profile = null;
            Set<String> seen = new HashSet<>();
            long expanded = 0;
            int count = 0;
            int ecMachine = -1, wowMachine = -1;
            String ecHash = "", wowHash = "";

            // XZ streaming prevents extension-based guesses; tar entries are never executed.
            try (XZInputStream xz = new XZInputStream(new FileInputStream(archive));
                 TarArchiveInputStream tar = new TarArchiveInputStream(xz)) {
                TarArchiveEntry entry;
                while ((entry = tar.getNextTarEntry()) != null) {
                    if (++count > MAX_ENTRIES) throw new IOException("FEX archive has too many entries");
                    String name = normalize(entry.getName());
                    // GNU TAR often starts with "./" (a real root directory entry).
                    // Never treat a root directory as a payload, but reject empty files.
                    if (name.isEmpty()) {
                        if (entry.isDirectory()) continue;
                        throw new IOException("Empty TAR file member");
                    }
                    if (!seen.add(name)) throw new IOException("Duplicate archive member");
                    if (!entry.isFile() && !entry.isDirectory())
                        throw new IOException("Unexpected archive link/special file");
                    if (entry.getSize() < 0 || entry.getSize() > MAX_SINGLE_ENTRY)
                        throw new IOException("Unexpected archive entry size");
                    expanded += entry.getSize();
                    if (expanded > MAX_ARCHIVE_EXPANDED)
                        throw new IOException("Decompression limit exceeded");
                    if (entry.isDirectory()) continue;
                    if ("profile.json".equals(name)) {
                        if (entry.getSize() > MAX_MANIFEST) throw new IOException("Manifest too large");
                        ByteArrayOutputStream manifest = new ByteArrayOutputStream();
                        transferExact(tar, manifest, entry.getSize());
                        profile = new JSONObject(new String(
                                manifest.toByteArray(), StandardCharsets.UTF_8));
                    } else if (ARM64EC.equals(name) || WOW64.equals(name)) {
                        File output = new File(tmp, ARM64EC.equals(name) ? "libarm64ecfex.dll" : "libwow64fex.dll");
                        if (output.exists()) throw new IOException("Duplicate FEX library");
                        MessageDigest md = MessageDigest.getInstance("SHA-256");
                        try (FileOutputStream out = new FileOutputStream(output)) {
                            byte[] buffer = new byte[65536];
                            long remaining = entry.getSize();
                            while (remaining > 0) {
                                int n = tar.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                                if (n < 0) throw new IOException("Truncated DLL");
                                if (n == 0) continue;
                                out.write(buffer, 0, n);
                                md.update(buffer, 0, n);
                                remaining -= n;
                            }
                            out.getFD().sync();
                        }
                        int machine = peMachine(output);
                        if (ARM64EC.equals(name)) {
                            ecMachine = machine;
                            ecHash = hex(md.digest());
                        } else {
                            wowMachine = machine;
                            wowHash = hex(md.digest());
                        }
                    } else {
                        // Consume all entries via TarArchiveInputStream; do not extract them.
                        byte[] buf = new byte[65536];
                        long remaining = entry.getSize();
                        while (remaining > 0) {
                            int n = tar.read(buf, 0, (int)Math.min(remaining, buf.length));
                            if (n < 0) throw new IOException("Truncated tar entry");
                            remaining -= n;
                        }
                    }
                }
            }
            if (profile == null) throw new IOException("profile.json missing");
            if (!"FEXCore".equalsIgnoreCase(profile.optString("type"))) {
                return null; // DXVK is a different content adapter; still opaque-staged.
            }
            String version = profile.optString("versionName");
            if (!version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,80}"))
                throw new IOException("Invalid FEXCore version label");
            // GameNative FEX 2609 has FOUR mappings: 2 PE DLLs plus 2
            // AArch64 Unix bridge libraries. Earlier O3 WCPs had only 2.
            // Both forms are accepted, but destinations are strictly fixed.
            JSONArray mappings = profile.optJSONArray("files");
            boolean unixBridges = validateMappings(mappings);
            if (ecMachine < 0 || wowMachine < 0)
                throw new IOException("Missing required FEXCore PE bridge pair");
            // AMD64 + ARM64EC hybrids may carry 0x8664, ARM64 0xaa64.
            // We record machine IDs but do not claim Proton ABI compatibility.
            JSONObject summary = new JSONObject();
            summary.put("type","FEXCore");
            summary.put("versionName",version);
            summary.put("packageSha256",sha256);
            summary.put("arm64ecBridgeSha256",ecHash);
            summary.put("wow64BridgeSha256",wowHash);
            summary.put("arm64ecPEMachine","0x"+Integer.toHexString(ecMachine));
            summary.put("wow64PEMachine","0x"+Integer.toHexString(wowMachine));
            summary.put("unixBridgesInArchive",unixBridges);
            summary.put("status","fex_bridge_pair_verified_not_activated");
            summary.put("runtimeCompatibility","NOT_TESTED");
            File destDir = new File(vault, "fexcore/" + version + "-" + sha256.substring(0,12));
            if (!destDir.getParentFile().isDirectory() && !destDir.getParentFile().mkdirs())
                throw new IOException("Cannot create FEXCore vault");
            if (destDir.exists()) {
                // An identical package has already been inspected; do not overwrite it.
                summary.put("stagedAt",destDir.getAbsolutePath());
                return summary;
            }
            File manifest = new File(tmp, "bridge.json");
            try (FileOutputStream out = new FileOutputStream(manifest)) {
                out.write(summary.toString(2).getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            if (!tmp.renameTo(destDir)) throw new IOException("Cannot atomically stage FEXCore bridge");
            committed=true;
            summary.put("stagedAt",destDir.getAbsolutePath());
            return summary;
        } finally {
            if (!committed) removeTree(tmp);
        }
    }

    /**
     * Verify the manifest before using any extracted PE modules. WCPs from
     * GameNative currently ship 4 explicit mappings; older variants ship 2.
     * Nothing in a profile is allowed to escape the approved target folders.
     */
    static boolean validateMappings(JSONArray mappings) throws Exception {
        if (mappings == null || (mappings.length()!=2 && mappings.length()!=4))
            throw new IOException("FEXCore requires exactly 2 PE or 2 PE + 2 Unix mappings");
        Set<String> found=new HashSet<>();
        for(int i=0;i<mappings.length();i++) {
            JSONObject mapping=mappings.getJSONObject(i);
            String source=normalize(mapping.getString("source"));
            String target=mapping.getString("target");
            final String expected;
            if (source.equals(ARM64EC) || source.equals(WOW64))
                expected="${system32}/"+source.substring(source.lastIndexOf('/')+1);
            else if(source.equals("aarch64-unix/libarm64ecfex.so") ||
                    source.equals("aarch64-unix/libwow64fex.so"))
                expected="${libdir}/wine/aarch64-unix/"+
                        source.substring(source.lastIndexOf('/')+1);
            else throw new IOException("Unexpected FEXCore library mapping: "+source);
            if(!expected.equals(target) || !found.add(source))
                throw new IOException("Untrusted or duplicated FEXCore mapping: "+source);
        }
        if(!found.contains(ARM64EC) || !found.contains(WOW64))
            throw new IOException("Missing FEXCore PE pair");
        boolean unix1=found.contains("aarch64-unix/libarm64ecfex.so");
        boolean unix2=found.contains("aarch64-unix/libwow64fex.so");
        if(unix1!=unix2 || (mappings.length()==4 && !unix1))
            throw new IOException("Incomplete FEXCore AArch64 Unix pair");
        return unix1;
    }

    private static void transferExact(TarArchiveInputStream src, ByteArrayOutputStream out,
                                      long size) throws IOException {
        byte[] buf = new byte[8192];
        long remain = size;
        while (remain > 0) {
            int n = src.read(buf,0,(int)Math.min(buf.length,remain));
            if (n < 0) throw new IOException("Truncated manifest");
            if (n == 0) continue;
            out.write(buf,0,n);
            remain -= n;
        }
    }

    // Package-visible for regression tests; this is a pure path validator.
    static String normalize(String s) throws IOException {
        if (s == null || s.isEmpty() || s.startsWith("/") || s.contains("\\") ||
                s.contains("\u0000")) throw new IOException("Unsafe archive path");
        while (s.startsWith("./")) s=s.substring(2);
        // A directory named "." or "./" is the TAR root, not an invalid file.
        if (s.isEmpty() || s.equals(".")) return "";
        while (s.endsWith("/") && !s.isEmpty()) s = s.substring(0, s.length()-1);
        if (s.isEmpty()) throw new IOException("Empty archive member");
        for (String part : s.split("/",-1))
            if (part.equals("..") || part.isEmpty())
                throw new IOException("Unsafe archive member");
        return s;
    }

    private static int peMachine(File source) throws IOException {
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(source,"r")) {
            if (f.length() < 128 || f.readUnsignedByte()!=0x4d || f.readUnsignedByte()!=0x5a)
                throw new IOException("Bridge file is not PE/MZ");
            f.seek(0x3c);
            long peOffset = (long)Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()));
            if (peOffset > f.length()-6 || peOffset < 0x40)
                throw new IOException("Invalid PE offset");
            f.seek(peOffset);
            if (f.readUnsignedByte()!='P' || f.readUnsignedByte()!='E' ||
                    f.readUnsignedByte()!=0 || f.readUnsignedByte()!=0)
                throw new IOException("Invalid PE header");
            int lo = f.readUnsignedByte(), hi=f.readUnsignedByte();
            int machine = lo | (hi<<8);
            if (machine != 0x8664 && machine != 0xaa64
                    && machine != 0xa641 && machine != 0xa64e)
                throw new IOException("Unexpected FEXCore PE machine: 0x" +
                        Integer.toHexString(machine));
            return machine;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb=new StringBuilder(bytes.length*2);
        for(byte b:bytes)sb.append(String.format(Locale.US,"%02x",b&255));
        return sb.toString();
    }
    private static void removeTree(File path) {
        File[] kids = path.listFiles();
        if (kids!=null)for(File f:kids)removeTree(f);
        path.delete();
    }
    private FexBridgePackage() {}
}

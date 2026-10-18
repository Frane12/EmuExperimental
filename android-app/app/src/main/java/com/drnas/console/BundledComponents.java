package com.drnas.console;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * First-run, offline APK-to-private-vault provisioning. WCPs remain inert
 * archives until ContainerBootstrap stages them in the isolated prefix.
 * Never touches the public Downloads folder or existing manual imports.
 */
final class BundledComponents {
    private static final long LIMIT = 768L * 1024 * 1024;
    private static final String MANIFEST = "runtime-components-v1.json";

    static synchronized void install(Context context, Consumer<String> log) throws Exception {
        JSONObject manifest;
        try (InputStream in = context.getAssets().open(MANIFEST)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > 16 * 1024) throw new IOException("Oversized bundled manifest");
                out.write(buf, 0, n);
            }
            manifest = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
        if (manifest.getInt("schema") != 1) throw new IOException("Unsupported bundled schema");
        JSONArray items = manifest.getJSONArray("items");
        // 2 core WCPs (Proton, FEXCore) + 2 DXVK WCP variants +
        // 4 Turnip ZIPs (shared A6xx/A7xx, A810, A830, A840) = 8 entries.
        // The count is a sanity guard, not a requirement for every optional GPU.
        log.accept("[bundle] Manifest lists " + items.length() + " bundled components");
        if (items.length() < 3 || items.length() > 8)
            throw new IOException("Bundled manifest component count out of range: " + items.length());
        Set<String> kinds = new HashSet<>();
        File vault = new File(context.getFilesDir(), "wcp-vault");
        if (!vault.isDirectory() && !vault.mkdirs()) throw new IOException("Cannot create component vault");
        for (int i = 0; i < items.length(); i++) {
            JSONObject p = items.getJSONObject(i);
            String kind = p.getString("kind");
            String name = p.getString("name");
            String asset = p.getString("asset");
            String sha = p.getString("sha256");
            String compression = p.getString("compression");
            long expectedSize = p.getLong("bytes");
            String gpuTarget=p.optString("gpuTarget","");
            String variant=p.optString("variant","");
            String kindKey=kind.equals("Turnip")?kind+gpuTarget:
                    kind.equals("DXVK")?kind+variant:kind;
            if (!kinds.add(kindKey) ||
                    (kind.equals("DXVK") &&
                      !(variant.equals("standard") || variant.equals("async"))) ||
                    !(kind.equals("Proton") || kind.equals("FEXCore") ||
                      kind.equals("DXVK") || kind.equals("Turnip")) ||
                    (kind.equals("Turnip") &&
                       !(gpuTarget.equals(TurnipDevicePolicy.LEGACY) || gpuTarget.equals("A810") ||
                         gpuTarget.equals("A830") || gpuTarget.equals("A840"))) ||
                    !name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{1,100}\\.(?:wcp|zip)") ||
                    !asset.equals("bundled/" + name) ||
                    !sha.matches("[0-9a-f]{64}") ||
                    !compression.equals(kind.equals("Proton") ? "ZSTD" :
                            kind.equals("Turnip") ? "ZIP" : "XZ") ||
                    expectedSize < 16 || expectedSize > LIMIT)
                throw new IOException("Unsafe bundled component manifest entry");
            File archive = new File(vault, sha + ".wcp");
            File metadata = new File(vault, sha + ".json");
            // Existing private archive + metadata is reused on normal app starts.
            if (archive.isFile() && metadata.isFile()) {
                JSONObject meta = WcpRuntimeStager.readJson(metadata);
                if (sha.equals(meta.optString("sha256")) &&
                        (!kind.equals("Turnip") || gpuTarget.equals(meta.optString("gpuTarget"))) &&
                        (!kind.equals("FEXCore") ||
                                "fex_bridge_pair_verified_not_activated".equals(meta.optString("status")))) {
                    log.accept("[bundle] " + kind + " already installed");
                    continue;
                }
                throw new IOException("Existing component metadata mismatch: " + name);
            }
            log.accept("[bundle] Installing " + kind + " from APK (" +
                    (expectedSize / (1024 * 1024)) + " MiB compressed)");
            File partial = new File(vault, ".bundle-" + sha + "-" + System.nanoTime());
            boolean moved = false;
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long size = 0;
                byte[] magic = new byte[6];
                int filled = 0;
                try (InputStream in = context.getAssets().open(asset);
                     FileOutputStream out = new FileOutputStream(partial)) {
                    byte[] buf = new byte[128 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        if (n == 0) continue;
                        size += n;
                        if (size > LIMIT || size > expectedSize)
                            throw new IOException("Bundled archive size exceeds manifest");
                        for (int j = 0; j < n && filled < 6; j++) magic[filled++] = buf[j];
                        digest.update(buf, 0, n);
                        out.write(buf, 0, n);
                    }
                    out.getFD().sync();
                }
                if (size != expectedSize || !sha.equals(hex(digest.digest())) ||
                        !compression.equals(sniff(magic)))
                    throw new IOException("Bundled SHA-256 / size / format mismatch: " + name);

                JSONObject meta = new JSONObject();
                meta.put("name", name);
                meta.put("bytes", size);
                meta.put("sha256", sha);
                meta.put("compression", compression);
                meta.put("componentType", kind);
                meta.put("status", "archive_staged_not_validated");
                if (kind.equals("Turnip")) {
                    WcpRuntimeStager.stageTurnipZip(partial,vault,sha,log);
                    meta.put("status","turnip_zip_staged_not_activated");
                    meta.put("gpuTarget",gpuTarget);
                }
                if (kind.equals("DXVK"))meta.put("variant",variant);
                if (kind.equals("FEXCore")) {
                    JSONObject fex = FexBridgePackage.inspectXz(partial, vault, sha);
                    if (fex == null) throw new IOException("Bundled FEXCore profile invalid");
                    meta.put("fexBridge", fex);
                    meta.put("componentVersion", fex.getString("versionName"));
                    meta.put("status", "fex_bridge_pair_verified_not_activated");
                }
                meta.put("bundledFromApk", true);
                meta.put("importTimeUtcMs", System.currentTimeMillis());

                if (!archive.isFile()) {
                    if (!partial.renameTo(archive))
                        throw new IOException("Cannot commit bundled archive: " + name);
                    moved = true;
                }
                File temporaryMeta = new File(vault, "." + sha + ".json.tmp");
                try (FileOutputStream out = new FileOutputStream(temporaryMeta)) {
                    out.write(meta.toString(2).getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
                if (!temporaryMeta.renameTo(metadata))
                    throw new IOException("Cannot commit bundled metadata: " + name);
                log.accept("[bundle] " + kind + " verified and installed (" +
                        sha.substring(0, 12) + ")");
            } finally {
                if (partial.exists()) partial.delete();
                // Preserve any pre-existing archive; discard only our newly staged file on failure.
                if (moved && !metadata.isFile()) archive.delete();
            }
        }
        if (!kinds.contains("Proton") || !kinds.contains("FEXCore") ||
                !kinds.contains("DXVKstandard") || !kinds.contains("DXVKasync")) throw new IOException("Required bundled component missing");
    }

    static String sniff(byte[] a) {
        // Turnip packages are ZIP. Check their magic separately from WCP formats;
        // otherwise every valid bundled Turnip ZIP is rejected on first boot.
        if (a != null && a.length >= 4 && (a[0]&255) == 0x50 &&
                (a[1]&255) == 0x4b && (a[2]&255) == 0x03 && (a[3]&255) == 0x04)
            return "ZIP";
        if (a == null || a.length < 6) return "unknown";
        if ((a[0]&255) == 0x28 && (a[1]&255) == 0xb5 && (a[2]&255) == 0x2f &&
                (a[3]&255) == 0xfd) return "ZSTD";
        if ((a[0]&255) == 0xfd && (a[1]&255) == 0x37 && (a[2]&255) == 0x7a &&
                (a[3]&255) == 0x58 && (a[4]&255) == 0x5a && a[5] == 0)
            return "XZ";
        return "unknown";
    }

    private static String hex(byte[] digest) {
        StringBuilder s = new StringBuilder();
        for (byte b : digest) s.append(String.format(Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }

    private BundledComponents() {}
}


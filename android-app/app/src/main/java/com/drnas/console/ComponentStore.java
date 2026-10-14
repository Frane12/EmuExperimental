package com.drnas.console;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Import: copies user-selected WCP, fingerprints and sniffs compression.
 * FEXCore XZ WCPs receive strict manifest, PE and bridge-pair verification.
 * All other WCP formats are opaque-staged (manifest not validated).
 * Nothing in this class ever executes a component or changes a Wine prefix.
 */
public final class ComponentStore {
    public interface Result {
        void onComplete(String message, boolean success);
    }
    private static final long MAX_BYTES = 1024L * 1024L * 1024L;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public ComponentStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public void importUri(Uri uri, Result callback) {
        executor.execute(() -> {
            boolean success = false;
            String message;
            File tmp = null;
            try {
                File root = new File(context.getFilesDir(), "wcp-vault");
                if (!root.isDirectory() && !root.mkdirs())
                    throw new IllegalStateException("Cannot create component vault");
                String originalName = displayName(uri);
                if (!originalName.toLowerCase(java.util.Locale.ROOT).endsWith(".wcp"))
                    throw new IllegalArgumentException("Choose a .wcp component package");
                tmp = File.createTempFile(".incoming-", ".tmp", root);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long total = 0;
                byte[] first = new byte[6];
                int firstCount = 0;
                byte[] buffer = new byte[128 * 1024];
                try (InputStream input = context.getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(tmp)) {
                    if (input == null) throw new IllegalArgumentException("Cannot open selected file");
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        if (n == 0) continue;
                        total += n;
                        if (total > MAX_BYTES) throw new IllegalArgumentException("Package exceeds 1 GiB limit");
                        for (int j = 0; j < n && firstCount < 6; j++)
                            first[firstCount++] = buffer[j];
                        output.write(buffer, 0, n);
                        digest.update(buffer, 0, n);
                    }
                    output.getFD().sync();
                }
                if (total < 16) throw new IllegalArgumentException("Archive too small");
                String compression = sniff(first);
                if ("unknown".equals(compression))
                    throw new IllegalArgumentException("Unsupported/unknown archive format");
                String sha = hex(digest.digest());
                File dst = new File(root, sha + ".wcp");
                if (!dst.exists() && !tmp.renameTo(dst))
                    throw new IllegalStateException("Could not move package into content store");
                JSONObject meta = new JSONObject();
                meta.put("name", originalName);
                meta.put("bytes", total);
                meta.put("sha256", sha);
                meta.put("compression", compression);
                meta.put("status", "archive_staged_not_validated");
                boolean isFex = originalName.toLowerCase(java.util.Locale.ROOT).contains("fex");
                if (isFex) {
                    if (!"XZ".equals(compression))
                        throw new IllegalArgumentException("FEXCore bridge validation currently supports XZ packages only");
                    if (total > 32L * 1024L * 1024L)
                        throw new IllegalArgumentException("FEXCore package unusually large");
                    JSONObject verified = FexBridgePackage.inspectXz(dst, root, sha);
                    if (verified == null)
                        throw new IllegalArgumentException("Selected archive is not a FEXCore package");
                    meta.put("componentType","FEXCore");
                    meta.put("componentVersion",verified.getString("versionName"));
                    meta.put("fexBridge",verified);
                    meta.put("status","fex_bridge_pair_verified_not_activated");
                }
                meta.put("importTimeUtcMs", System.currentTimeMillis());
                File file = new File(root, sha + ".json");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(meta.toString(2).getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
                success = true;
                String state = meta.getString("status");
                message = "Staged " + originalName + " (" + compression + ", " +
                        (total / (1024 * 1024)) + " MiB) SHA-256 " + sha.substring(0, 12) +
                        ("fex_bridge_pair_verified_not_activated".equals(state)
                            ? " · FEX bridges VERIFIED (not activated)"
                            : " · manifest NOT validated; cannot execute");
            } catch (Exception e) {
                message = "Import failed: " + e.getMessage();
            } finally {
                if (tmp != null && tmp.exists()) tmp.delete();
            }
            final boolean done = success;
            final String report = message;
            main.post(() -> callback.onComplete(report, done));
        });
    }

    private String displayName(Uri uri) {
        String result = "component.wcp";
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) result = cursor.getString(i);
            }
        }
        return result == null ? "component.wcp" : result.replaceAll("[\\r\\n\\t]", "_");
    }

    private static String sniff(byte[] magic) {
        if ((magic[0]&255)==0x28 && (magic[1]&255)==0xb5 &&
                (magic[2]&255)==0x2f && (magic[3]&255)==0xfd) return "ZSTD";
        if ((magic[0]&255)==0xfd && (magic[1]&255)==0x37 &&
                (magic[2]&255)==0x7a && (magic[3]&255)==0x58 &&
                (magic[4]&255)==0x5a && (magic[5]&255)==0x00) return "XZ";
        if ((magic[0]&255)==0x1f && (magic[1]&255)==0x8b) return "GZIP";
        return "unknown";
    }
    private static String hex(byte[] a) {
        StringBuilder b = new StringBuilder();
        for (byte v : a) b.append(String.format(java.util.Locale.US, "%02x", v&255));
        return b.toString();
    }
    public List<String> list() {
        File root = new File(context.getFilesDir(), "wcp-vault");
        File[] entries = root.listFiles((dir,name)->name.endsWith(".json"));
        if (entries == null) return new ArrayList<>();
        Arrays.sort(entries, Comparator.comparingLong(File::lastModified).reversed());
        List<String> items = new ArrayList<>();
        for (File entry : entries) {
            try (FileInputStream input = new FileInputStream(entry)) {
                byte[] bytes = new byte[(int)Math.min(entry.length(), 32768)];
                int n = input.read(bytes);
                if (n <= 0) continue;
                JSONObject meta = new JSONObject(new String(bytes,0,n,StandardCharsets.UTF_8));
                items.add(meta.optString("name") + " · " + meta.optString("compression") +
                        " · " + meta.optString("sha256").substring(0,12) +
                        "\n    " + ("fex_bridge_pair_verified_not_activated".equals(meta.optString("status"))
                                ? "FEX BRIDGE PAIR VERIFIED — Proton link not active"
                                : "STAGED — not validated"));
                if (items.size() == 8) break;
            } catch (Exception ignored) {
                items.add("[bad component metadata: " + entry.getName() + "]");
            }
        }
        return items;
    }

    public void close() { executor.shutdown(); }
}

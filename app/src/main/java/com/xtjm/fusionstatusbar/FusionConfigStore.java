package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.UserManager;
import android.util.AtomicFile;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/** Serializes complete snapshots; failed writes never replace the committed baseline. */
public final class FusionConfigStore {
    private static final String PREFS = "fusion_statusbar";
    private static final String FILE = "fusion_config_v4.json";
    private static final int MAX_BYTES = 256 * 1024;
    private static final Object LOCK = new Object();
    private static final String KEY_RESTORE_TOKEN = "configuration_restore_token";

    public static final class WriteResult {
        public final boolean success;
        public final FusionConfig config;
        public final long revision;
        public final String error;

        WriteResult(boolean success, FusionConfig config, String error) {
            this.success = success;
            this.config = config;
            this.revision = config == null ? 0L : config.revision;
            this.error = error == null ? "" : error;
        }
    }

    interface SnapshotFile {
        byte[] read() throws IOException;
        void write(byte[] data) throws IOException;
    }

    private FusionConfigStore() { }

    public static FusionConfig read(Context context) {
        if (context == null) return FusionConfig.defaults();
        synchronized (LOCK) {
            return read(file(context), readLegacy(context));
        }
    }

    public static WriteResult write(Context context, FusionConfig config) {
        if (context == null || config == null) {
            return new WriteResult(false, FusionConfig.defaults(), "missing_config");
        }
        WriteResult result;
        synchronized (LOCK) {
            result = write(file(context), config, readLegacy(context));
        }
        notifyCommitted(context, result);
        return result;
    }

    static WriteResult restore(Context context, FusionConfig config) {
        WriteResult result;
        synchronized (LOCK) {
            result = ConfigurationRecoveryStore.restore(file(context), ConfigurationRecoveryStore.file(context),
                    config, readLegacy(context));
        }
        notifyCommitted(context, result);
        return result;
    }

    static WriteResult undoRestore(Context context) {
        WriteResult result;
        synchronized (LOCK) {
            result = ConfigurationRecoveryStore.undo(file(context), ConfigurationRecoveryStore.file(context), readLegacy(context));
        }
        notifyCommitted(context, result);
        return result;
    }

    static boolean canUndoRestore(Context context) {
        synchronized (LOCK) {
            return ConfigurationRecoveryStore.canUndo(file(context), ConfigurationRecoveryStore.file(context));
        }
    }

    static String restoreToken(SnapshotFile file) {
        try { return new JSONObject(new String(file.read(), StandardCharsets.UTF_8)).optString(KEY_RESTORE_TOKEN, ""); }
        catch (IOException | JSONException | RuntimeException error) { return ""; }
    }

    private static void notifyCommitted(Context context, WriteResult result) {
        if (result.success) {
            try {
                context.getContentResolver().notifyChange(FusionConfig.contentUri(), null);
            } catch (RuntimeException error) {
                Log.w("FusionStatusBar", "config committed; notification failed", error);
            }
        }
    }

    static FusionConfig read(SnapshotFile file, FusionConfig fallback) {
        try {
            byte[] bytes = file.read();
            if (bytes.length > MAX_BYTES) throw new IOException("config_too_large");
            return decode(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException | JSONException | RuntimeException error) {
            return fallback;
        }
    }

    static WriteResult write(SnapshotFile file, FusionConfig config, FusionConfig fallback) {
        return write(file, config, fallback, null);
    }

    static WriteResult write(SnapshotFile file, FusionConfig config, FusionConfig fallback, String restoreToken) {
        FusionConfig baseline = read(file, fallback);
        try {
            try {
                byte[] previous = file.read();
                if (previous.length > MAX_BYTES) throw new IOException("config_too_large");
                JSONObject stored = new JSONObject(new String(previous, StandardCharsets.UTF_8));
                if (stored.optInt(FusionConfig.KEY_SCHEMA_VERSION, 0)
                        > FusionConfig.CONFIG_SCHEMA_VERSION) throw new IOException("unsupported_config_schema");
            } catch (java.io.FileNotFoundException missing) {
                // The first save creates the snapshot from the migrated legacy values.
            } catch (JSONException malformed) {
                // A valid explicit user save may repair malformed persisted input.
            }
            if (baseline.revision == Long.MAX_VALUE) throw new IOException("revision_overflow");
            FusionConfig committed = config.withRevision(baseline.revision + 1L);
            JSONObject document = new JSONObject(encode(committed));
            String token = restoreToken == null ? restoreToken(file) : restoreToken;
            if (!token.isEmpty()) document.put(KEY_RESTORE_TOKEN, token);
            byte[] bytes = document.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IOException("config_too_large");
            file.write(bytes);
            return new WriteResult(true, committed, "");
        } catch (IOException | JSONException | RuntimeException error) {
            Log.w("FusionStatusBar", "config persistence failed", error);
            return new WriteResult(false, baseline, error.getClass().getSimpleName());
        }
    }

    static String encode(FusionConfig config) throws JSONException {
        JSONObject json = new JSONObject();
        Bundle values = config.toBundle();
        for (String key : values.keySet()) json.put(key, values.get(key));
        return json.toString();
    }

    static FusionConfig decode(String text) throws JSONException {
        if (text.length() > MAX_BYTES) throw new JSONException("config_too_large");
        JSONObject json = new JSONObject(text);
        int schema = json.optInt(FusionConfig.KEY_SCHEMA_VERSION, 0);
        if (schema > FusionConfig.CONFIG_SCHEMA_VERSION) {
            throw new JSONException("unsupported_config_schema");
        }
        Bundle values = new Bundle();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            putPrimitive(values, key, json.get(key));
        }
        return FusionConfig.fromBundle(sanitize(values));
    }

    static Bundle sanitize(Bundle input) {
        Bundle result = new Bundle();
        Bundle defaults = FusionConfig.defaults().toBundle();
        for (int i = 0; i < 5; i++) {
            defaults.putBoolean("telemetry_enabled_" + i, false);
            defaults.putInt("telemetry_position_" + i, 0);
            if (i < TelemetryConfig.COUNT) defaults.putInt("telemetry_group_side_" + i, 0);
        }
        for (String key : defaults.keySet()) {
            Object value = input.get(key);
            Object expected = defaults.get(key);
            if (expected instanceof Integer && value instanceof Integer) {
                result.putInt(key, (Integer) value);
            } else if (expected instanceof Long && value instanceof Number) {
                result.putLong(key, ((Number) value).longValue());
            } else if (expected instanceof Boolean && value instanceof Boolean) {
                result.putBoolean(key, (Boolean) value);
            } else if (expected instanceof String && value instanceof String
                    && ((String) value).length() <= MAX_BYTES / 2) {
                result.putString(key, (String) value);
            }
        }
        Object legacy = input.get(FusionConfig.KEY_LEGACY_BATTERY_SCALE);
        if (!result.containsKey(FusionConfig.KEY_ICON_SCALE) && legacy instanceof Integer) {
            result.putInt(FusionConfig.KEY_ICON_SCALE, (Integer) legacy);
        }
        return result;
    }

    static boolean availableBeforeUnlock(Context context) {
        Context device = context.createDeviceProtectedStorageContext();
        File base = new File(device.getFilesDir(), FILE);
        return base.exists() || new File(base + ".bak").exists()
                || !device.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getAll().isEmpty();
    }

    private static FusionConfig readLegacy(Context context) {
        Context device = context.createDeviceProtectedStorageContext();
        SharedPreferences preferences = device.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (preferences.getAll().isEmpty()) {
            UserManager users = context.getSystemService(UserManager.class);
            if (users == null || users.isUserUnlocked()) {
                try {
                    Context credential = context.isDeviceProtectedStorage()
                            ? context.createPackageContext(context.getPackageName(), 0) : context;
                    preferences = credential.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                } catch (android.content.pm.PackageManager.NameNotFoundException unavailable) {
                    Log.w("FusionStatusBar", "legacy config context unavailable", unavailable);
                }
            }
        }
        Bundle values = new Bundle();
        for (Map.Entry<String, ?> item : preferences.getAll().entrySet()) {
            putPrimitive(values, item.getKey(), item.getValue());
        }
        return FusionConfig.fromBundle(sanitize(values));
    }

    private static void putPrimitive(Bundle values, String key, Object value) {
        if (value instanceof Boolean) values.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) values.putInt(key, (Integer) value);
        else if (value instanceof Long) values.putLong(key, (Long) value);
        else if (value instanceof String) values.putString(key, (String) value);
    }

    private static SnapshotFile file(Context context) {
        AtomicFile atomic = new AtomicFile(new File(
                context.createDeviceProtectedStorageContext().getFilesDir(), FILE));
        return new SnapshotFile() {
            @Override public byte[] read() throws IOException {
                try (java.io.FileInputStream input = atomic.openRead()) {
                    byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                    if (bytes.length > MAX_BYTES) throw new IOException("config_too_large");
                    return bytes;
                }
            }

            @Override public void write(byte[] data) throws IOException {
                FileOutputStream output = null;
                try {
                    output = atomic.startWrite();
                    output.write(data);
                    output.flush();
                    output.getFD().sync();
                    atomic.finishWrite(output);
                    try (java.io.FileInputStream check = atomic.openRead()) {
                        if (!java.util.Arrays.equals(data, check.readNBytes(MAX_BYTES + 1))) {
                            throw new IOException("config_publish_failed");
                        }
                    }
                } catch (IOException | RuntimeException error) {
                    atomic.failWrite(output);
                    throw error;
                }
            }
        };
    }
}

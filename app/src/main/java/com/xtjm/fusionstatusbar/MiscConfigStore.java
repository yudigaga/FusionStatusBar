package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Device-protected atomic storage read freshly by the app and config provider. */
final class MiscConfigStore {
    private static final String FILE = "misc_config_v1.json";
    private static final int MAX_BYTES = 4096;
    private static final Object LOCK = new Object();

    private MiscConfigStore() { }

    static MiscConfig read(Context context) {
        if (context == null) return MiscConfig.defaults();
        synchronized (LOCK) { return read(file(context)); }
    }

    static boolean write(Context context, MiscConfig config) {
        if (context == null || config == null) return false;
        synchronized (LOCK) { return write(file(context), config); }
    }

    static MiscConfig read(FusionConfigStore.SnapshotFile file) {
        try {
            return decode(file.read());
        } catch (IOException | JSONException | RuntimeException error) {
            return MiscConfig.defaults();
        }
    }

    static boolean write(FusionConfigStore.SnapshotFile file, MiscConfig config) {
        try {
            try {
                JSONObject previous = document(file.read());
                if (previous.optInt(MiscConfig.KEY_SCHEMA, 0) > MiscConfig.SCHEMA_VERSION) {
                    throw new IOException("unsupported_misc_config_schema");
                }
            } catch (FileNotFoundException | JSONException missingOrMalformed) {
                // An explicit save may initialize or repair this small snapshot.
            }
            JSONObject next = new JSONObject();
            next.put(MiscConfig.KEY_SCHEMA, MiscConfig.SCHEMA_VERSION);
            next.put(MiscConfig.KEY_REMOVE_CHAIN_START_DIALOG, config.removeChainStartDialog);
            file.write(next.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | JSONException | RuntimeException error) {
            Log.w(FusionModule.TAG, "misc config persistence failed", error);
            return false;
        }
    }

    private static MiscConfig decode(byte[] bytes) throws IOException, JSONException {
        JSONObject json = document(bytes);
        Bundle values = new Bundle();
        values.putInt(MiscConfig.KEY_SCHEMA,
                json.optInt(MiscConfig.KEY_SCHEMA, MiscConfig.SCHEMA_VERSION));
        values.putBoolean(MiscConfig.KEY_REMOVE_CHAIN_START_DIALOG,
                Boolean.TRUE.equals(json.opt(MiscConfig.KEY_REMOVE_CHAIN_START_DIALOG)));
        return MiscConfig.fromBundle(values);
    }

    private static JSONObject document(byte[] bytes) throws IOException, JSONException {
        if (bytes.length > MAX_BYTES) throw new IOException("misc_config_too_large");
        return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }

    private static FusionConfigStore.SnapshotFile file(Context context) {
        Context device = context.createDeviceProtectedStorageContext();
        return new BoundedAtomicDocument(new File(device.getFilesDir(), FILE), MAX_BYTES);
    }
}

package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONException;

/** Durable host-owned snapshot used when the settings package is disabled. */
final class RuntimeConfigCache {
    private static final String TAG = "FusionStatusBar";
    private static final String PREFS = "fusion_statusbar_runtime";
    private static final String KEY_SNAPSHOT = "config_snapshot_v1";
    private static final int MAX_BYTES = 256 * 1024;

    private RuntimeConfigCache() { }

    static FusionConfig read(Context context) {
        if (context == null) return null;
        try {
            String snapshot = preferences(context).getString(KEY_SNAPSHOT, null);
            return snapshot == null ? null : FusionConfigStore.decode(snapshot);
        } catch (JSONException | RuntimeException error) {
            Log.w(TAG, "host config snapshot unavailable: " + error.getClass().getSimpleName());
            return null;
        }
    }

    static boolean write(Context context, FusionConfig config) {
        if (context == null || config == null) return false;
        try {
            SharedPreferences preferences = preferences(context);
            String snapshot = FusionConfigStore.encode(config);
            if (snapshot.length() > MAX_BYTES) return false;
            return preferences.edit().putString(KEY_SNAPSHOT, snapshot).commit();
        } catch (JSONException | RuntimeException error) {
            Log.w(TAG, "host config snapshot write failed: " + error.getClass().getSimpleName());
            return false;
        }
    }

    static boolean settingsPackageDisabled(Context context) {
        if (context == null) return false;
        try {
            int state = context.getPackageManager()
                    .getApplicationEnabledSetting(BuildConfig.APPLICATION_ID);
            return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

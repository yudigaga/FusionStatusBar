package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.os.Bundle;

import java.util.Map;

/** Remembers whether the injected SystemUI process read settings during this boot. */
final class FusionActivationStatus {
    private static final String PREFS = "fusion_activation";
    private static final String KEY_BOOT_START = "boot_start_ms";
    private static final String KEY_REPORT_TIME = "report_time_ms";
    private static final String STATUS_URI = "content://io.github.yudigaga.fusionstatusbar.config/activation";
    private static final long BOOT_TOLERANCE_MS = 120_000L;
    static final String KEY_READ_REVISION = "read_revision";
    static final String KEY_APPLIED_REVISION = "applied_revision";
    static final String KEY_MODULE_VERSION = "module_version";
    static final String KEY_MODULE_CODE = "module_code";
    static final String KEY_ROM = "rom";
    static final String KEY_FEATURES = "features";
    static final String KEY_RUNTIME_SESSION = "runtime_session";
    static final String APPLIED = "applied";
    static final String WAITING = "waiting";
    static final String DEGRADED = "degraded";
    static final String DISABLED = "disabled";
    static final String FAILED = "failed";
    static final String FEATURE_STATUS_BAR = "status_bar";
    static final String FEATURE_CONTROL_CENTER = "control_center";
    static final String FEATURE_NOTIFICATION_CLOCK = "notification_clock";
    private static final Object LOCK = new Object();
    private static final String RUNTIME_SESSION = java.util.UUID.randomUUID().toString();

    private FusionActivationStatus() {
    }

    static Uri contentUri() {
        return Uri.parse(STATUS_URI);
    }

    static void reportFromSystemUi(Context context) {
        reportRead(context, 0L);
    }

    static void reportRead(Context context, long revision) {
        reportRead(context, revision, RUNTIME_SESSION);
    }

    static String runtimeSession() { return RUNTIME_SESSION; }

    static String callerSession(int uid, int pid, Bundle values) {
        String token = values == null ? "legacy" : values.getString(KEY_RUNTIME_SESSION, "legacy");
        return uid + ":" + pid + ":" + bounded(token, 80);
    }

    static void reportRead(Context context, long revision, String session) {
        if (context == null) return;
        try {
            synchronized (LOCK) {
                SharedPreferences preferences = preferences(context);
                SharedPreferences.Editor editor = preferences.edit();
                boolean sameSession = isSameBoot(preferences.getLong(KEY_BOOT_START, 0L), bootStart())
                        && session.equals(preferences.getString(KEY_RUNTIME_SESSION, ""));
                if (!sameSession) {
                    editor.clear();
                }
                editor.putLong(KEY_BOOT_START, bootStart())
                        .putString(KEY_RUNTIME_SESSION, session)
                        .putLong(KEY_REPORT_TIME, System.currentTimeMillis())
                        .putLong(KEY_READ_REVISION, Math.max(revision,
                                sameSession
                                        ? preferences.getLong(KEY_READ_REVISION, 0L) : 0L)).apply();
            }
            context.getContentResolver().notifyChange(contentUri(), null);
        } catch (RuntimeException ignored) {
            // Status reporting must never prevent SystemUI from reading its config.
        }
    }

    /** Call from the runtime's serial worker, after view application on its main thread. */
    static boolean reportApplied(Context context, long revision, String feature,
            String state, String reason) {
        String value = state + (reason == null || reason.isEmpty() ? "" : ":" + reason);
        return reportApplied(context, revision, java.util.Collections.singletonMap(feature, value),
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                android.os.Build.MANUFACTURER + "/" + android.os.Build.MODEL + "/"
                        + android.os.Build.DISPLAY + "/sdk=" + android.os.Build.VERSION.SDK_INT);
    }

    static boolean reportApplied(Context context, long revision, Map<String, String> features,
            String moduleVersion, int moduleCode, String rom) {
        try {
            Bundle values = new Bundle();
            values.putString(KEY_RUNTIME_SESSION, RUNTIME_SESSION);
            values.putLong(FusionConfig.KEY_REVISION, revision);
            values.putString(KEY_MODULE_VERSION, bounded(moduleVersion, 80));
            values.putInt(KEY_MODULE_CODE, moduleCode);
            values.putString(KEY_ROM, bounded(rom, 256));
            Bundle states = new Bundle();
            if (features != null) {
                for (Map.Entry<String, String> item : features.entrySet()) {
                    if (states.size() >= 16) break;
                    states.putString(bounded(item.getKey(), 64), bounded(item.getValue(), 512));
                }
            }
            values.putBundle(KEY_FEATURES, states);
            Bundle result = context.getContentResolver().call(FusionConfig.contentUri(),
                    FusionConfig.METHOD_REPORT_APPLIED, null, values);
            return result != null && result.getBoolean("accepted", false);
        } catch (RuntimeException error) {
            return false;
        }
    }

    static boolean recordApplied(Context context, Bundle values) {
        return recordApplied(context, values, RUNTIME_SESSION);
    }

    static boolean recordApplied(Context context, Bundle values, String session) {
        if (values == null) return false;
        long revision = values.getLong(FusionConfig.KEY_REVISION, -1L);
        Bundle features = values.getBundle(KEY_FEATURES);
        if (revision < 0L || features == null || features.isEmpty() || features.size() > 16) return false;
        synchronized (LOCK) {
            SharedPreferences preferences = preferences(context);
            if (!isLoadedThisBoot(context)
                    || !session.equals(preferences.getString(KEY_RUNTIME_SESSION, ""))
                    || revision != preferences.getLong(KEY_READ_REVISION, 0L)
                    || revision < preferences.getLong(KEY_APPLIED_REVISION, -1L)) return false;
            SharedPreferences.Editor editor = preferences.edit();
            if (revision != preferences.getLong(KEY_APPLIED_REVISION, -1L)) {
                for (String key : preferences.getAll().keySet()) {
                    if (key.startsWith("feature_")) editor.remove(key);
                }
            }
            for (String feature : features.keySet()) {
                String state = features.getString(feature, "");
                if (feature.length() > 64 || state.length() > 512 || !validState(state)) return false;
                editor.putString("feature_" + feature, state);
            }
            editor.putLong(KEY_APPLIED_REVISION, revision)
                    .putString(KEY_MODULE_VERSION, bounded(values.getString(KEY_MODULE_VERSION, ""), 80))
                    .putInt(KEY_MODULE_CODE, values.getInt(KEY_MODULE_CODE, 0))
                    .putString(KEY_ROM, bounded(values.getString(KEY_ROM, ""), 256))
                    .putLong(KEY_REPORT_TIME, System.currentTimeMillis()).apply();
        }
        context.getContentResolver().notifyChange(contentUri(), null);
        return true;
    }

    static Bundle read(Context context) {
        Bundle result = new Bundle();
        if (!isLoadedThisBoot(context)) return result;
        synchronized (LOCK) {
            SharedPreferences preferences = preferences(context);
            result.putLong(KEY_READ_REVISION, preferences.getLong(KEY_READ_REVISION, 0L));
            result.putString(KEY_RUNTIME_SESSION, preferences.getString(KEY_RUNTIME_SESSION, ""));
            result.putLong(KEY_APPLIED_REVISION, preferences.getLong(KEY_APPLIED_REVISION, -1L));
            result.putString(KEY_MODULE_VERSION, preferences.getString(KEY_MODULE_VERSION, ""));
            result.putInt(KEY_MODULE_CODE, preferences.getInt(KEY_MODULE_CODE, 0));
            result.putString(KEY_ROM, preferences.getString(KEY_ROM, ""));
            Bundle features = new Bundle();
            for (Map.Entry<String, ?> item : preferences.getAll().entrySet()) {
                if (item.getKey().startsWith("feature_") && item.getValue() instanceof String) {
                    features.putString(item.getKey().substring(8), (String) item.getValue());
                }
            }
            result.putBundle(KEY_FEATURES, features);
        }
        return result;
    }

    private static boolean validState(String state) {
        String status = state.split(":", 2)[0];
        return APPLIED.equals(status) || WAITING.equals(status) || DEGRADED.equals(status)
                || DISABLED.equals(status) || FAILED.equals(status);
    }

    private static String bounded(String value, int limit) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), limit));
    }

    private static SharedPreferences preferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isLoadedThisBoot(Context context) {
        if (context == null) return false;
        SharedPreferences preferences = context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return isSameBoot(preferences.getLong(KEY_BOOT_START, 0), bootStart());
    }

    static long lastReportTime(Context context) {
        if (!isLoadedThisBoot(context)) return 0;
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_REPORT_TIME, 0);
    }

    static boolean isSameBoot(long storedStart, long currentStart) {
        return storedStart > 0 && currentStart > 0
                && Math.abs(storedStart - currentStart) < BOOT_TOLERANCE_MS;
    }

    private static long bootStart() {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }
}

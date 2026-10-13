package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/** Settings for system features outside the status bar and launcher. */
public final class MiscConfig {
    static final String METHOD_GET = "misc_config_get";
    static final String METHOD_SET = "misc_config_set";
    static final String KEY_ACCEPTED = "misc_config_accepted";
    static final String KEY_SCHEMA = "misc_schema_version";
    static final String KEY_REMOVE_CHAIN_START_DIALOG = "remove_chain_start_dialog";
    static final int SCHEMA_VERSION = 1;

    public final boolean removeChainStartDialog;

    private MiscConfig(boolean removeChainStartDialog) {
        this.removeChainStartDialog = removeChainStartDialog;
    }

    public static MiscConfig defaults() { return new MiscConfig(false); }

    public MiscConfig withRemoveChainStartDialog(boolean enabled) {
        return new MiscConfig(enabled);
    }

    public static Uri contentUri() {
        return Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".config/misc");
    }

    public Bundle toBundle() {
        Bundle values = new Bundle();
        values.putInt(KEY_SCHEMA, SCHEMA_VERSION);
        values.putBoolean(KEY_REMOVE_CHAIN_START_DIALOG, removeChainStartDialog);
        return values;
    }

    public static MiscConfig fromBundle(Bundle values) {
        if (values == null || values.getInt(KEY_SCHEMA, SCHEMA_VERSION) > SCHEMA_VERSION) {
            return defaults();
        }
        return new MiscConfig(Boolean.TRUE.equals(values.get(KEY_REMOVE_CHAIN_START_DIALOG)));
    }

    public static MiscConfig read(Context context) {
        if (context == null) return defaults();
        if (BuildConfig.APPLICATION_ID.equals(context.getPackageName())) {
            return MiscConfigStore.read(context);
        }
        MiscConfig loaded = readFromProvider(context);
        return loaded == null ? defaults() : loaded;
    }

    static MiscConfig readFromProvider(Context context) {
        if (context == null) return null;
        try {
            Bundle values = context.getContentResolver().call(contentUri(), METHOD_GET, null, null);
            return values == null ? null : fromBundle(values);
        } catch (RuntimeException error) {
            Log.w(FusionModule.TAG, "misc config read unavailable: "
                    + error.getClass().getSimpleName());
            return null;
        }
    }

    public static boolean save(Context context, MiscConfig config) {
        if (context == null || config == null) return false;
        if (BuildConfig.APPLICATION_ID.equals(context.getPackageName())) {
            boolean saved = MiscConfigStore.write(context, config);
            if (saved) notifyChanged(context);
            return saved;
        }
        try {
            Bundle result = context.getContentResolver().call(contentUri(), METHOD_SET, null,
                    config.toBundle());
            return result != null && result.getBoolean(KEY_ACCEPTED, false);
        } catch (RuntimeException error) {
            Log.w(FusionModule.TAG, "misc config save unavailable: "
                    + error.getClass().getSimpleName());
            return false;
        }
    }

    public boolean save(Context context) { return save(context, this); }

    static void notifyChanged(Context context) {
        try {
            context.getContentResolver().notifyChange(contentUri(), null);
        } catch (RuntimeException error) {
            Log.w(FusionModule.TAG, "misc config committed; notification unavailable", error);
        }
        try {
            MiscSystemConfigExchange.publish(context, MiscConfigStore.read(context));
        } catch (RuntimeException error) {
            Log.w(FusionModule.TAG, "misc config committed; framework publication unavailable", error);
        }
    }
}

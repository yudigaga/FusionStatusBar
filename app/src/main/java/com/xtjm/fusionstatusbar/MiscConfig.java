package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import java.util.EnumSet;

/** Settings for system features outside the status bar and launcher. */
public final class MiscConfig {
    static final String METHOD_GET = "misc_config_get";
    static final String METHOD_SET = "misc_config_set";
    static final String KEY_ACCEPTED = "misc_config_accepted";
    static final String KEY_SCHEMA = "misc_schema_version";
    static final String KEY_REMOVE_CHAIN_START_DIALOG = "remove_chain_start_dialog";
    static final String KEY_SCREENSHOT_CLIPBOARD_ENABLED = "screenshot_clipboard_enabled";
    static final String KEY_SCREEN_ON_TIME_PRECISION = "security_center_screen_on_time_precision_minutes";
    static final int SCHEMA_VERSION = 1;

    public final boolean removeChainStartDialog;
    public final boolean screenshotClipboardEnabled;
    public final int screenOnTimePrecisionMinutes;
    private final EnumSet<SecurityCenterFeature> securityFeatures;

    private MiscConfig(boolean removeChainStartDialog, boolean screenshotClipboardEnabled,
            EnumSet<SecurityCenterFeature> securityFeatures, int precision) {
        this.removeChainStartDialog = removeChainStartDialog;
        this.screenshotClipboardEnabled = screenshotClipboardEnabled;
        this.securityFeatures = securityFeatures.clone();
        this.screenOnTimePrecisionMinutes = Math.max(1, Math.min(60, precision));
    }

    public static MiscConfig defaults() {
        return new MiscConfig(false, false, EnumSet.noneOf(SecurityCenterFeature.class), 1);
    }

    public MiscConfig withRemoveChainStartDialog(boolean enabled) {
        return new MiscConfig(enabled, screenshotClipboardEnabled, securityFeatures, screenOnTimePrecisionMinutes);
    }

    public MiscConfig withScreenshotClipboardEnabled(boolean enabled) {
        return new MiscConfig(removeChainStartDialog, enabled, securityFeatures, screenOnTimePrecisionMinutes);
    }

    public boolean isEnabled(SecurityCenterFeature feature) { return securityFeatures.contains(feature); }

    public MiscConfig withSecurityFeature(SecurityCenterFeature feature, boolean enabled) {
        EnumSet<SecurityCenterFeature> next = securityFeatures.clone();
        if (enabled) next.add(feature); else next.remove(feature);
        return new MiscConfig(removeChainStartDialog, screenshotClipboardEnabled, next, screenOnTimePrecisionMinutes);
    }

    public MiscConfig withScreenOnTimePrecisionMinutes(int precision) {
        return new MiscConfig(removeChainStartDialog, screenshotClipboardEnabled, securityFeatures, precision);
    }

    public static Uri contentUri() {
        return Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".config/misc");
    }

    public Bundle toBundle() {
        Bundle values = new Bundle();
        values.putInt(KEY_SCHEMA, SCHEMA_VERSION);
        values.putBoolean(KEY_REMOVE_CHAIN_START_DIALOG, removeChainStartDialog);
        values.putBoolean(KEY_SCREENSHOT_CLIPBOARD_ENABLED, screenshotClipboardEnabled);
        for (SecurityCenterFeature feature : SecurityCenterFeature.values())
            values.putBoolean(feature.key(), isEnabled(feature));
        values.putInt(KEY_SCREEN_ON_TIME_PRECISION, screenOnTimePrecisionMinutes);
        return values;
    }

    public static MiscConfig fromBundle(Bundle values) {
        if (values == null || values.getInt(KEY_SCHEMA, SCHEMA_VERSION) > SCHEMA_VERSION) {
            return defaults();
        }
        EnumSet<SecurityCenterFeature> enabled = EnumSet.noneOf(SecurityCenterFeature.class);
        for (SecurityCenterFeature feature : SecurityCenterFeature.values())
            if (Boolean.TRUE.equals(values.get(feature.key()))) enabled.add(feature);
        Object precision = values.get(KEY_SCREEN_ON_TIME_PRECISION);
        return new MiscConfig(Boolean.TRUE.equals(values.get(KEY_REMOVE_CHAIN_START_DIALOG)),
                Boolean.TRUE.equals(values.get(KEY_SCREENSHOT_CLIPBOARD_ENABLED)), enabled,
                precision instanceof Integer ? (Integer) precision : 1);
    }

    static boolean validUpdate(Bundle values) {
        if (values == null || values.getInt(KEY_SCHEMA, -1) != SCHEMA_VERSION
                || !(values.get(KEY_REMOVE_CHAIN_START_DIALOG) instanceof Boolean)) return false;
        for (String key : defaults().toBundle().keySet()) {
            if (!values.containsKey(key) || KEY_SCHEMA.equals(key)) continue;
            Object value = values.get(key);
            if (KEY_SCREEN_ON_TIME_PRECISION.equals(key)) {
                if (!(value instanceof Integer) || (Integer) value < 1 || (Integer) value > 60) return false;
            } else if (!(value instanceof Boolean)) return false;
        }
        return true;
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

package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashSet;

/** Persistence owned by the exported config provider. */
final class DesktopConfigStore {
    private static final String FILE = "desktop_settings_v1";
    private static final String KEY_HIDE_ICONS = "hide_icons";
    private static final String KEY_HIDDEN_PACKAGES = "hidden_packages";

    private DesktopConfigStore() { }

    static DesktopConfig read(Context context) {
        if (context == null) return DesktopConfig.defaults();
        SharedPreferences preferences = preferences(context);
        Bundle values = new Bundle();
        boolean legacy = preferences.getInt("desktop_schema_version", 1) < 2;
        values.putInt("desktop_schema_version", 2);
        values.putBoolean(KEY_HIDE_ICONS, preferences.getBoolean(KEY_HIDE_ICONS, false));
        DesktopConfig defaults = DesktopConfig.defaults();
        values.putInt(DesktopConfig.KEY_BACK_STYLE, preferences.getInt(
                DesktopConfig.KEY_BACK_STYLE, defaults.backGestureStyle));
        values.putInt(DesktopConfig.KEY_BACK_BACKGROUND, preferences.getInt(
                DesktopConfig.KEY_BACK_BACKGROUND, defaults.backGestureBackgroundColor));
        values.putInt(DesktopConfig.KEY_BACK_ARROW, preferences.getInt(
                DesktopConfig.KEY_BACK_ARROW, defaults.backGestureArrowColor));
        values.putInt(DesktopConfig.KEY_BACK_HOLD_DISTANCE, preferences.getInt(
                DesktopConfig.KEY_BACK_HOLD_DISTANCE, defaults.backHoldDistanceDp));
        values.putInt(DesktopConfig.KEY_BACK_HOLD_DELAY, preferences.getInt(
                DesktopConfig.KEY_BACK_HOLD_DELAY, defaults.backHoldDelayMs));
        try {
            values.putStringArrayList(KEY_HIDDEN_PACKAGES,
                    new ArrayList<>(preferences.getStringSet(KEY_HIDDEN_PACKAGES, new HashSet<>())));
        } catch (ClassCastException ignored) {
            values.putStringArrayList(KEY_HIDDEN_PACKAGES, new ArrayList<>());
        }
        for (int i = 0; i < DesktopConfig.GESTURE_COUNT; i++) {
            int action = preferences.getInt(gestureActionKey(i), DesktopConfig.ACTION_NATIVE);
            String target = preferences.getString(gestureTargetKey(i), null);
            if (legacy) {
                int previous = action;
                action = switch (previous) {
                    case 1 -> DesktopConfig.ACTION_NOTIFICATIONS;
                    case 2 -> DesktopConfig.ACTION_QUICK_SETTINGS;
                    case 3, 4 -> DesktopConfig.ACTION_LAUNCH_APP;
                    default -> DesktopConfig.ACTION_NATIVE;
                };
                if (previous == 3) target = "com.android.settings";
                if (previous == 4) target = preferences.getString("gesture_" + i + "_package", null);
                if (target == null && action == DesktopConfig.ACTION_LAUNCH_APP) {
                    action = DesktopConfig.ACTION_NATIVE;
                }
            }
            values.putInt(gestureActionKey(i), action);
            values.putString(gestureTargetKey(i), target);
            values.putString(gestureLabelKey(i), preferences.getString(
                    gestureLabelKey(i), null));
            values.putInt(gestureToggleKey(i), preferences.getInt(gestureToggleKey(i), 1));
        }
        return DesktopConfig.fromBundle(values);
    }

    static boolean write(Context context, DesktopConfig config) {
        if (context == null || config == null) return false;
        SharedPreferences.Editor editor = preferences(context).edit();
        editor.putInt("desktop_schema_version", 2);
        editor.putBoolean(KEY_HIDE_ICONS, config.hideIcons);
        editor.putInt(DesktopConfig.KEY_BACK_STYLE, config.backGestureStyle);
        editor.putInt(DesktopConfig.KEY_BACK_BACKGROUND, config.backGestureBackgroundColor);
        editor.putInt(DesktopConfig.KEY_BACK_ARROW, config.backGestureArrowColor);
        editor.putInt(DesktopConfig.KEY_BACK_HOLD_DISTANCE, config.backHoldDistanceDp);
        editor.putInt(DesktopConfig.KEY_BACK_HOLD_DELAY, config.backHoldDelayMs);
        editor.putStringSet(KEY_HIDDEN_PACKAGES, new HashSet<>(config.hiddenPackages()));
        for (int i = 0; i < DesktopConfig.GESTURE_COUNT; i++) {
            editor.putInt(gestureActionKey(i), config.gestureAction(i));
            editor.putString(gestureTargetKey(i), config.gestureTarget(i));
            editor.putString(gestureLabelKey(i), config.gestureTargetLabel(i));
            editor.putInt(gestureToggleKey(i), config.gestureToggle(i));
        }
        return editor.commit();
    }

    private static SharedPreferences preferences(Context context) {
        Context owner = BuildConfig.APPLICATION_ID.equals(context.getPackageName())
                ? context : context.createDeviceProtectedStorageContext();
        return owner.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static String gestureActionKey(int gestureId) {
        return "gesture_" + gestureId + "_action";
    }

    private static String gestureTargetKey(int gestureId) {
        return "gesture_" + gestureId + "_target";
    }

    private static String gestureLabelKey(int gestureId) {
        return "gesture_" + gestureId + "_target_label";
    }

    private static String gestureToggleKey(int gestureId) {
        return "gesture_" + gestureId + "_toggle";
    }
}


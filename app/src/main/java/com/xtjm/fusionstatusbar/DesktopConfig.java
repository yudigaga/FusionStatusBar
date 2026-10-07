package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Launcher-only settings kept outside the SystemUI configuration snapshot. */
public final class DesktopConfig {
    static final String METHOD_GET = "desktop_config_get";
    static final String METHOD_SET = "desktop_config_set";
    static final String KEY_ACCEPTED = "desktop_config_accepted";
    static final String CONTENT_URI_STRING =
            "content://" + BuildConfig.APPLICATION_ID + ".config/desktop";

    public static final int GESTURE_SINGLE_DOWN = 0;
    public static final int GESTURE_DOUBLE_DOWN = 1;
    public static final int GESTURE_SINGLE_UP = 2;
    public static final int GESTURE_DOUBLE_UP = 3;
    public static final int GESTURE_DOCK_RIGHT = 4;
    public static final int GESTURE_DOCK_LEFT = 5;
    public static final int GESTURE_SHAKE = 6;
    public static final int GESTURE_DOUBLE_TAP = 7;
    public static final int GESTURE_PINCH = 8;
    public static final int GESTURE_EXPAND = 9;
    public static final int GESTURE_COUNT = 10;

    public static final int ACTION_NATIVE = 0;
    public static final int ACTION_NONE = 1;
    public static final int ACTION_NOTIFICATIONS = 2;
    public static final int ACTION_QUICK_SETTINGS = 3;
    public static final int ACTION_LOCK_DEVICE = 4;
    public static final int ACTION_SLEEP = 5;
    public static final int ACTION_SCREENSHOT = 6;
    public static final int ACTION_RECENTS = 7;
    public static final int ACTION_LAUNCH_APP = 8;
    public static final int ACTION_LAUNCH_SHORTCUT = 9;
    public static final int ACTION_TOGGLE = 10;
    public static final int ACTION_POWER_MENU = 12;
    public static final int ACTION_CLEAR_MEMORY = 13;
    public static final int ACTION_ONE_HANDED = 22;
    public static final int ACTION_CLEAR_NOTIFICATIONS = 23;
    public static final int ACTION_SIDEBAR = 26;
    public static final int ACTION_LAUNCH_ACTIVITY = 20;
    public static final int TOGGLE_COUNT = 12;

    private static final String KEY_HIDE_ICONS = "hide_icons";
    private static final String KEY_HIDDEN_PACKAGES = "hidden_packages";
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_HIDDEN_PACKAGES = 512;
    private static final int MAX_PACKAGE_NAME_LENGTH = 255;

    public final boolean hideIcons;
    private final Set<String> hiddenPackages;
    private final int[] gestureActions;
    private final String[] gestureTargets;
    private final String[] gestureTargetLabels;
    private final int[] gestureToggles;

    private DesktopConfig(boolean hideIcons, Set<String> hiddenPackages,
            int[] gestureActions, String[] gestureTargets, String[] gestureTargetLabels,
            int[] gestureToggles) {
        this.hideIcons = hideIcons;
        this.hiddenPackages = Collections.unmodifiableSet(normalizePackages(hiddenPackages));
        this.gestureActions = new int[GESTURE_COUNT];
        this.gestureTargets = new String[GESTURE_COUNT];
        this.gestureTargetLabels = new String[GESTURE_COUNT];
        this.gestureToggles = new int[GESTURE_COUNT];
        for (int i = 0; i < GESTURE_COUNT; i++) {
            int action = gestureActions != null && i < gestureActions.length
                    ? normalizeAction(gestureActions[i]) : ACTION_NATIVE;
            String target = gestureTargets != null && i < gestureTargets.length
                    ? normalizeTarget(gestureTargets[i]) : null;
            String targetLabel = gestureTargetLabels != null && i < gestureTargetLabels.length
                    ? normalizeLabel(gestureTargetLabels[i]) : null;
            if (target != null && (action == ACTION_LAUNCH_APP || action == ACTION_LAUNCH_ACTIVITY)
                    && !validComponentTarget(target)) {
                action = ACTION_NATIVE;
                target = null;
                targetLabel = null;
            }
            int toggle = gestureToggles != null && i < gestureToggles.length
                    ? normalizeToggle(gestureToggles[i]) : 1;
            this.gestureActions[i] = action;
            this.gestureTargets[i] = target;
            this.gestureTargetLabels[i] = targetLabel;
            this.gestureToggles[i] = toggle;
        }
    }

    public static DesktopConfig defaults() {
        return new DesktopConfig(false, Collections.emptySet(), null, null, null, null);
    }

    public static Uri contentUri() {
        return Uri.parse(CONTENT_URI_STRING);
    }

    public Set<String> hiddenPackages() {
        return hiddenPackages;
    }

    public boolean hidesPackage(String packageName) {
        return hideIcons && packageName != null && hiddenPackages.contains(packageName);
    }

    public int gestureAction(int gestureId) {
        return validGesture(gestureId) ? gestureActions[gestureId] : ACTION_NATIVE;
    }

    public String gestureTarget(int gestureId) {
        return validGesture(gestureId) ? gestureTargets[gestureId] : null;
    }

    public String gestureTargetLabel(int gestureId) {
        return validGesture(gestureId) ? gestureTargetLabels[gestureId] : null;
    }

    public int gestureToggle(int gestureId) {
        return validGesture(gestureId) ? gestureToggles[gestureId] : 1;
    }

    public String gesturePackage(int gestureId) {
        if (!validGesture(gestureId)) return null;
        String target = gestureTargets[gestureId];
        if (target == null) return null;
        int delimiter = target.indexOf('|');
        return delimiter < 0 ? target : target.substring(0, delimiter);
    }

    public boolean isGestureComplete(int gestureId) {
        if (!validGesture(gestureId)) return false;
        int action = gestureActions[gestureId];
        if (action == ACTION_LAUNCH_APP || action == ACTION_LAUNCH_SHORTCUT) {
            return gestureTargets[gestureId] != null;
        }
        if (action == ACTION_LAUNCH_ACTIVITY) {
            String target = gestureTargets[gestureId];
            int separator = target == null ? -1 : target.indexOf('|');
            return separator > 0 && separator < target.length() - 1;
        }
        return action != ACTION_TOGGLE || gestureToggles[gestureId] >= 1;
    }

    public DesktopConfig withHideIcons(boolean value) {
        return new DesktopConfig(value, hiddenPackages, gestureActions, gestureTargets,
                gestureTargetLabels, gestureToggles);
    }

    public DesktopConfig withHiddenPackages(Set<String> packages) {
        return new DesktopConfig(hideIcons, packages, gestureActions, gestureTargets,
                gestureTargetLabels, gestureToggles);
    }

    public DesktopConfig withGesture(int gestureId, int action) {
        if (!validGesture(gestureId)) return this;
        if (gestureAction(gestureId) == action) return this;
        int[] actions = gestureActions.clone();
        String[] targets = gestureTargets.clone();
        String[] labels = gestureTargetLabels.clone();
        int[] toggles = gestureToggles.clone();
        actions[gestureId] = normalizeAction(action);
        targets[gestureId] = null;
        labels[gestureId] = null;
        toggles[gestureId] = 1;
        return new DesktopConfig(hideIcons, hiddenPackages, actions, targets, labels, toggles);
    }

    public DesktopConfig withGestureTarget(int gestureId, String target, String label) {
        if (!validGesture(gestureId)) return this;
        String[] targets = gestureTargets.clone();
        String[] labels = gestureTargetLabels.clone();
        targets[gestureId] = normalizeTarget(target);
        labels[gestureId] = normalizeLabel(label);
        return new DesktopConfig(hideIcons, hiddenPackages, gestureActions, targets, labels,
                gestureToggles);
    }

    public DesktopConfig withGestureToggle(int gestureId, int toggle) {
        if (!validGesture(gestureId)) return this;
        int[] toggles = gestureToggles.clone();
        toggles[gestureId] = normalizeToggle(toggle);
        return new DesktopConfig(hideIcons, hiddenPackages, gestureActions, gestureTargets,
                gestureTargetLabels, toggles);
    }

    public Bundle toBundle() {
        Bundle values = new Bundle();
        values.putInt("desktop_schema_version", SCHEMA_VERSION);
        values.putBoolean(KEY_HIDE_ICONS, hideIcons);
        values.putStringArrayList(KEY_HIDDEN_PACKAGES, new ArrayList<>(hiddenPackages));
        for (int i = 0; i < GESTURE_COUNT; i++) {
            values.putInt(gestureActionKey(i), gestureActions[i]);
            values.putString(gestureTargetKey(i), gestureTargets[i]);
            values.putString(gestureLabelKey(i), gestureTargetLabels[i]);
            values.putInt(gestureToggleKey(i), gestureToggles[i]);
        }
        return values;
    }

    public static DesktopConfig fromBundle(Bundle values) {
        if (values == null) return defaults();
        Set<String> packages = new TreeSet<>();
        ArrayList<String> encodedPackages = values.getStringArrayList(KEY_HIDDEN_PACKAGES);
        if (encodedPackages != null) packages.addAll(encodedPackages);
        int[] actions = new int[GESTURE_COUNT];
        String[] targets = new String[GESTURE_COUNT];
        String[] labels = new String[GESTURE_COUNT];
        int[] toggles = new int[GESTURE_COUNT];
        for (int i = 0; i < GESTURE_COUNT; i++) {
            actions[i] = values.getInt(gestureActionKey(i), ACTION_NATIVE);
            targets[i] = values.getString(gestureTargetKey(i));
            labels[i] = values.getString(gestureLabelKey(i));
            toggles[i] = values.getInt(gestureToggleKey(i), 1);
        }
        return new DesktopConfig(values.getBoolean(KEY_HIDE_ICONS, false), packages,
                actions, targets, labels, toggles);
    }

    public static boolean save(Context context, DesktopConfig config) {
        if (context == null || config == null) return false;
        if (BuildConfig.APPLICATION_ID.equals(context.getPackageName())) {
            boolean saved = DesktopConfigStore.write(context, config);
            if (saved) context.getContentResolver().notifyChange(contentUri(), null);
            return saved;
        }
        try {
            Bundle result = context.getContentResolver().call(contentUri(), METHOD_SET,
                    null, config.toBundle());
            return result != null && result.getBoolean(KEY_ACCEPTED, false);
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "desktop settings save failed: "
                    + error.getClass().getSimpleName());
            return false;
        }
    }

    public static DesktopConfig read(Context context) {
        if (context == null) return defaults();
        if (BuildConfig.APPLICATION_ID.equals(context.getPackageName())) {
            return DesktopConfigStore.read(context);
        }
        try {
            Bundle result = context.getContentResolver().call(contentUri(), METHOD_GET, null, null);
            return result == null ? defaults() : fromBundle(result);
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "desktop settings read failed: "
                    + error.getClass().getSimpleName());
            return defaults();
        }
    }

    static int gestureForVerticalDirection(int direction, int pointerCount) {
        if (pointerCount == 1) {
            if (direction == 11) return GESTURE_SINGLE_DOWN;
            if (direction == 10) return GESTURE_SINGLE_UP;
        } else if (pointerCount == 2) {
            if (direction == 11) return GESTURE_DOUBLE_DOWN;
            if (direction == 10) return GESTURE_DOUBLE_UP;
        }
        return -1;
    }

    static int gestureForPinch(float progress) {
        if (progress > 0.02f) return GESTURE_PINCH;
        if (progress < -0.02f) return GESTURE_EXPAND;
        return -1;
    }

    static boolean isValidPackageName(String packageName) {
        return normalizePackage(packageName) != null;
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

    private static boolean validGesture(int gestureId) {
        return gestureId >= 0 && gestureId < GESTURE_COUNT;
    }

    private static int normalizeAction(int action) {
        return isSupportedAction(action) ? action : ACTION_NATIVE;
    }

    static boolean isSupportedAction(int action) {
        return action == ACTION_NATIVE || action == ACTION_NONE
                || action == ACTION_NOTIFICATIONS || action == ACTION_QUICK_SETTINGS
                || action == ACTION_LOCK_DEVICE || action == ACTION_SLEEP
                || action == ACTION_SCREENSHOT || action == ACTION_RECENTS
                || action == ACTION_LAUNCH_APP || action == ACTION_LAUNCH_SHORTCUT
                || action == ACTION_TOGGLE || action == ACTION_POWER_MENU
                || action == ACTION_CLEAR_MEMORY || action == ACTION_ONE_HANDED
                || action == ACTION_CLEAR_NOTIFICATIONS || action == ACTION_SIDEBAR
                || action == ACTION_LAUNCH_ACTIVITY;
    }

    private static String normalizeTarget(String target) {
        return target == null || target.isEmpty() || target.length() > 8192 ? null : target;
    }

    private static boolean validComponentTarget(String target) {
        int separator = target.indexOf('|');
        if (separator < 0) return isValidPackageName(target);
        if (separator <= 0 || target.indexOf('|', separator + 1) >= 0
                || !isValidPackageName(target.substring(0, separator))) return false;
        String className = target.substring(separator + 1);
        if (className.isEmpty()) return true;
        for (int i = 0; i < className.length(); i++) {
            char value = className.charAt(i);
            if (!(Character.isLetterOrDigit(value) || value == '.' || value == '$' || value == '_')) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeLabel(String label) {
        return label == null || label.isEmpty() ? null : label.substring(0,
                Math.min(label.length(), 512));
    }

    private static int normalizeToggle(int toggle) {
        return toggle >= 1 && toggle <= TOGGLE_COUNT ? toggle : 1;
    }

    private static TreeSet<String> normalizePackages(Set<String> packages) {
        TreeSet<String> normalized = new TreeSet<>();
        if (packages == null) return normalized;
        for (String packageName : packages) {
            String candidate = normalizePackage(packageName);
            if (candidate != null) normalized.add(candidate);
            if (normalized.size() >= MAX_HIDDEN_PACKAGES) break;
        }
        return normalized;
    }

    private static String normalizePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()
                || packageName.length() > MAX_PACKAGE_NAME_LENGTH
                || packageName.startsWith(".") || packageName.endsWith(".")) return null;
        boolean previousDot = false;
        boolean hasDot = false;
        for (int i = 0; i < packageName.length(); i++) {
            char ch = packageName.charAt(i);
            if (ch == '.') {
                if (previousDot) return null;
                previousDot = true;
                hasDot = true;
            } else {
                if (!(Character.isLetterOrDigit(ch) || ch == '_')) return null;
                previousDot = false;
            }
        }
        return previousDot || !hasDot ? null : packageName;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DesktopConfig that)) return false;
        return hideIcons == that.hideIcons && hiddenPackages.equals(that.hiddenPackages)
                && java.util.Arrays.equals(gestureActions, that.gestureActions)
                && java.util.Arrays.equals(gestureTargets, that.gestureTargets)
                && java.util.Arrays.equals(gestureTargetLabels, that.gestureTargetLabels)
                && java.util.Arrays.equals(gestureToggles, that.gestureToggles);
    }

    @Override public int hashCode() {
        int result = java.util.Objects.hash(hideIcons, hiddenPackages);
        result = 31 * result + java.util.Arrays.hashCode(gestureActions);
        result = 31 * result + java.util.Arrays.hashCode(gestureTargets);
        result = 31 * result + java.util.Arrays.hashCode(gestureTargetLabels);
        return 31 * result + java.util.Arrays.hashCode(gestureToggles);
    }
}


package com.xtjm.fusionstatusbar;

import android.content.SharedPreferences;
import android.os.Bundle;

/** Notification-center time settings, independent of the status-bar clock. */
final class NotificationClockConfig {
    static final int MAX_TIME_SIZE_SP = 180;
    static final int MAX_DATE_SIZE_SP = 72;
    static final int MAX_OFFSET_Y_DP = 64;
    static final int MAX_LIST_OFFSET_DP = 128;
    static final String KEY_ENABLED = "notification_clock_enabled";
    static final String KEY_PATTERN = "notification_clock_pattern";
    static final String KEY_SIZE = "notification_clock_size";
    static final String KEY_OFFSET_Y = "notification_clock_offset_y";
    static final String KEY_DATE_ENABLED = "notification_date_enabled";
    static final String KEY_DATE_PATTERN = "notification_date_pattern";
    static final String KEY_DATE_SIZE = "notification_date_size";
    static final String KEY_DATE_OFFSET_Y = "notification_date_offset_y";
    static final String KEY_LIST_OFFSET_Y = "notification_list_offset_y";
    static final String KEY_TIME_CENTERED = "notification_time_centered";
    static final String KEY_DATE_CENTERED = "notification_date_centered";
    static final String KEY_HIDE_SETTINGS = "notification_hide_settings";

    final boolean enabled;
    final String pattern;
    final int sizeSp;
    final int offsetYDp;
    final boolean dateEnabled;
    final String datePattern;
    final int dateSizeSp;
    final int dateOffsetYDp;
    final int listOffsetYDp;
    final boolean timeCentered;
    final boolean dateCentered;
    final boolean hideSettings;

    NotificationClockConfig(boolean enabled, String pattern, int sizeSp, int offsetYDp) {
        this(enabled, pattern, sizeSp, offsetYDp, false, "MM/dd E", 0, 0);
    }

    NotificationClockConfig(boolean enabled, String pattern, int sizeSp, int offsetYDp,
            boolean dateEnabled, String datePattern, int dateSizeSp, int dateOffsetYDp) {
        this(enabled, pattern, sizeSp, offsetYDp, dateEnabled, datePattern, dateSizeSp,
                dateOffsetYDp, 0);
    }

    private NotificationClockConfig(boolean enabled, String pattern, int sizeSp, int offsetYDp,
            boolean dateEnabled, String datePattern, int dateSizeSp, int dateOffsetYDp,
            int listOffsetYDp) {
        this(enabled, pattern, sizeSp, offsetYDp, dateEnabled, datePattern, dateSizeSp,
                dateOffsetYDp, listOffsetYDp, false, false);
    }

    private NotificationClockConfig(boolean enabled, String pattern, int sizeSp, int offsetYDp,
            boolean dateEnabled, String datePattern, int dateSizeSp, int dateOffsetYDp,
            int listOffsetYDp, boolean timeCentered, boolean dateCentered) {
        this(enabled, pattern, sizeSp, offsetYDp, dateEnabled, datePattern, dateSizeSp,
                dateOffsetYDp, listOffsetYDp, timeCentered, dateCentered, false);
    }

    private NotificationClockConfig(boolean enabled, String pattern, int sizeSp, int offsetYDp,
            boolean dateEnabled, String datePattern, int dateSizeSp, int dateOffsetYDp,
            int listOffsetYDp, boolean timeCentered, boolean dateCentered,
            boolean hideSettings) {
        this.enabled = enabled;
        this.pattern = pattern == null ? "HH:mm" : pattern.substring(0,
                Math.min(pattern.length(), ClockTextFormatter.MAX_PATTERN_LENGTH));
        this.sizeSp = sizeSp == 0 ? 0 : Math.max(24, Math.min(MAX_TIME_SIZE_SP, sizeSp));
        this.offsetYDp = Math.max(-24, Math.min(MAX_OFFSET_Y_DP, offsetYDp));
        this.dateEnabled = dateEnabled;
        this.datePattern = datePattern == null ? "MM/dd E" : datePattern.substring(0,
                Math.min(datePattern.length(), ClockTextFormatter.MAX_PATTERN_LENGTH));
        this.dateSizeSp = dateSizeSp == 0 ? 0 : Math.max(10, Math.min(MAX_DATE_SIZE_SP, dateSizeSp));
        this.dateOffsetYDp = Math.max(-24, Math.min(MAX_OFFSET_Y_DP, dateOffsetYDp));
        this.listOffsetYDp = Math.max(0, Math.min(MAX_LIST_OFFSET_DP, listOffsetYDp));
        this.timeCentered = timeCentered;
        this.dateCentered = dateCentered;
        this.hideSettings = hideSettings;
    }

    static NotificationClockConfig defaults() {
        return new NotificationClockConfig(false, "HH:mm", 0, 0);
    }

    /** Returns the single pattern used by the merged notification clock. */
    String combinedPattern() {
        if (!dateEnabled || pattern.indexOf('\n') >= 0 || datePattern.isEmpty()) {
            return pattern;
        }
        String merged = pattern + "\n" + datePattern;
        return merged.substring(0, Math.min(merged.length(), ClockTextFormatter.MAX_PATTERN_LENGTH));
    }

    static float expansionOffsetDp(int offsetDp, float progress) {
        return offsetDp * Math.max(0f, Math.min(1f, progress));
    }

    int dateRowSpacingDp(float progress) {
        if (progress < 0.5f || !((enabled && sizeSp > 0)
                || (dateEnabled && dateSizeSp > 0))) return 0;
        int timeOffset = enabled ? offsetYDp : 0;
        int dateOffset = dateEnabled ? dateOffsetYDp : 0;
        return 8 + Math.max(0, timeOffset - dateOffset);
    }

    static NotificationClockConfig fromBundle(Bundle values) {
        if (values == null) return defaults();
        return new NotificationClockConfig(values.getBoolean(KEY_ENABLED, false),
                values.getString(KEY_PATTERN, "HH:mm"),
                values.getInt(KEY_SIZE, 0), values.getInt(KEY_OFFSET_Y, 0),
                values.getBoolean(KEY_DATE_ENABLED, false),
                values.getString(KEY_DATE_PATTERN, "MM/dd E"),
                values.getInt(KEY_DATE_SIZE, 0), values.getInt(KEY_DATE_OFFSET_Y, 0),
                values.getInt(KEY_LIST_OFFSET_Y, 0),
                values.getBoolean(KEY_TIME_CENTERED, false),
                values.getBoolean(KEY_DATE_CENTERED, false),
                values.getBoolean(KEY_HIDE_SETTINGS, false));
    }

    static NotificationClockConfig read(SharedPreferences values) {
        return new NotificationClockConfig(values.getBoolean(KEY_ENABLED, false),
                values.getString(KEY_PATTERN, "HH:mm"),
                values.getInt(KEY_SIZE, 0), values.getInt(KEY_OFFSET_Y, 0),
                values.getBoolean(KEY_DATE_ENABLED, false),
                values.getString(KEY_DATE_PATTERN, "MM/dd E"),
                values.getInt(KEY_DATE_SIZE, 0), values.getInt(KEY_DATE_OFFSET_Y, 0),
                values.getInt(KEY_LIST_OFFSET_Y, 0),
                values.getBoolean(KEY_TIME_CENTERED, false),
                values.getBoolean(KEY_DATE_CENTERED, false),
                values.getBoolean(KEY_HIDE_SETTINGS, false));
    }

    void writeTo(Bundle values) {
        values.putBoolean(KEY_ENABLED, enabled);
        values.putString(KEY_PATTERN, pattern);
        values.putInt(KEY_SIZE, sizeSp);
        values.putInt(KEY_OFFSET_Y, offsetYDp);
        values.putBoolean(KEY_DATE_ENABLED, dateEnabled);
        values.putString(KEY_DATE_PATTERN, datePattern);
        values.putInt(KEY_DATE_SIZE, dateSizeSp);
        values.putInt(KEY_DATE_OFFSET_Y, dateOffsetYDp);
        values.putInt(KEY_LIST_OFFSET_Y, listOffsetYDp);
        values.putBoolean(KEY_TIME_CENTERED, timeCentered);
        values.putBoolean(KEY_DATE_CENTERED, dateCentered);
        values.putBoolean(KEY_HIDE_SETTINGS, hideSettings);
    }

    void writeTo(SharedPreferences.Editor editor) {
        editor.putBoolean(KEY_ENABLED, enabled);
        editor.putString(KEY_PATTERN, pattern);
        editor.putInt(KEY_SIZE, sizeSp);
        editor.putInt(KEY_OFFSET_Y, offsetYDp);
        editor.putBoolean(KEY_DATE_ENABLED, dateEnabled);
        editor.putString(KEY_DATE_PATTERN, datePattern);
        editor.putInt(KEY_DATE_SIZE, dateSizeSp);
        editor.putInt(KEY_DATE_OFFSET_Y, dateOffsetYDp);
        editor.putInt(KEY_LIST_OFFSET_Y, listOffsetYDp);
        editor.putBoolean(KEY_TIME_CENTERED, timeCentered);
        editor.putBoolean(KEY_DATE_CENTERED, dateCentered);
        editor.putBoolean(KEY_HIDE_SETTINGS, hideSettings);
    }

    NotificationClockConfig withEnabled(boolean value) {
        return new NotificationClockConfig(value, pattern, sizeSp, offsetYDp,
                dateEnabled, datePattern, dateSizeSp, dateOffsetYDp, listOffsetYDp,
                timeCentered, dateCentered, hideSettings);
    }

    NotificationClockConfig withPattern(String value) {
        // A manually edited merged pattern is the new source of truth. Keep the
        // date size because it is now the second-line style of this same block.
        return new NotificationClockConfig(enabled, value, sizeSp, offsetYDp,
                false, "MM/dd E", dateSizeSp, 0, listOffsetYDp, timeCentered, false,
                hideSettings);
    }

    NotificationClockConfig withSize(int value) {
        return new NotificationClockConfig(enabled, pattern, value, offsetYDp,
                dateEnabled, datePattern, dateSizeSp, dateOffsetYDp, listOffsetYDp,
                timeCentered, dateCentered, hideSettings);
    }

    NotificationClockConfig withOffsetY(int value) {
        return new NotificationClockConfig(enabled, pattern, sizeSp, value,
                dateEnabled, datePattern, dateSizeSp, dateOffsetYDp, listOffsetYDp,
                timeCentered, dateCentered, hideSettings);
    }

    NotificationClockConfig withDateEnabled(boolean value) {
        return new NotificationClockConfig(enabled, pattern, sizeSp, offsetYDp,
                value, datePattern, dateSizeSp, dateOffsetYDp, listOffsetYDp,
                timeCentered, dateCentered, hideSettings);
    }

    NotificationClockConfig withDatePattern(String value) {
        return new NotificationClockConfig(enabled, pattern, sizeSp, offsetYDp,
                dateEnabled, value, dateSizeSp, dateOffsetYDp, listOffsetYDp,
                timeCentered, dateCentered, hideSettings);
    }

    NotificationClockConfig withDateSize(int value) {
        return new NotificationClockConfig(enabled, pattern, sizeSp, offsetYDp,
                dateEnabled, datePattern, value, dateOffsetYDp, listOffsetYDp,
                timeCentered, dateCentered, hideSettings);
    }

    NotificationClockConfig withDateOffsetY(int value) {
        return new NotificationClockConfig(enabled, pattern, sizeSp, offsetYDp,
                dateEnabled, datePattern, dateSizeSp, value, listOffsetYDp,
                timeCentered, dateCentered, hideSettings);
    }

    NotificationClockConfig withListOffsetY(int value) {
        return new NotificationClockConfig(enabled, pattern, sizeSp, offsetYDp,
                dateEnabled, datePattern, dateSizeSp, dateOffsetYDp, value,
                timeCentered, dateCentered, hideSettings);
    }

    NotificationClockConfig withTimeCentered(boolean value) {
        return new NotificationClockConfig(enabled, pattern, sizeSp, offsetYDp,
                dateEnabled, datePattern, dateSizeSp, dateOffsetYDp, listOffsetYDp,
                value, dateCentered, hideSettings);
    }

    NotificationClockConfig withDateCentered(boolean value) {
        return new NotificationClockConfig(enabled, pattern, sizeSp, offsetYDp,
                dateEnabled, datePattern, dateSizeSp, dateOffsetYDp, listOffsetYDp,
                timeCentered, value, hideSettings);
    }

    NotificationClockConfig withHideSettings(boolean value) {
        return new NotificationClockConfig(enabled, pattern, sizeSp, offsetYDp,
                dateEnabled, datePattern, dateSizeSp, dateOffsetYDp, listOffsetYDp,
                timeCentered, dateCentered, value);
    }
}

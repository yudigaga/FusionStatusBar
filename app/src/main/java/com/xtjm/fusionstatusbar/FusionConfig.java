package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/** Shared display and status-bar layout settings. */
public final class FusionConfig {
    public static final String CONTENT_URI_STRING =
            "content://com.xtjm.fusionstatusbar.config/settings";
    static final String CONTROL_CENTER_PREVIEW_URI_STRING =
            "content://com.xtjm.fusionstatusbar.config/control_center_preview";
    public static final String METHOD_GET = "get";
    static final String METHOD_REPORT_APPLIED = "report_applied";
    static final String KEY_REVISION = "config_revision";
    static final String METHOD_PREVIEW_REQUEST = "control_center_preview_request";
    static final String METHOD_PREVIEW_STATE = "control_center_preview_state";
    static final String METHOD_PREVIEW_READY = "control_center_preview_ready";
    static final String METHOD_PREVIEW_CATALOG = "control_center_preview_catalog";
    static final String METHOD_PREVIEW_CLEAR = "control_center_preview_clear";
    static final String KEY_PREVIEW_REQUEST = "preview_request_generation";
    static final String KEY_PREVIEW_READY = "preview_ready_generation";
    static final String KEY_PREVIEW_LAYOUT = "preview_layout";
    static final String KEY_PREVIEW_CATALOG = "preview_tile_catalog";
    static final String KEY_PREVIEW_URI = "preview_uri";
    static final String KEY_SCHEMA_VERSION = "config_schema_version";
    static final int CONFIG_SCHEMA_VERSION = 4;

    public static final int SIDE_LEFT = 0;
    public static final int SIDE_RIGHT = 1;
    public static final int MAX_SPAN_OFFSET_DP = 24;

    static final String KEY_ICON_SCALE = "icon_scale";
    static final String KEY_STROKE_SCALE = "stroke_scale";
    static final String KEY_LEGACY_BATTERY_SCALE = "battery_scale";
    static final String KEY_DOUBLE_ROW = "double_row";
    static final String KEY_DOUBLE_ROW_SIDE = "double_row_side";
    static final String KEY_SPAN_ROWS = "all_icons_span_rows";
    static final String KEY_SPAN_OFFSET_X = "span_offset_x";
    static final String KEY_SPAN_OFFSET_Y = "span_offset_y";
    static final String KEY_CUSTOM_CLOCK = "custom_clock";
    static final String KEY_CLOCK_PATTERN = "clock_pattern";
    static final String KEY_SHOW_WEATHER = "show_weather";
    static final String KEY_WIFI_ICON = "wifi_icon";
    static final String KEY_STATUSBAR_HEIGHT = "statusbar_height";
    static final String KEY_CLOCK_SIDE = "clock_side";
    static final String KEY_CLOCK_ROW = "clock_row";
    static final String KEY_NOTIFICATION_SIDE = "notification_side";
    static final String KEY_NOTIFICATION_ROW = "notification_row";
    static final String KEY_SYSTEM_SIDE = "system_side";
    static final String KEY_SYSTEM_ROW = "system_row";
    static final String KEY_FUSION_SIDE = "fusion_side";
    static final String KEY_FUSION_ROW = "fusion_row";
    static final String KEY_SYSTEM_ICON_SCALE = "system_icon_scale";
    static final String KEY_NOTIFICATION_ICON_SCALE = "notification_icon_scale";

    public final int iconScale;
    public final long revision;
    public final int strokeScale;
    public final boolean doubleRow;
    public final int doubleRowSide;
    public final boolean spanRows;
    public final int spanOffsetX;
    public final int spanOffsetY;
    public final boolean customClock;
    public final String clockPattern;
    public final boolean showWeather;
    final TelemetryConfig telemetry;
    final NotificationClockConfig notificationClock;
    final ControlCenterConfig controlCenter;
    public final boolean wifiIcon;
    public final int statusBarHeight;
    public final int clockSide;
    public final int clockRow;
    public final int notificationSide;
    public final int notificationRow;
    public final int systemSide;
    public final int systemRow;
    public final int fusionSide;
    public final int fusionRow;
    public final int systemIconScale;
    public final int notificationIconScale;

    public FusionConfig(int iconScale, int strokeScale, boolean doubleRow, int doubleRowSide,
            boolean spanRows, boolean wifiIcon, int statusBarHeight, int clockSide, int clockRow,
            int notificationSide, int notificationRow, int systemSide, int systemRow,
            int spanOffsetX, int spanOffsetY, boolean customClock, String clockPattern,
            boolean showWeather, TelemetryConfig telemetry) {
        this(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows, wifiIcon,
                statusBarHeight, clockSide, clockRow, notificationSide, notificationRow,
                systemSide, systemRow, systemSide, systemRow, 100, 100, spanOffsetX,
                spanOffsetY, customClock, clockPattern, showWeather, telemetry);
    }

    FusionConfig(int iconScale, int strokeScale, boolean doubleRow, int doubleRowSide,
            boolean spanRows, boolean wifiIcon, int statusBarHeight, int clockSide, int clockRow,
            int notificationSide, int notificationRow, int systemSide, int systemRow,
            int fusionSide, int fusionRow, int systemIconScale, int notificationIconScale,
            int spanOffsetX, int spanOffsetY, boolean customClock, String clockPattern,
            boolean showWeather, TelemetryConfig telemetry,
            NotificationClockConfig notificationClock) {
        this(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows, wifiIcon,
                statusBarHeight, clockSide, clockRow, notificationSide, notificationRow,
                systemSide, systemRow, fusionSide, fusionRow, systemIconScale,
                notificationIconScale, spanOffsetX, spanOffsetY, customClock, clockPattern,
                showWeather, telemetry, notificationClock, ControlCenterConfig.defaults());
    }

    FusionConfig(int iconScale, int strokeScale, boolean doubleRow, int doubleRowSide,
            boolean spanRows, boolean wifiIcon, int statusBarHeight, int clockSide, int clockRow,
            int notificationSide, int notificationRow, int systemSide, int systemRow,
            int fusionSide, int fusionRow, int systemIconScale, int notificationIconScale,
            int spanOffsetX, int spanOffsetY, boolean customClock, String clockPattern,
            boolean showWeather, TelemetryConfig telemetry,
            NotificationClockConfig notificationClock, ControlCenterConfig controlCenter) {
        this.revision = 0L;
        this.iconScale = clampScale(iconScale);
        this.strokeScale = clampScale(strokeScale);
        this.doubleRow = doubleRow;
        this.doubleRowSide = normalizeSide(doubleRowSide);
        this.spanRows = spanRows;
        this.spanOffsetX = clampSpanOffset(spanOffsetX);
        this.spanOffsetY = clampSpanOffset(spanOffsetY);
        this.customClock = customClock;
        this.clockPattern = clockPattern == null ? ClockTextFormatter.DEFAULT_PATTERN
                : clockPattern.substring(0,
                        Math.min(clockPattern.length(), ClockTextFormatter.MAX_PATTERN_LENGTH));
        this.showWeather = showWeather;
        this.telemetry = telemetry == null ? TelemetryConfig.defaults() : telemetry;
        this.notificationClock = notificationClock == null
                ? NotificationClockConfig.defaults() : notificationClock;
        this.controlCenter = controlCenter == null
                ? ControlCenterConfig.defaults() : controlCenter;
        this.wifiIcon = wifiIcon;
        this.statusBarHeight = clampStatusBarHeight(statusBarHeight);
        this.clockSide = normalizeSide(clockSide);
        this.clockRow = normalizeRow(clockRow);
        this.notificationSide = normalizeSide(notificationSide);
        this.notificationRow = normalizeRow(notificationRow);
        this.systemSide = normalizeSide(systemSide);
        this.systemRow = normalizeRow(systemRow);
        this.fusionSide = normalizeSide(fusionSide);
        this.fusionRow = normalizeRow(fusionRow);
        this.systemIconScale = clampScale(systemIconScale);
        this.notificationIconScale = clampScale(notificationIconScale);
    }

    FusionConfig(int iconScale, int strokeScale, boolean doubleRow, int doubleRowSide,
            boolean spanRows, boolean wifiIcon, int statusBarHeight, int clockSide, int clockRow,
            int notificationSide, int notificationRow, int systemSide, int systemRow,
            int fusionSide, int fusionRow, int systemIconScale, int notificationIconScale,
            int spanOffsetX, int spanOffsetY, boolean customClock, String clockPattern,
            boolean showWeather, TelemetryConfig telemetry) {
        this(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows, wifiIcon,
                statusBarHeight, clockSide, clockRow, notificationSide, notificationRow,
                systemSide, systemRow, fusionSide, fusionRow, systemIconScale,
                notificationIconScale, spanOffsetX, spanOffsetY, customClock, clockPattern,
                showWeather, telemetry, NotificationClockConfig.defaults());
    }

    public static FusionConfig defaults() {
        return new FusionConfig(100, 100, false, SIDE_RIGHT, false, true, 48,
                SIDE_LEFT, 0, SIDE_LEFT, 1, SIDE_RIGHT, 0, 0, 0,
                false, ClockTextFormatter.DEFAULT_PATTERN, false, TelemetryConfig.defaults());
    }

    public static Uri contentUri() {
        return Uri.parse(CONTENT_URI_STRING);
    }

    static Uri controlCenterPreviewUri() {
        return Uri.parse(CONTROL_CENTER_PREVIEW_URI_STRING);
    }

    static Uri controlCenterPreviewUri(long generation) {
        return controlCenterPreviewUri().buildUpon()
                .appendQueryParameter("generation", Long.toString(generation)).build();
    }

    static long requestControlCenterPreview(Context context) {
        try {
            Bundle result = context.getContentResolver().call(controlCenterPreviewUri(),
                    METHOD_PREVIEW_REQUEST, null, null);
            long generation = result == null ? 0L
                    : result.getLong(KEY_PREVIEW_REQUEST, 0L);
            Log.i("FusionStatusBar", "control center preview request generation="
                    + generation);
            return generation;
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "control center preview request failed: "
                    + error.getClass().getSimpleName());
            return 0L;
        }
    }

    static void clearControlCenterPreview(Context context) {
        try {
            context.getContentResolver().call(controlCenterPreviewUri(),
                    METHOD_PREVIEW_CLEAR, null, null);
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "control center preview clear failed: "
                    + error.getClass().getSimpleName());
        }
    }

    static Bundle controlCenterPreviewState(Context context) {
        try {
            return context.getContentResolver().call(controlCenterPreviewUri(),
                    METHOD_PREVIEW_STATE, null, null);
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "control center preview state failed: "
                    + error.getClass().getSimpleName());
            return null;
        }
    }

    static boolean markControlCenterPreviewReady(Context context, long generation, String layout) {
        return markControlCenterPreviewReady(context, generation, layout, "");
    }

    static boolean markControlCenterPreviewReady(Context context, long generation, String layout,
            String catalog) {
        try {
            Bundle values = new Bundle();
            values.putLong(KEY_PREVIEW_READY, generation);
            values.putString(KEY_PREVIEW_LAYOUT, layout == null ? "" : layout);
            values.putString(KEY_PREVIEW_CATALOG, catalog == null ? "" : catalog);
            Bundle result = context.getContentResolver().call(controlCenterPreviewUri(),
                    METHOD_PREVIEW_READY, null, values);
            return result != null && result.getLong(KEY_PREVIEW_READY, 0L) == generation;
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "control center preview ready failed: "
                    + error.getClass().getSimpleName());
            return false;
        }
    }

    static boolean refreshControlCenterPreviewCatalog(Context context, long generation,
            String catalog) {
        try {
            Bundle values = new Bundle();
            values.putLong(KEY_PREVIEW_READY, generation);
            values.putString(KEY_PREVIEW_CATALOG, catalog == null ? "" : catalog);
            Bundle result = context.getContentResolver().call(controlCenterPreviewUri(),
                    METHOD_PREVIEW_CATALOG, null, values);
            return result != null && result.getLong(KEY_PREVIEW_READY, 0L) == generation;
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "control center preview catalog refresh failed: "
                    + error.getClass().getSimpleName());
            return false;
        }
    }

    public static FusionConfig read(Context context) {
        if (context == null) {
            return defaults();
        }
        FusionConfig providerConfig = readFromProvider(context);
        if (providerConfig != null) return providerConfig;
        try {
            Context packageContext = context.createPackageContext(
                    "com.xtjm.fusionstatusbar", Context.CONTEXT_IGNORE_SECURITY);
            return FusionConfigStore.read(packageContext);
        } catch (Throwable ignored) {
            return defaults();
        }
    }

    static FusionConfig readFromProvider(Context context) {
        if (context == null) return null;
        try {
            Bundle request = new Bundle();
            request.putString(FusionActivationStatus.KEY_RUNTIME_SESSION,
                    FusionActivationStatus.runtimeSession());
            Bundle values = context.getContentResolver().call(
                    contentUri(), METHOD_GET, null, request);
            if (values != null) {
                return fromBundle(values);
            }
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "config provider read failed: "
                    + error.getClass().getSimpleName());
        }
        return null;
    }

    public static FusionConfig fromBundle(Bundle values) {
        FusionConfig fallback = defaults();
        if (values == null) {
            return fallback;
        }
        values = FusionConfigStore.sanitize(values);
        if (values.getInt(KEY_SCHEMA_VERSION, 0) > CONFIG_SCHEMA_VERSION) return fallback;
        return new FusionConfig(
                values.getInt(KEY_ICON_SCALE,
                        values.getInt(KEY_LEGACY_BATTERY_SCALE, fallback.iconScale)),
                values.getInt(KEY_STROKE_SCALE, fallback.strokeScale),
                values.getBoolean(KEY_DOUBLE_ROW, fallback.doubleRow),
                values.getInt(KEY_DOUBLE_ROW_SIDE, fallback.doubleRowSide),
                values.getBoolean(KEY_SPAN_ROWS, fallback.spanRows),
                values.getBoolean(KEY_WIFI_ICON, fallback.wifiIcon),
                values.getInt(KEY_STATUSBAR_HEIGHT, fallback.statusBarHeight),
                values.getInt(KEY_CLOCK_SIDE, fallback.clockSide),
                values.getInt(KEY_CLOCK_ROW, fallback.clockRow),
                values.getInt(KEY_NOTIFICATION_SIDE, fallback.notificationSide),
                values.getInt(KEY_NOTIFICATION_ROW, fallback.notificationRow),
                values.getInt(KEY_SYSTEM_SIDE, fallback.systemSide),
                values.getInt(KEY_SYSTEM_ROW, fallback.systemRow),
                values.getInt(KEY_FUSION_SIDE,
                        values.getInt(KEY_SYSTEM_SIDE, fallback.fusionSide)),
                values.getInt(KEY_FUSION_ROW,
                        values.getInt(KEY_SYSTEM_ROW, fallback.fusionRow)),
                values.getInt(KEY_SYSTEM_ICON_SCALE, fallback.systemIconScale),
                values.getInt(KEY_NOTIFICATION_ICON_SCALE, fallback.notificationIconScale),
                values.getInt(KEY_SPAN_OFFSET_X, fallback.spanOffsetX),
                values.getInt(KEY_SPAN_OFFSET_Y, fallback.spanOffsetY),
                values.getBoolean(KEY_CUSTOM_CLOCK, fallback.customClock),
                values.getString(KEY_CLOCK_PATTERN, fallback.clockPattern),
                values.getBoolean(KEY_SHOW_WEATHER, fallback.showWeather),
                TelemetryConfig.fromBundle(values), NotificationClockConfig.fromBundle(values),
                ControlCenterConfig.fromBundle(values)).withRevision(
                        values.getLong(KEY_REVISION, 0L));
    }

    public Bundle toBundle() {
        Bundle values = new Bundle();
        values.putLong(KEY_REVISION, revision);
        values.putInt(KEY_SCHEMA_VERSION, CONFIG_SCHEMA_VERSION);
        values.putInt(KEY_ICON_SCALE, iconScale);
        values.putInt(KEY_STROKE_SCALE, strokeScale);
        values.putBoolean(KEY_DOUBLE_ROW, doubleRow);
        values.putInt(KEY_DOUBLE_ROW_SIDE, doubleRowSide);
        values.putBoolean(KEY_SPAN_ROWS, spanRows);
        values.putInt(KEY_SPAN_OFFSET_X, spanOffsetX);
        values.putInt(KEY_SPAN_OFFSET_Y, spanOffsetY);
        values.putBoolean(KEY_CUSTOM_CLOCK, customClock);
        values.putString(KEY_CLOCK_PATTERN, clockPattern);
        values.putBoolean(KEY_SHOW_WEATHER, showWeather);
        telemetry.writeTo(values);
        notificationClock.writeTo(values);
        controlCenter.writeTo(values);
        values.putBoolean(KEY_WIFI_ICON, wifiIcon);
        values.putInt(KEY_STATUSBAR_HEIGHT, statusBarHeight);
        values.putInt(KEY_CLOCK_SIDE, clockSide);
        values.putInt(KEY_CLOCK_ROW, clockRow);
        values.putInt(KEY_NOTIFICATION_SIDE, notificationSide);
        values.putInt(KEY_NOTIFICATION_ROW, notificationRow);
        values.putInt(KEY_SYSTEM_SIDE, systemSide);
        values.putInt(KEY_SYSTEM_ROW, systemRow);
        values.putInt(KEY_FUSION_SIDE, fusionSide);
        values.putInt(KEY_FUSION_ROW, fusionRow);
        values.putInt(KEY_SYSTEM_ICON_SCALE, systemIconScale);
        values.putInt(KEY_NOTIFICATION_ICON_SCALE, notificationIconScale);
        return values;
    }

    private FusionConfig(FusionConfig source, long revision) {
        this.revision = Math.max(0L, revision);
        iconScale = source.iconScale;
        strokeScale = source.strokeScale;
        doubleRow = source.doubleRow;
        doubleRowSide = source.doubleRowSide;
        spanRows = source.spanRows;
        spanOffsetX = source.spanOffsetX;
        spanOffsetY = source.spanOffsetY;
        customClock = source.customClock;
        clockPattern = source.clockPattern;
        showWeather = source.showWeather;
        telemetry = source.telemetry;
        notificationClock = source.notificationClock;
        controlCenter = source.controlCenter;
        wifiIcon = source.wifiIcon;
        statusBarHeight = source.statusBarHeight;
        clockSide = source.clockSide;
        clockRow = source.clockRow;
        notificationSide = source.notificationSide;
        notificationRow = source.notificationRow;
        systemSide = source.systemSide;
        systemRow = source.systemRow;
        fusionSide = source.fusionSide;
        fusionRow = source.fusionRow;
        systemIconScale = source.systemIconScale;
        notificationIconScale = source.notificationIconScale;
    }

    FusionConfig withRevision(long revision) {
        return new FusionConfig(this, revision);
    }

    public FusionConfig withIconScale(int value) {
        return new FusionConfig(value, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withStrokeScale(int value) {
        return new FusionConfig(iconScale, value, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withDoubleRow(boolean value) {
        return new FusionConfig(iconScale, strokeScale, value, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withDoubleRowSide(int value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, value, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withSpanRows(boolean value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, value,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withSpanOffsetX(int value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, value, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withSpanOffsetY(int value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, value,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withCustomClock(boolean value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                value, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withClockPattern(String value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, value, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withShowWeather(boolean value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, value, telemetry, notificationClock,
                controlCenter);
    }

    FusionConfig withTelemetry(TelemetryConfig value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, value, notificationClock,
                controlCenter);
    }

    FusionConfig withNotificationClock(NotificationClockConfig value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, value, controlCenter);
    }

    FusionConfig withControlCenter(ControlCenterConfig value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock, value);
    }

    public FusionConfig withWifiIcon(boolean value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                value, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withStatusBarHeight(int value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, value, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withSystemPosition(int side, int row) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, side, row, fusionSide, fusionRow,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withFusionPosition(int side, int row) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, side, row,
                systemIconScale, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withSystemIconScale(int value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                value, notificationIconScale, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withNotificationIconScale(int value) {
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide, spanRows,
                wifiIcon, statusBarHeight, clockSide, clockRow, notificationSide,
                notificationRow, systemSide, systemRow, fusionSide, fusionRow,
                systemIconScale, value, spanOffsetX, spanOffsetY,
                customClock, clockPattern, showWeather, telemetry, notificationClock,
                controlCenter);
    }

    public FusionConfig withElementPosition(int element, int side, int row) {
        int nextClockSide = clockSide;
        int nextClockRow = clockRow;
        int nextNotificationSide = notificationSide;
        int nextNotificationRow = notificationRow;
        int nextSystemSide = systemSide;
        int nextSystemRow = systemRow;
        if (element == 0) {
            nextClockSide = side;
            nextClockRow = row;
        } else if (element == 1) {
            nextNotificationSide = side;
            nextNotificationRow = row;
        } else {
            nextSystemSide = side;
            nextSystemRow = row;
        }
        return new FusionConfig(iconScale, strokeScale, doubleRow, doubleRowSide,
                spanRows, wifiIcon, statusBarHeight, nextClockSide, nextClockRow,
                nextNotificationSide, nextNotificationRow, nextSystemSide, nextSystemRow,
                fusionSide, fusionRow, systemIconScale, notificationIconScale,
                spanOffsetX, spanOffsetY, customClock, clockPattern, showWeather,
                telemetry, notificationClock, controlCenter);
    }

    public static int clampStatusBarHeight(int value) {
        return Math.max(32, Math.min(96, value));
    }

    static int clampScale(int value) {
        return Math.max(50, Math.min(150, value));
    }

    static int clampSpanOffset(int value) {
        return Math.max(-MAX_SPAN_OFFSET_DP, Math.min(MAX_SPAN_OFFSET_DP, value));
    }

    private static int normalizeSide(int value) {
        return value == SIDE_LEFT ? SIDE_LEFT : SIDE_RIGHT;
    }

    private static int normalizeRow(int value) {
        return value <= 0 ? 0 : 1;
    }
}

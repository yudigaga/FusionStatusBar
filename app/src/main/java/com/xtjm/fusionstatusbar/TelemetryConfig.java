package com.xtjm.fusionstatusbar;

import android.content.SharedPreferences;
import android.os.Bundle;

/** Immutable settings for three two-line status-bar readouts. */
final class TelemetryConfig {
    static final int TEMPERATURES = 0;
    static final int POWER_CURRENT = 1;
    static final int NET_SPEED = 2;
    static final int COUNT = 3;
    static final int SIDE_LEFT = 0;
    static final int SIDE_RIGHT = 1;
    static final int ALIGN_DEFAULT = 0;
    static final int ALIGN_LEFT = 1;
    static final int ALIGN_CENTER = 2;
    static final int ALIGN_RIGHT = 3;

    private static final String KEY_GROUP_ENABLED = "telemetry_group_enabled_";
    private static final String KEY_GROUP_POSITION = "telemetry_group_position_";
    private static final String KEY_GROUP_SIDE = "telemetry_group_side_";
    private static final String KEY_GROUP_TEXT_SIZE = "telemetry_group_text_size_";
    private static final String KEY_GROUP_ALIGNMENT = "telemetry_group_alignment_";
    private static final String KEY_GROUP_FIXED_WIDTH = "telemetry_group_fixed_width_";
    private static final String KEY_GROUP_LEFT_MARGIN = "telemetry_group_left_margin_";
    private static final String KEY_GROUP_RIGHT_MARGIN = "telemetry_group_right_margin_";
    private static final String KEY_GROUP_VERTICAL_OFFSET = "telemetry_group_vertical_offset_";
    private static final String KEY_GROUP_BOLD = "telemetry_group_bold_";
    private static final String KEY_GROUP_LINE_SPACING = "telemetry_group_line_spacing_";
    private static final String KEY_OLD_ENABLED = "telemetry_enabled_";
    private static final String KEY_OLD_POSITION = "telemetry_position_";
    private static final String KEY_DUAL_SPEED = "telemetry_dual_speed";
    private static final String KEY_CURRENT_MA = "telemetry_current_ma";
    private static final String KEY_CURRENT_POSITIVE = "telemetry_current_positive";
    private static final String KEY_TEXT_SIZE = "telemetry_text_size";

    private final boolean[] enabled;
    private final int[] positions;
    final boolean dualSpeed;
    final boolean currentInMilliamps;
    final boolean currentPositive;
    final int textSizeSp;
    private final int[] groupTextSizeSp;
    private final int[] alignment;
    private final int[] fixedWidthDp;
    private final int[] leftMarginDp;
    private final int[] rightMarginDp;
    private final int[] verticalOffsetDp;
    private final boolean[] bold;
    private final int[] lineSpacingDp;

    private TelemetryConfig(boolean[] enabled, int[] positions, boolean dualSpeed,
            boolean currentInMilliamps, boolean currentPositive, int textSizeSp) {
        this(enabled, positions, dualSpeed, currentInMilliamps, currentPositive, textSizeSp,
                uniformInt(textSizeSp), new int[COUNT], new int[COUNT], new int[COUNT],
                new int[COUNT], new int[COUNT], new boolean[COUNT], new int[COUNT]);
    }

    private TelemetryConfig(boolean[] enabled, int[] positions, boolean dualSpeed,
            boolean currentInMilliamps, boolean currentPositive, int textSizeSp,
            int[] groupTextSizeSp, int[] alignment, int[] fixedWidthDp,
            int[] leftMarginDp, int[] rightMarginDp, int[] verticalOffsetDp,
            boolean[] bold, int[] lineSpacingDp) {
        this.enabled = enabled.clone();
        this.positions = positions.clone();
        this.groupTextSizeSp = groupTextSizeSp.clone();
        this.alignment = alignment.clone();
        this.fixedWidthDp = fixedWidthDp.clone();
        this.leftMarginDp = leftMarginDp.clone();
        this.rightMarginDp = rightMarginDp.clone();
        this.verticalOffsetDp = verticalOffsetDp.clone();
        this.bold = bold.clone();
        this.lineSpacingDp = lineSpacingDp.clone();
        for (int i = 0; i < COUNT; i++) {
            this.positions[i] = normalizePosition(this.positions[i]);
            this.groupTextSizeSp[i] = clampTextSize(this.groupTextSizeSp[i], textSizeSp);
            this.alignment[i] = normalizeAlignment(this.alignment[i]);
            this.fixedWidthDp[i] = clampFixedWidth(this.fixedWidthDp[i]);
            this.leftMarginDp[i] = clampMargin(this.leftMarginDp[i]);
            this.rightMarginDp[i] = clampMargin(this.rightMarginDp[i]);
            this.verticalOffsetDp[i] = clampVerticalOffset(this.verticalOffsetDp[i]);
            this.lineSpacingDp[i] = clampLineSpacing(this.lineSpacingDp[i]);
        }
        this.dualSpeed = dualSpeed;
        this.currentInMilliamps = currentInMilliamps;
        this.currentPositive = currentPositive;
        this.textSizeSp = clampTextSize(textSizeSp, 10);
    }

    static TelemetryConfig defaults() {
        return new TelemetryConfig(new boolean[COUNT], new int[] {1, 3, 2},
                true, false, false, 10);
    }

    boolean enabled(int group) {
        return group >= 0 && group < COUNT && enabled[group];
    }

    int position(int group) {
        return group >= 0 && group < COUNT ? positions[group] : 0;
    }

    int textSize(int group) {
        return group >= 0 && group < COUNT ? groupTextSizeSp[group] : textSizeSp;
    }

    int alignment(int group) {
        return group >= 0 && group < COUNT ? alignment[group] : ALIGN_DEFAULT;
    }

    static int resolvedAlignment(int alignment, int side) {
        if (alignment != ALIGN_DEFAULT) {
            return alignment;
        }
        return side == SIDE_RIGHT ? ALIGN_RIGHT : ALIGN_LEFT;
    }

    int fixedWidth(int group) {
        return group >= 0 && group < COUNT ? fixedWidthDp[group] : 0;
    }

    int leftMargin(int group) {
        return group >= 0 && group < COUNT ? leftMarginDp[group] : 0;
    }

    int rightMargin(int group) {
        return group >= 0 && group < COUNT ? rightMarginDp[group] : 0;
    }

    int verticalOffset(int group) {
        return group >= 0 && group < COUNT ? verticalOffsetDp[group] : 0;
    }

    boolean bold(int group) {
        return group >= 0 && group < COUNT && bold[group];
    }

    int lineSpacing(int group) {
        return group >= 0 && group < COUNT ? lineSpacingDp[group] : 0;
    }

    static int side(int position) {
        return normalizePosition(position) / 2;
    }

    static int row(int position) {
        return normalizePosition(position) % 2;
    }

    boolean anyEnabled() {
        for (boolean value : enabled) {
            if (value) return true;
        }
        return false;
    }

    boolean needsBattery() {
        return enabled[TEMPERATURES] || enabled[POWER_CURRENT];
    }

    boolean needsGpu() {
        return enabled[TEMPERATURES];
    }

    float textSizeFor(int group, int statusHeightDp, boolean doubleRow) {
        float rowHeight = doubleRow ? statusHeightDp / 2f : statusHeightDp;
        int requestedSize = textSize(group);
        if (group == NET_SPEED && !dualSpeed) {
            return Math.min(requestedSize, Math.max(5f, rowHeight - 3f));
        }
        return Math.min(requestedSize, Math.max(4f, rowHeight / 2f - 2f));
    }

    TelemetryConfig withEnabled(int group, boolean value) {
        if (group < 0 || group >= COUNT) return this;
        boolean[] copy = enabled.clone();
        copy[group] = value;
        return copy(copy, positions, dualSpeed, currentInMilliamps, currentPositive,
                textSizeSp, groupTextSizeSp, alignment, fixedWidthDp, leftMarginDp,
                rightMarginDp, verticalOffsetDp, bold, lineSpacingDp);
    }

    TelemetryConfig withPosition(int group, int value) {
        if (group < 0 || group >= COUNT) return this;
        int[] copy = positions.clone();
        copy[group] = value;
        return copy(enabled, copy, dualSpeed, currentInMilliamps, currentPositive,
                textSizeSp, groupTextSizeSp, alignment, fixedWidthDp, leftMarginDp,
                rightMarginDp, verticalOffsetDp, bold, lineSpacingDp);
    }

    TelemetryConfig withDualSpeed(boolean value) {
        return copy(enabled, positions, value, currentInMilliamps, currentPositive,
                textSizeSp, groupTextSizeSp, alignment, fixedWidthDp, leftMarginDp,
                rightMarginDp, verticalOffsetDp, bold, lineSpacingDp);
    }

    TelemetryConfig withCurrentInMilliamps(boolean value) {
        return copy(enabled, positions, dualSpeed, value, currentPositive,
                textSizeSp, groupTextSizeSp, alignment, fixedWidthDp, leftMarginDp,
                rightMarginDp, verticalOffsetDp, bold, lineSpacingDp);
    }

    TelemetryConfig withCurrentPositive(boolean value) {
        return copy(enabled, positions, dualSpeed, currentInMilliamps, value,
                textSizeSp, groupTextSizeSp, alignment, fixedWidthDp, leftMarginDp,
                rightMarginDp, verticalOffsetDp, bold, lineSpacingDp);
    }

    TelemetryConfig withTextSize(int value) {
        int normalized = clampTextSize(value, 10);
        return copy(enabled, positions, dualSpeed, currentInMilliamps, currentPositive,
                normalized, uniformInt(normalized), alignment, fixedWidthDp, leftMarginDp,
                rightMarginDp, verticalOffsetDp, bold, lineSpacingDp);
    }

    TelemetryConfig withGroupTextSize(int group, int value) {
        if (group < 0 || group >= COUNT) return this;
        int[] copy = groupTextSizeSp.clone();
        copy[group] = value;
        return copy(enabled, positions, dualSpeed, currentInMilliamps, currentPositive,
                textSizeSp, copy, alignment, fixedWidthDp, leftMarginDp,
                rightMarginDp, verticalOffsetDp, bold, lineSpacingDp);
    }

    TelemetryConfig withAlignment(int group, int value) {
        if (group < 0 || group >= COUNT) return this;
        int[] copy = alignment.clone();
        copy[group] = value;
        return copy(enabled, positions, dualSpeed, currentInMilliamps, currentPositive,
                textSizeSp, groupTextSizeSp, copy, fixedWidthDp, leftMarginDp,
                rightMarginDp, verticalOffsetDp, bold, lineSpacingDp);
    }

    TelemetryConfig withFixedWidth(int group, int value) {
        if (group < 0 || group >= COUNT) return this;
        int[] copy = fixedWidthDp.clone();
        copy[group] = value;
        return copy(enabled, positions, dualSpeed, currentInMilliamps, currentPositive,
                textSizeSp, groupTextSizeSp, alignment, copy, leftMarginDp,
                rightMarginDp, verticalOffsetDp, bold, lineSpacingDp);
    }

    TelemetryConfig withLeftMargin(int group, int value) {
        if (group < 0 || group >= COUNT) return this;
        int[] copy = leftMarginDp.clone();
        copy[group] = value;
        return copy(enabled, positions, dualSpeed, currentInMilliamps, currentPositive,
                textSizeSp, groupTextSizeSp, alignment, fixedWidthDp, copy,
                rightMarginDp, verticalOffsetDp, bold, lineSpacingDp);
    }

    TelemetryConfig withRightMargin(int group, int value) {
        if (group < 0 || group >= COUNT) return this;
        int[] copy = rightMarginDp.clone();
        copy[group] = value;
        return copy(enabled, positions, dualSpeed, currentInMilliamps, currentPositive,
                textSizeSp, groupTextSizeSp, alignment, fixedWidthDp, leftMarginDp,
                copy, verticalOffsetDp, bold, lineSpacingDp);
    }

    TelemetryConfig withVerticalOffset(int group, int value) {
        if (group < 0 || group >= COUNT) return this;
        int[] copy = verticalOffsetDp.clone();
        copy[group] = value;
        return copy(enabled, positions, dualSpeed, currentInMilliamps, currentPositive,
                textSizeSp, groupTextSizeSp, alignment, fixedWidthDp, leftMarginDp,
                rightMarginDp, copy, bold, lineSpacingDp);
    }

    TelemetryConfig withBold(int group, boolean value) {
        if (group < 0 || group >= COUNT) return this;
        boolean[] copy = bold.clone();
        copy[group] = value;
        return copy(enabled, positions, dualSpeed, currentInMilliamps, currentPositive,
                textSizeSp, groupTextSizeSp, alignment, fixedWidthDp, leftMarginDp,
                rightMarginDp, verticalOffsetDp, copy, lineSpacingDp);
    }

    TelemetryConfig withLineSpacing(int group, int value) {
        if (group < 0 || group >= COUNT) return this;
        int[] copy = lineSpacingDp.clone();
        copy[group] = value;
        return copy(enabled, positions, dualSpeed, currentInMilliamps, currentPositive,
                textSizeSp, groupTextSizeSp, alignment, fixedWidthDp, leftMarginDp,
                rightMarginDp, verticalOffsetDp, bold, copy);
    }

    static TelemetryConfig fromBundle(Bundle values) {
        TelemetryConfig fallback = defaults();
        if (values == null) return fallback;
        boolean[] enabled = new boolean[COUNT];
        int[] positions = new int[COUNT];
        if (values.containsKey(KEY_GROUP_POSITION + TEMPERATURES)) {
            for (int i = 0; i < COUNT; i++) {
                enabled[i] = values.getBoolean(KEY_GROUP_ENABLED + i, fallback.enabled(i));
                positions[i] = values.getInt(KEY_GROUP_POSITION + i, fallback.position(i));
            }
        } else if (values.containsKey(KEY_GROUP_SIDE + TEMPERATURES)) {
            for (int i = 0; i < COUNT; i++) {
                enabled[i] = values.getBoolean(KEY_GROUP_ENABLED + i, fallback.enabled(i));
                int side = values.getInt(KEY_GROUP_SIDE + i, side(fallback.position(i)));
                int oldMetric = i == TEMPERATURES ? 0 : i == POWER_CURRENT ? 2 : 4;
                int oldPosition = values.getInt(KEY_OLD_POSITION + oldMetric, fallback.position(i));
                positions[i] = migratedPosition(side, oldPosition);
            }
        } else if (values.containsKey(KEY_OLD_ENABLED + 0)) {
            boolean[] oldEnabled = new boolean[5];
            int[] oldPositions = {1, 3, 2, 0, 3};
            for (int i = 0; i < 5; i++) {
                oldEnabled[i] = values.getBoolean(KEY_OLD_ENABLED + i, false);
                oldPositions[i] = values.getInt(KEY_OLD_POSITION + i, oldPositions[i]);
            }
            migrate(oldEnabled, oldPositions, enabled, positions);
        } else {
            return fallback;
        }
        int textSize = values.getInt(KEY_TEXT_SIZE, fallback.textSizeSp);
        int[] groupTextSize = new int[COUNT];
        int[] alignment = new int[COUNT];
        int[] fixedWidth = new int[COUNT];
        int[] leftMargin = new int[COUNT];
        int[] rightMargin = new int[COUNT];
        int[] verticalOffset = new int[COUNT];
        boolean[] bold = new boolean[COUNT];
        int[] lineSpacing = new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            groupTextSize[i] = values.getInt(KEY_GROUP_TEXT_SIZE + i, textSize);
            alignment[i] = values.getInt(KEY_GROUP_ALIGNMENT + i, ALIGN_DEFAULT);
            fixedWidth[i] = values.getInt(KEY_GROUP_FIXED_WIDTH + i, 0);
            leftMargin[i] = values.getInt(KEY_GROUP_LEFT_MARGIN + i, 0);
            rightMargin[i] = values.getInt(KEY_GROUP_RIGHT_MARGIN + i, 0);
            verticalOffset[i] = values.getInt(KEY_GROUP_VERTICAL_OFFSET + i, 0);
            bold[i] = values.getBoolean(KEY_GROUP_BOLD + i, false);
            lineSpacing[i] = values.getInt(KEY_GROUP_LINE_SPACING + i, 0);
        }
        return new TelemetryConfig(enabled, positions,
                values.getBoolean(KEY_DUAL_SPEED, fallback.dualSpeed),
                values.getBoolean(KEY_CURRENT_MA, fallback.currentInMilliamps),
                values.getBoolean(KEY_CURRENT_POSITIVE, fallback.currentPositive),
                textSize, groupTextSize, alignment, fixedWidth, leftMargin, rightMargin,
                verticalOffset, bold, lineSpacing);
    }

    void writeTo(Bundle values) {
        for (int i = 0; i < COUNT; i++) {
            values.putBoolean(KEY_GROUP_ENABLED + i, enabled[i]);
            values.putInt(KEY_GROUP_POSITION + i, positions[i]);
            values.putInt(KEY_GROUP_TEXT_SIZE + i, groupTextSizeSp[i]);
            values.putInt(KEY_GROUP_ALIGNMENT + i, alignment[i]);
            values.putInt(KEY_GROUP_FIXED_WIDTH + i, fixedWidthDp[i]);
            values.putInt(KEY_GROUP_LEFT_MARGIN + i, leftMarginDp[i]);
            values.putInt(KEY_GROUP_RIGHT_MARGIN + i, rightMarginDp[i]);
            values.putInt(KEY_GROUP_VERTICAL_OFFSET + i, verticalOffsetDp[i]);
            values.putBoolean(KEY_GROUP_BOLD + i, bold[i]);
            values.putInt(KEY_GROUP_LINE_SPACING + i, lineSpacingDp[i]);
        }
        values.putBoolean(KEY_DUAL_SPEED, dualSpeed);
        values.putBoolean(KEY_CURRENT_MA, currentInMilliamps);
        values.putBoolean(KEY_CURRENT_POSITIVE, currentPositive);
        values.putInt(KEY_TEXT_SIZE, textSizeSp);
    }

    static TelemetryConfig read(SharedPreferences values) {
        TelemetryConfig fallback = defaults();
        boolean[] enabled = new boolean[COUNT];
        int[] positions = new int[COUNT];
        if (values.contains(KEY_GROUP_POSITION + TEMPERATURES)) {
            for (int i = 0; i < COUNT; i++) {
                enabled[i] = values.getBoolean(KEY_GROUP_ENABLED + i, fallback.enabled(i));
                positions[i] = values.getInt(KEY_GROUP_POSITION + i, fallback.position(i));
            }
        } else if (values.contains(KEY_GROUP_SIDE + TEMPERATURES)) {
            for (int i = 0; i < COUNT; i++) {
                enabled[i] = values.getBoolean(KEY_GROUP_ENABLED + i, fallback.enabled(i));
                int side = values.getInt(KEY_GROUP_SIDE + i, side(fallback.position(i)));
                int oldMetric = i == TEMPERATURES ? 0 : i == POWER_CURRENT ? 2 : 4;
                int oldPosition = values.getInt(KEY_OLD_POSITION + oldMetric, fallback.position(i));
                positions[i] = migratedPosition(side, oldPosition);
            }
        } else if (values.contains(KEY_OLD_ENABLED + 0)) {
            boolean[] oldEnabled = new boolean[5];
            int[] oldPositions = {1, 3, 2, 0, 3};
            for (int i = 0; i < 5; i++) {
                oldEnabled[i] = values.getBoolean(KEY_OLD_ENABLED + i, false);
                oldPositions[i] = values.getInt(KEY_OLD_POSITION + i, oldPositions[i]);
            }
            migrate(oldEnabled, oldPositions, enabled, positions);
        } else {
            return fallback;
        }
        int textSize = values.getInt(KEY_TEXT_SIZE, fallback.textSizeSp);
        int[] groupTextSize = new int[COUNT];
        int[] alignment = new int[COUNT];
        int[] fixedWidth = new int[COUNT];
        int[] leftMargin = new int[COUNT];
        int[] rightMargin = new int[COUNT];
        int[] verticalOffset = new int[COUNT];
        boolean[] bold = new boolean[COUNT];
        int[] lineSpacing = new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            groupTextSize[i] = values.getInt(KEY_GROUP_TEXT_SIZE + i, textSize);
            alignment[i] = values.getInt(KEY_GROUP_ALIGNMENT + i, ALIGN_DEFAULT);
            fixedWidth[i] = values.getInt(KEY_GROUP_FIXED_WIDTH + i, 0);
            leftMargin[i] = values.getInt(KEY_GROUP_LEFT_MARGIN + i, 0);
            rightMargin[i] = values.getInt(KEY_GROUP_RIGHT_MARGIN + i, 0);
            verticalOffset[i] = values.getInt(KEY_GROUP_VERTICAL_OFFSET + i, 0);
            bold[i] = values.getBoolean(KEY_GROUP_BOLD + i, false);
            lineSpacing[i] = values.getInt(KEY_GROUP_LINE_SPACING + i, 0);
        }
        return new TelemetryConfig(enabled, positions,
                values.getBoolean(KEY_DUAL_SPEED, fallback.dualSpeed),
                values.getBoolean(KEY_CURRENT_MA, fallback.currentInMilliamps),
                values.getBoolean(KEY_CURRENT_POSITIVE, fallback.currentPositive),
                textSize, groupTextSize, alignment, fixedWidth, leftMargin, rightMargin,
                verticalOffset, bold, lineSpacing);
    }

    void writeTo(SharedPreferences.Editor editor) {
        for (int i = 0; i < COUNT; i++) {
            editor.putBoolean(KEY_GROUP_ENABLED + i, enabled[i]);
            editor.putInt(KEY_GROUP_POSITION + i, positions[i]);
            editor.putInt(KEY_GROUP_TEXT_SIZE + i, groupTextSizeSp[i]);
            editor.putInt(KEY_GROUP_ALIGNMENT + i, alignment[i]);
            editor.putInt(KEY_GROUP_FIXED_WIDTH + i, fixedWidthDp[i]);
            editor.putInt(KEY_GROUP_LEFT_MARGIN + i, leftMarginDp[i]);
            editor.putInt(KEY_GROUP_RIGHT_MARGIN + i, rightMarginDp[i]);
            editor.putInt(KEY_GROUP_VERTICAL_OFFSET + i, verticalOffsetDp[i]);
            editor.putBoolean(KEY_GROUP_BOLD + i, bold[i]);
            editor.putInt(KEY_GROUP_LINE_SPACING + i, lineSpacingDp[i]);
        }
        editor.putBoolean(KEY_DUAL_SPEED, dualSpeed);
        editor.putBoolean(KEY_CURRENT_MA, currentInMilliamps);
        editor.putBoolean(KEY_CURRENT_POSITIVE, currentPositive);
        editor.putInt(KEY_TEXT_SIZE, textSizeSp);
    }

    static TelemetryConfig fromLegacy(boolean[] oldEnabled, int[] oldPositions) {
        boolean[] enabled = new boolean[COUNT];
        int[] positions = new int[COUNT];
        migrate(oldEnabled, oldPositions, enabled, positions);
        return new TelemetryConfig(enabled, positions, true, false, false, 10);
    }

    private static void migrate(boolean[] oldEnabled, int[] oldPositions,
            boolean[] enabled, int[] positions) {
        enabled[TEMPERATURES] = oldEnabled[0] || oldEnabled[1];
        enabled[POWER_CURRENT] = oldEnabled[2] || oldEnabled[3];
        enabled[NET_SPEED] = oldEnabled[4];
        positions[TEMPERATURES] = normalizePosition(
                oldEnabled[0] ? oldPositions[0] : oldPositions[1]);
        positions[POWER_CURRENT] = normalizePosition(
                oldEnabled[2] ? oldPositions[2] : oldPositions[3]);
        positions[NET_SPEED] = normalizePosition(oldPositions[4]);
    }

    private static int normalizePosition(int value) {
        return Math.max(0, Math.min(3, value));
    }

    private static TelemetryConfig copy(boolean[] enabled, int[] positions, boolean dualSpeed,
            boolean currentInMilliamps, boolean currentPositive, int textSizeSp,
            int[] groupTextSizeSp, int[] alignment, int[] fixedWidthDp,
            int[] leftMarginDp, int[] rightMarginDp, int[] verticalOffsetDp,
            boolean[] bold, int[] lineSpacingDp) {
        return new TelemetryConfig(enabled, positions, dualSpeed, currentInMilliamps,
                currentPositive, textSizeSp, groupTextSizeSp, alignment, fixedWidthDp,
                leftMarginDp, rightMarginDp, verticalOffsetDp, bold, lineSpacingDp);
    }

    private static int[] uniformInt(int value) {
        int[] result = new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            result[i] = value;
        }
        return result;
    }

    private static int clampTextSize(int value, int fallback) {
        int normalized = value <= 0 ? fallback : value;
        return Math.max(5, Math.min(14, normalized));
    }

    private static int normalizeAlignment(int value) {
        return Math.max(ALIGN_DEFAULT, Math.min(ALIGN_RIGHT, value));
    }

    private static int clampFixedWidth(int value) {
        return Math.max(0, Math.min(240, value));
    }

    private static int clampMargin(int value) {
        return Math.max(0, Math.min(32, value));
    }

    private static int clampVerticalOffset(int value) {
        return Math.max(-24, Math.min(24, value));
    }

    private static int clampLineSpacing(int value) {
        return Math.max(-4, Math.min(12, value));
    }

    static int migratedPosition(int side, int oldPosition) {
        return (side == SIDE_RIGHT ? 2 : 0) + row(oldPosition);
    }
}

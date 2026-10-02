package com.xtjm.fusionstatusbar;

/** Immutable placement plan shared by the SystemUI adapter and the local preview. */
final class StatusBarLayoutPlan {
    static final int CLOCK = 0;
    static final int NOTIFICATIONS = 1;
    static final int SYSTEM = 2;

    final boolean doubleRow;
    final boolean spanFusion;
    final int clockSide;
    final int clockRow;
    final int notificationSide;
    final int notificationRow;
    final int systemSide;
    final int systemRow;
    final int fusionSide;
    final int fusionRow;

    private StatusBarLayoutPlan(FusionConfig config) {
        doubleRow = config.doubleRow;
        spanFusion = doubleRow && config.spanRows;
        clockSide = config.clockSide;
        clockRow = row(config.clockRow);
        notificationSide = config.notificationSide;
        notificationRow = row(config.notificationRow);
        systemSide = config.systemSide;
        systemRow = row(config.systemRow);
        fusionSide = config.fusionSide;
        fusionRow = row(config.fusionRow);
    }

    static StatusBarLayoutPlan from(FusionConfig config) {
        return new StatusBarLayoutPlan(config == null ? FusionConfig.defaults() : config);
    }

    int sideFor(int element) {
        if (element == CLOCK) return clockSide;
        if (element == NOTIFICATIONS) return notificationSide;
        return systemSide;
    }

    int rowFor(int element) {
        if (element == CLOCK) return clockRow;
        if (element == NOTIFICATIONS) return notificationRow;
        return systemRow;
    }

    int telemetryRow(int position) {
        return doubleRow ? TelemetryConfig.row(position) : 0;
    }

    int systemChildRow(boolean batteryContainer) {
        if (spanFusion && batteryContainer) {
            return 0;
        }
        return batteryContainer ? fusionRow : systemRow;
    }

    boolean fusionUsesRow(int row) {
        return spanFusion || fusionRow == row;
    }

    boolean nativeElementAt(int side, int row) {
        return (clockSide == side && clockRow == row)
                || (notificationSide == side && notificationRow == row)
                || (systemSide == side && systemRow == row);
    }

    private int row(int value) {
        return doubleRow ? Math.max(0, Math.min(1, value)) : 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StatusBarLayoutPlan plan)) return false;
        return doubleRow == plan.doubleRow
                && spanFusion == plan.spanFusion
                && clockSide == plan.clockSide && clockRow == plan.clockRow
                && notificationSide == plan.notificationSide
                && notificationRow == plan.notificationRow
                && systemSide == plan.systemSide && systemRow == plan.systemRow
                && fusionSide == plan.fusionSide && fusionRow == plan.fusionRow;
    }

    @Override
    public int hashCode() {
        int result = doubleRow ? 1 : 0;
        result = 31 * result + (spanFusion ? 1 : 0);
        result = 31 * result + clockSide;
        result = 31 * result + clockRow;
        result = 31 * result + notificationSide;
        result = 31 * result + notificationRow;
        result = 31 * result + systemSide;
        result = 31 * result + systemRow;
        result = 31 * result + fusionSide;
        return 31 * result + fusionRow;
    }
}

package com.xtjm.fusionstatusbar;

/** Shared capability-to-status decisions; installed handles remain the source of capability. */
final class RuntimeApplicationPolicy {
    record Result(String feature, String state, String reason) { }

    private RuntimeApplicationPolicy() { }

    static Result statusBar(boolean adapterAvailable, boolean rootAttached,
            boolean customClockEnabled, boolean clockAttached) {
        if (!adapterAvailable) {
            return statusBar(FusionActivationStatus.DEGRADED, "battery_adapter_unavailable");
        }
        if (!rootAttached || (customClockEnabled && !clockAttached)) {
            return statusBar(FusionActivationStatus.WAITING, "status_bar_mount_pending");
        }
        return statusBar(FusionActivationStatus.APPLIED, "status_bar_applied");
    }

    static Result notificationClock(boolean enabled, boolean adapterAvailable, boolean attached) {
        if (!enabled || attached) {
            return notification(FusionActivationStatus.APPLIED, "notification_clock_applied");
        }
        return adapterAvailable
                ? notification(FusionActivationStatus.WAITING, "notification_clock_mount_pending")
                : notification(FusionActivationStatus.DEGRADED, "notification_clock_adapter_unavailable");
    }

    static Result controlCenterAvailability(boolean enabled, boolean hooksInstalled) {
        if (!enabled) {
            return controlCenter(FusionActivationStatus.APPLIED, "native_restored");
        }
        if (!hooksInstalled) {
            return controlCenter(FusionActivationStatus.DEGRADED, "required_control_center_hooks_unavailable");
        }
        return controlCenter(FusionActivationStatus.WAITING, "control_center_mount_pending");
    }

    static Result systemSurface(String feature, boolean enabled, boolean adapterAvailable,
            boolean applied, boolean mounted) {
        if (!enabled) return new Result(feature, FusionActivationStatus.APPLIED, "native_restored");
        if (!adapterAvailable) return new Result(feature, FusionActivationStatus.DEGRADED,
                "surface_hooks_unavailable");
        if (applied) return new Result(feature, FusionActivationStatus.APPLIED, "surface_applied");
        return new Result(feature, mounted ? FusionActivationStatus.DEGRADED : FusionActivationStatus.WAITING,
                mounted ? "surface_application_incomplete" : "surface_mount_pending");
    }

    private static Result statusBar(String state, String reason) {
        return new Result(FusionActivationStatus.FEATURE_STATUS_BAR, state, reason);
    }

    private static Result notification(String state, String reason) {
        return new Result(FusionActivationStatus.FEATURE_NOTIFICATION_CLOCK, state, reason);
    }

    private static Result controlCenter(String state, String reason) {
        return new Result(FusionActivationStatus.FEATURE_CONTROL_CENTER, state, reason);
    }
}

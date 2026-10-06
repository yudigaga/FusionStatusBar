package com.xtjm.fusionstatusbar;

import android.os.Bundle;
import java.util.Objects;
import java.util.Arrays;

/** Dependencies for one immutable config transition, excluding revision metadata. */
final class RuntimeConfigDiff {
    final boolean statusBar;
    final boolean statusClock;
    final boolean notificationClock;
    final boolean controlCenterLayout;
    final boolean controlCenterMaterials;
    final boolean controlCenterHeader;
    final boolean systemMenus;
    final boolean notificationSurfaces;

    private RuntimeConfigDiff(boolean statusBar, boolean statusClock, boolean notificationClock,
            boolean controlCenterLayout, boolean controlCenterMaterials,
            boolean controlCenterHeader, boolean systemMenus, boolean notificationSurfaces) {
        this.statusBar = statusBar;
        this.statusClock = statusClock;
        this.notificationClock = notificationClock;
        this.controlCenterLayout = controlCenterLayout;
        this.controlCenterMaterials = controlCenterMaterials;
        this.controlCenterHeader = controlCenterHeader;
        this.systemMenus = systemMenus;
        this.notificationSurfaces = notificationSurfaces;
    }

    static RuntimeConfigDiff between(FusionConfig previous, FusionConfig next) {
        if (previous == null) return new RuntimeConfigDiff(true, true, true, true, true, true,
                true, true);
        boolean clock = previous.customClock != next.customClock
                || !previous.clockPattern.equals(next.clockPattern)
                || previous.showWeather != next.showWeather
                || previous.statusClockSizeSp != next.statusClockSizeSp;
        boolean status = previous.iconScale != next.iconScale
                || previous.strokeScale != next.strokeScale || previous.doubleRow != next.doubleRow
                || previous.doubleRowSide != next.doubleRowSide || previous.spanRows != next.spanRows
                || previous.spanOffsetX != next.spanOffsetX || previous.spanOffsetY != next.spanOffsetY
                || previous.wifiIcon != next.wifiIcon || previous.statusBarHeight != next.statusBarHeight
                || previous.clockSide != next.clockSide || previous.clockRow != next.clockRow
                || previous.notificationSide != next.notificationSide
                || previous.notificationRow != next.notificationRow || previous.systemSide != next.systemSide
                || previous.systemRow != next.systemRow || previous.fusionSide != next.fusionSide
                || previous.fusionRow != next.fusionRow || previous.systemIconScale != next.systemIconScale
                || previous.notificationIconScale != next.notificationIconScale
                || !sameTelemetry(previous.telemetry, next.telemetry);
        ControlCenterConfig old = previous.controlCenter;
        ControlCenterConfig value = next.controlCenter;
        boolean layout = old.enabled != value.enabled || !old.order.equals(value.order)
                || !old.hidden.equals(value.hidden) || old.columns != value.columns
                || old.tileScale != value.tileScale
                || old.spacing != value.spacing
                || old.style != value.style || !old.layout.equals(value.layout)
                || !old.layoutPlan.equals(value.layoutPlan)
                || !old.componentOrder.equals(value.componentOrder)
                || !old.componentPlacement.equals(value.componentPlacement);
        boolean appearance = old.tileScale != value.tileScale || old.cornerRadius != value.cornerRadius
                || old.surface != value.surface || old.borderEnabled != value.borderEnabled
                || old.borderWidthDp != value.borderWidthDp || old.backgroundBlur != value.backgroundBlur
                || old.cardBlur != value.cardBlur || old.tileBlur != value.tileBlur;
        boolean header = old.hideDate != value.hideDate || old.hideCarrier != value.hideCarrier;
        SystemSurfaceConfig oldSurfaces = old.systemSurfaces;
        SystemSurfaceConfig surfaces = value.systemSurfaces;
        boolean menus = appearance || old.spacing != value.spacing
                || oldSurfaces.powerMenu != surfaces.powerMenu
                || oldSurfaces.volumeDialog != surfaces.volumeDialog
                || oldSurfaces.extendedPowerMenu != surfaces.extendedPowerMenu
                || oldSurfaces.compactVolume != surfaces.compactVolume;
        boolean notifications = appearance || oldSurfaces.notifications != surfaces.notifications
                || oldSurfaces.headsUp != surfaces.headsUp;
        return new RuntimeConfigDiff(status, clock, !sameNotificationClock(previous.notificationClock,
                next.notificationClock), layout, old.enabled != value.enabled || appearance,
                header, menus, notifications);
    }

    boolean controlCenter() { return controlCenterLayout || controlCenterMaterials; }

    private static boolean sameTelemetry(TelemetryConfig old, TelemetryConfig next) {
        Bundle a = new Bundle();
        Bundle b = new Bundle();
        old.writeTo(a);
        next.writeTo(b);
        return sameValues(a, b);
    }

    private static boolean sameNotificationClock(NotificationClockConfig old, NotificationClockConfig next) {
        Bundle a = new Bundle();
        Bundle b = new Bundle();
        old.writeTo(a);
        next.writeTo(b);
        return sameValues(a, b);
    }

    private static boolean sameValues(Bundle a, Bundle b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (String key : a.keySet()) if (!sameValue(a.get(key), b.get(key))) return false;
        return true;
    }

    private static boolean sameValue(Object a, Object b) {
        if (a instanceof int[] x && b instanceof int[] y) return Arrays.equals(x, y);
        if (a instanceof boolean[] x && b instanceof boolean[] y) return Arrays.equals(x, y);
        if (a instanceof String[] x && b instanceof String[] y) return Arrays.equals(x, y);
        return Objects.equals(a, b);
    }
}

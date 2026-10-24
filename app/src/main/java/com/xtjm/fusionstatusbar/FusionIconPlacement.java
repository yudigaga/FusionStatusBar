package com.xtjm.fusionstatusbar;

import android.view.View;
import android.view.ViewParent;

/** Shared icon sizing for the preview and SystemUI. */
final class FusionIconPlacement {
    static final int CONTROL_CENTER_ICON_SCALE = 80;
    private static final String CONTROL_CENTER_STATUS_BAR_ICON_CLASS =
            "com.android.systemui.controlcenter.phone.widget.ControlCenterStatusBarIcon";
    private static final String CONTROL_CENTER_FAKE_STATUS_ICONS_CLASS =
            "com.android.systemui.controlcenter.phone.widget.ControlCenterFakeStatusIcons";

    private FusionIconPlacement() {
    }

    static int iconScalePercent(int statusBarScale, boolean controlCenterHost) {
        return controlCenterHost ? CONTROL_CENTER_ICON_SCALE : statusBarScale;
    }

    static boolean isControlCenterHosted(View view) {
        for (ViewParent parent = view == null ? null : view.getParent();
                parent instanceof View ancestor; parent = ancestor.getParent()) {
            String className = ancestor.getClass().getName();
            if (CONTROL_CENTER_STATUS_BAR_ICON_CLASS.equals(className)
                    || CONTROL_CENTER_FAKE_STATUS_ICONS_CLASS.equals(className)) {
                return true;
            }
        }
        return false;
    }

    static int iconHeight(int availableHeight, int scalePercent) {
        return Math.min(availableHeight, Math.max(1,
                Math.round(availableHeight * (2f / 3f) * scalePercent / 100f)));
    }
}

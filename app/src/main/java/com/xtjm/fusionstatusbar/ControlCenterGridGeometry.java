package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Resources;

/** Shared pixel geometry used by the draft editor and the injected runtime grid. */
final class ControlCenterGridGeometry {
    private static final String PLUGIN_PACKAGE = "miui.systemui.plugin";
    private static final String ROW_DIMEN = "control_center_universal_1_row_with_margin_size";
    private static final float FALLBACK_ROW_DP = 85.5f;

    private ControlCenterGridGeometry() {
    }

    static int gapPx(Context context, int spacingDp) {
        return Math.round(context.getResources().getDisplayMetrics().density
                * Math.max(0, spacingDp));
    }

    static int radiusPx(Context context, int radiusDp) {
        return Math.round(context.getResources().getDisplayMetrics().density
                * Math.max(0, radiusDp));
    }

    static int rowHeightPx(Context context) {
        Resources resources = pluginResources(context);
        int id = resources.getIdentifier(ROW_DIMEN, "dimen", PLUGIN_PACKAGE);
        if (id == 0 && resources != context.getResources()) {
            resources = context.getResources();
            id = resources.getIdentifier(ROW_DIMEN, "dimen", PLUGIN_PACKAGE);
        }
        if (id == 0) {
            id = resources.getIdentifier(ROW_DIMEN, "dimen", context.getPackageName());
        }
        if (id != 0) {
            try {
                return resources.getDimensionPixelSize(id);
            } catch (Resources.NotFoundException ignored) {
                // Use the same fallback as the runtime renderer below.
            }
        }
        return Math.round(context.getResources().getDisplayMetrics().density * FALLBACK_ROW_DP);
    }

    static int columnUnitPx(int widthPx, int columns, int gapPx) {
        int safeColumns = Math.max(1, columns);
        return Math.max(1, (widthPx - gapPx * (safeColumns - 1)) / safeColumns);
    }

    static int spanPx(int unitPx, int gapPx, int span) {
        int safeSpan = Math.max(1, span);
        return unitPx * safeSpan + gapPx * (safeSpan - 1);
    }

    /** Grid spans use the same row pitch as their y coordinates in both processes. */
    static int spanHeightPx(Context context, int gapPx, int span) {
        return spanPx(rowHeightPx(context), gapPx, span);
    }

    static int contentHeightPx(int rowCount, int rowHeightPx, int gapPx) {
        int safeRows = Math.max(0, rowCount);
        return safeRows * rowHeightPx + Math.max(0, safeRows - 1) * gapPx;
    }

    private static Resources pluginResources(Context context) {
        // The injected grid already receives the SystemUI/plugin context that owns
        // the active resource overlays. Creating another package context for every
        // measurement makes ResourcesManager reload stale .frro idmaps and can
        // return a resource set that does not match the currently mounted panel.
        return context.getResources();
    }
}

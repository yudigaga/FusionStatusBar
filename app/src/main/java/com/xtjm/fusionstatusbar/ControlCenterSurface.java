package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;

/** Container tint shared by the editor and native material hooks. */
enum ControlCenterSurface {
    DEFAULT(R.string.ui_control_surface_default, "原有背景", 0), CLEAR(R.string.ui_control_surface_clear, "透明", 0x00000000),
    FROSTED(R.string.ui_control_surface_frosted, "雾面", 0x408c8c92), DARK(R.string.ui_control_surface_dark, "深色", 0xc8232529),
    GLASS(R.string.ui_control_surface_glass, "晶透", 0x26ffffff), LIGHT(R.string.ui_control_surface_light, "浅色", 0xe6f0f2f5),
    GRAPHITE(R.string.ui_control_surface_graphite, "石墨", 0xe6191b1f), ICE(R.string.ui_control_surface_ice, "冰蓝", 0x704e8eae),
    MINT(R.string.ui_control_surface_mint, "薄荷", 0x70609680), SOFT_GLASS(R.string.ui_control_surface_soft_glass, "柔光玻璃", 0xc03c4149),
    // A neutral, very light lift lets the actual backdrop supply the glass color.
    HIGHLIGHT_GLASS(R.string.ui_control_surface_highlight_glass, "高光玻璃（兼容·随系统）", 0x18ffffff);

    @Deprecated final String label;
    @androidx.annotation.StringRes final int labelResource;
    final int color;

    ControlCenterSurface(int labelResource, String label, int color) {
        this.labelResource = labelResource;
        this.label = label;
        this.color = color;
    }

    static ControlCenterSurface fromOrdinal(int value) {
        return value >= 0 && value < values().length ? values()[value] : DEFAULT;
    }

    static int defaultGroupColor() { return FROSTED.color; }

    boolean allowsContainerBlur() { return this != CLEAR; }

    boolean hasSoftGlassReflection() { return this == SOFT_GLASS || this == HIGHLIGHT_GLASS; }

    /** Resolve at the consuming view, keeping the saved choice independent of uiMode. */
    ControlCenterSurface resolve(Context context) {
        if (this == HIGHLIGHT_GLASS && context != null
                && (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES) return SOFT_GLASS;
        return this;
    }

    int stateColor(boolean active) {
        if (this == CLEAR) return color;
        if (active) return ControlCenterCardView.ACTIVE_SURFACE;
        return this == DEFAULT ? ControlCenterCardView.INACTIVE_SURFACE : color;
    }

    int neutralForeground() { return this == LIGHT ? 0xff202124 : 0xffffffff; }

    String label(android.content.Context context) { return ModuleStrings.get(context, labelResource); }

    @Override public String toString() { return label; }
}

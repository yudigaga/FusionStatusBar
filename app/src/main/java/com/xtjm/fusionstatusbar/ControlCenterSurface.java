package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;

/** Container tint shared by the editor and native material hooks. */
enum ControlCenterSurface {
    DEFAULT("原有背景", 0), CLEAR("透明", 0x00000000),
    FROSTED("雾面", 0x408c8c92), DARK("深色", 0xc8232529),
    GLASS("晶透", 0x26ffffff), LIGHT("浅色", 0xe6f0f2f5),
    GRAPHITE("石墨", 0xe6191b1f), ICE("冰蓝", 0x704e8eae),
    MINT("薄荷", 0x70609680), SOFT_GLASS("柔光玻璃", 0xc03c4149),
    HIGHLIGHT_GLASS("高光玻璃（随系统）", 0xb8e8edf2);

    final String label;
    final int color;

    ControlCenterSurface(String label, int color) {
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

    int neutralForeground() { return this == LIGHT || this == HIGHLIGHT_GLASS ? 0xff202124 : 0xffffffff; }

    @Override public String toString() { return label; }
}

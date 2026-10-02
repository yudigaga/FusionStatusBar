package com.xtjm.fusionstatusbar;

/** Shared icon sizing for the preview and SystemUI. */
final class FusionIconPlacement {
    private FusionIconPlacement() {
    }

    static int iconHeight(int availableHeight, int scalePercent) {
        return Math.min(availableHeight, Math.max(1,
                Math.round(availableHeight * (2f / 3f) * scalePercent / 100f)));
    }
}

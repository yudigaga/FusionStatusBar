package com.xtjm.fusionstatusbar;

/** Pure design-space geometry for the fused icon, independent of Android drawing APIs. */
final class FusionIconGeometry {
    static final float DESIGN_W = 271f;
    static final float DESIGN_H = 282f;
    static final float RING_CENTER_X = DESIGN_W / 2f;
    static final float RING_CENTER_Y = 137f;
    static final float RING_RADIUS = 98f;
    static final float RING_STROKE_WIDTH = 18f;
    static final float SIGNAL_ROW_EXTRA_GAP = 6f;
    static final float SIGNAL_DOT_SPACING = 34f;
    static final float SIGNAL_DOT_CURVE = 9f;
    static final float WIFI_ANCHOR_Y = RING_CENTER_Y + 9.5f;

    private FusionIconGeometry() {
    }

    static float stroke(float designWidth, int strokeScale) {
        return designWidth * Math.max(0, strokeScale) / 100f;
    }

    static float innerSignalRowY(int strokeScale) {
        return RING_CENTER_Y + RING_RADIUS - stroke(RING_STROKE_WIDTH, strokeScale);
    }

    static float outerSignalRowY(int strokeScale) {
        return innerSignalRowY(strokeScale)
                + stroke(RING_STROKE_WIDTH + SIGNAL_ROW_EXTRA_GAP, strokeScale);
    }

    static float signalX(int index) {
        return RING_CENTER_X + (index - 1.5f) * SIGNAL_DOT_SPACING;
    }

    static float signalY(float rowCenter, int index) {
        return rowCenter + (index == 0 || index == 3
                ? -SIGNAL_DOT_CURVE : SIGNAL_DOT_CURVE);
    }
}

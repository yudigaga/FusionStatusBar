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
    static final float BATTERY_CONTENT_Y = 62f;

    static final class ContentLayout {
        private final float[] centers;
        private final float[] halfHeights;

        ContentLayout(float[] centers, float[] halfHeights) {
            this.centers = centers;
            this.halfHeights = halfHeights;
        }

        float centerY(int part) { return centers[part]; }
        float top(int part) { return centerY(part) - halfHeights[part]; }
        float bottom(int part) { return centerY(part) + halfHeights[part]; }
    }

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

    static float contentSourceY(int part, int strokeScale) {
        if (part == FusionContentConfig.BATTERY) return BATTERY_CONTENT_Y;
        if (part == FusionContentConfig.NETWORK) return RING_CENTER_Y;
        return (innerSignalRowY(strokeScale) + outerSignalRowY(strokeScale)) / 2f;
    }

    static ContentLayout contentLayout(FusionContentConfig config, int strokeScale) {
        float[] halfHeights = {
                31f * config.batteryScale / 100f,
                43f * config.networkScale / 100f,
                ((outerSignalRowY(strokeScale) - innerSignalRowY(strokeScale)) / 2f
                        + SIGNAL_DOT_CURVE + stroke(8.5f, strokeScale))
                        * config.signalScale / 100f
        };
        float[] slots = {BATTERY_CONTENT_Y, RING_CENTER_Y,
                contentSourceY(FusionContentConfig.SIGNAL, strokeScale)};
        float[] centers = new float[3];
        // Keep each part's size independent; only constrain its own anchor at the frame edges.
        for (int part = 0; part < 3; part++) {
            centers[part] = Math.max(halfHeights[part] + 1f,
                    Math.min(DESIGN_H - halfHeights[part] - 1f, slots[config.positionFor(part)]));
        }
        return new ContentLayout(centers, halfHeights);
    }

    static boolean flipContent(int part, FusionContentConfig config) {
        return part == FusionContentConfig.SIGNAL && config.signalPosition == FusionContentConfig.TOP;
    }
}

package com.xtjm.fusionstatusbar;

/** Pure design-space geometry for the fused icon, independent of Android drawing APIs. */
final class FusionIconGeometry {
    static final float DESIGN_W = 271f;
    static final float DESIGN_H = 282f;
    static final float RING_CENTER_X = DESIGN_W / 2f;
    static final float RING_CENTER_Y = 142f;
    static final float RING_RADIUS = 102f;
    static final float RING_STROKE_WIDTH = 28f;
    static final float SIGNAL_ROW_EXTRA_GAP = 6f;
    static final float SIGNAL_DOT_SPACING = 32.5f;
    static final float SIGNAL_DOT_CURVE = 9f;
    static final float WIFI_ANCHOR_Y = 76f;
    static final float WIFI_OUTER_RADIUS = 46f;
    static final float WIFI_INNER_RADIUS = 25f;
    static final float WIFI_STROKE_WIDTH = 12f;
    static final float WIFI_DOT_RADIUS = 8.5f;
    private static final float WIFI_ARC_HALF_WIDTH =
            WIFI_OUTER_RADIUS * (float) Math.sin(Math.toRadians(50));
    static final float WIFI_CONTENT_Y = 54f;
    static final float HEADER_CONTENT_Y = 43f;
    static final float MIDDLE_CONTENT_Y = 151f;
    static final float BATTERY_CONTENT_Y = MIDDLE_CONTENT_Y;

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

    static float networkContentWidth(int strokeScale) {
        return WIFI_ARC_HALF_WIDTH * 2f + stroke(WIFI_STROKE_WIDTH, strokeScale);
    }

    static float networkContentHeight(int strokeScale) {
        return WIFI_OUTER_RADIUS
                + stroke(WIFI_STROKE_WIDTH / 2f + WIFI_DOT_RADIUS, strokeScale);
    }

    private static float networkContentHalfHeight(int strokeScale) {
        float top = WIFI_ANCHOR_Y - WIFI_OUTER_RADIUS
                - stroke(WIFI_STROKE_WIDTH / 2f, strokeScale);
        float bottom = WIFI_ANCHOR_Y + stroke(WIFI_DOT_RADIUS, strokeScale);
        return Math.max(35f, Math.max(WIFI_CONTENT_Y - top, bottom - WIFI_CONTENT_Y) + 1f);
    }

    static float contentSourceY(int part, int strokeScale) {
        return contentSourceY(part, strokeScale, true);
    }

    static float contentSourceY(int part, int strokeScale, boolean showWifi) {
        if (part == FusionContentConfig.BATTERY) return BATTERY_CONTENT_Y;
        if (part == FusionContentConfig.NETWORK) {
            return showWifi ? WIFI_CONTENT_Y : MIDDLE_CONTENT_Y;
        }
        return (innerSignalRowY(strokeScale) + outerSignalRowY(strokeScale)) / 2f;
    }

    static ContentLayout contentLayout(FusionContentConfig config, int strokeScale) {
        return contentLayout(config, strokeScale, true);
    }

    static ContentLayout contentLayout(FusionContentConfig config, int strokeScale,
            boolean showWifi) {
        float[] halfHeights = {
                (config.batteryPosition == FusionContentConfig.MIDDLE ? 54f : 37f)
                        * config.batteryScale / 100f,
                networkContentHalfHeight(strokeScale) * config.networkScale / 100f,
                ((outerSignalRowY(strokeScale) - innerSignalRowY(strokeScale)) / 2f
                        + SIGNAL_DOT_CURVE + stroke(10.5f, strokeScale))
                        * config.signalScale / 100f
        };
        float[] slots = {HEADER_CONTENT_Y, MIDDLE_CONTENT_Y,
                contentSourceY(FusionContentConfig.SIGNAL, strokeScale)};
        float[] centers = new float[3];
        // Keep each part's size independent; only constrain its own anchor at the frame edges.
        for (int part = 0; part < 3; part++) {
            float anchor = part == FusionContentConfig.NETWORK && showWifi
                    && config.networkPosition == FusionContentConfig.TOP
                    ? WIFI_CONTENT_Y : slots[config.positionFor(part)];
            centers[part] = Math.max(halfHeights[part] + 1f,
                    Math.min(DESIGN_H - halfHeights[part] - 1f, anchor));
        }
        return new ContentLayout(centers, halfHeights);
    }

    static boolean flipContent(int part, FusionContentConfig config) {
        return part == FusionContentConfig.SIGNAL && config.signalPosition == FusionContentConfig.TOP;
    }
}

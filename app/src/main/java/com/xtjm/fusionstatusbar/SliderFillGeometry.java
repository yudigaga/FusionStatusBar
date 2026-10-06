package com.xtjm.fusionstatusbar;

import android.graphics.Path;
import android.graphics.RectF;

/** Clips the level against the inside of the complete track, including its end curves. */
final class SliderFillGeometry {
    private SliderFillGeometry() { }

    static void intersect(Path result, RectF trackBounds, float outerRadius, float inset,
            RectF levelBounds) {
        intersect(result, trackBounds, outerRadius, inset, levelBounds, false);
    }

    static void intersect(Path result, RectF trackBounds, float outerRadius, float inset,
            RectF levelBounds, boolean oval) {
        result.reset();
        if (trackBounds.isEmpty() || levelBounds.isEmpty()) return;
        float margin = Math.max(0f, inset);
        RectF inside = new RectF(trackBounds);
        inside.inset(margin, margin);
        if (inside.isEmpty()) return;
        if (oval) {
            result.addOval(inside, Path.Direction.CW);
        } else {
            float radius = Math.max(0f, Math.min(outerRadius,
                    Math.min(trackBounds.width(), trackBounds.height()) / 2f) - margin);
            result.addRoundRect(inside, radius, radius, Path.Direction.CW);
        }
        Path level = new Path();
        level.addRect(levelBounds, Path.Direction.CW);
        if (!result.op(level, Path.Op.INTERSECT)) result.reset();
    }
}

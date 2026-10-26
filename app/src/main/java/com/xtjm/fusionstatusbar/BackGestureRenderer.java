package com.xtjm.fusionstatusbar;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** Compact progress-driven affordances shared by the launcher and the settings preview. */
final class BackGestureRenderer {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF bounds = new RectF();
    private BackGesturePalette palette;
    private int backgroundSeed;
    private int arrowSeed;
    private boolean night;
    private final Matrix rainbowMatrix = new Matrix();
    private LinearGradient rainbowFill;
    private LinearGradient rainbowOutline;
    private int rainbowSeed;
    private boolean rainbowNight;

    void drawThemed(Canvas canvas, int style, int backgroundColor, int arrowColor, boolean right,
            float edgeX, float centerY, float nativeDepth, float nativeHeight,
            float nativeScale, float arrowAlpha, float density, boolean isNight) {
        if (palette == null || backgroundSeed != backgroundColor || arrowSeed != arrowColor
                || night != isNight) {
            backgroundSeed = backgroundColor;
            arrowSeed = arrowColor;
            night = isNight;
            palette = BackGesturePalette.resolve(backgroundColor, arrowColor, isNight);
        }
        if (style == DesktopConfig.BACK_STYLE_RAINBOW) configureRainbow(backgroundColor, isNight);
        drawInternal(canvas, style, palette.background, palette.arrow, palette.outline,
                palette.shadow, right, edgeX, centerY, nativeDepth, nativeHeight,
                nativeScale, arrowAlpha, density);
    }

    void draw(Canvas canvas, int style, int backgroundColor, int arrowColor, boolean right,
            float edgeX, float centerY, float nativeDepth, float nativeHeight,
            float nativeScale, float arrowAlpha, float density) {
        if (style == DesktopConfig.BACK_STYLE_RAINBOW) configureRainbow(backgroundColor, false);
        drawInternal(canvas, style, backgroundColor, arrowColor,
                withOpacity(arrowColor, Color.alpha(backgroundColor) / 255f * .22f),
                Color.TRANSPARENT, right, edgeX, centerY, nativeDepth, nativeHeight,
                nativeScale, arrowAlpha, density);
    }

    private void drawInternal(Canvas canvas, int style, int backgroundColor, int arrowColor,
            int outlineColor, int shadowColor, boolean right, float edgeX, float centerY,
            float nativeDepth, float nativeHeight, float nativeScale, float arrowAlpha,
            float density) {
        if (canvas == null || !Float.isFinite(nativeScale) || !Float.isFinite(nativeDepth)
                || !Float.isFinite(nativeHeight) || !Float.isFinite(density)
                || nativeDepth <= 0 || nativeScale <= 0 || nativeHeight <= 0 || density <= 0) return;
        float progress = Math.min(nativeScale, 1f);
        float response = response(style, progress);
        float depth = Math.min(54f * density,
                nativeDepth * response / Math.max(progress, .001f));
        float opacity = smoothstep(nativeScale / .18f);
        float bodyOpacity = Color.alpha(backgroundColor) / 255f;
        float inset = style == DesktopConfig.BACK_STYLE_CLASSIC ? 0f
                : (3f + 5f * response) * density * smoothstep(progress / .4f);
        float halfHeight;
        path.rewind();
        if (style == DesktopConfig.BACK_STYLE_CLASSIC) {
            halfHeight = Math.min(nativeHeight / 2f, (24f + 8f * response) * density);
            path.moveTo(0, -halfHeight);
            path.cubicTo(depth * .12f, -halfHeight * .42f,
                    depth, -halfHeight * .52f, depth, 0);
            path.cubicTo(depth, halfHeight * .52f,
                    depth * .12f, halfHeight * .42f, 0, halfHeight);
            path.close();
            bounds.set(0, -halfHeight, depth, halfHeight);
        } else {
            halfHeight = Math.min(nativeHeight / 2f,
                    (style == DesktopConfig.BACK_STYLE_RIPPLE ? 21f
                            : style == DesktopConfig.BACK_STYLE_NEON ? 18f + 3f * response
                            : 16f + 6f * response) * density);
            bounds.set(inset, -halfHeight, inset + depth, halfHeight);
            float nearRadius = style == DesktopConfig.BACK_STYLE_CAPSULE ? 10f * density
                    : halfHeight;
            roundedPill(bounds, nearRadius, halfHeight);
        }
        int saved = canvas.save();
        try {
            canvas.translate(edgeX, centerY);
            if (right) canvas.scale(-1f, 1f);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (Color.alpha(shadowColor) > 0 && bodyOpacity > 0) {
                paint.setColor(withOpacity(shadowColor, opacity * bodyOpacity));
                int shadowSave = canvas.save();
                canvas.translate(0, 1.25f * density);
                canvas.drawPath(path, paint);
                canvas.restoreToCount(shadowSave);
            }
            if (style == DesktopConfig.BACK_STYLE_RAINBOW) {
                rainbowMatrix.setScale(bounds.width(), bounds.height());
                rainbowMatrix.postTranslate(bounds.left, bounds.top);
                rainbowMatrix.postRotate(-8f + progress * 16f, bounds.centerX(), 0);
                rainbowMatrix.postTranslate((progress - .5f) * depth * .2f, 0);
                rainbowFill.setLocalMatrix(rainbowMatrix);
                rainbowOutline.setLocalMatrix(rainbowMatrix);
                paint.setShader(rainbowFill);
                paint.setColor(withOpacity(Color.WHITE, opacity));
            } else {
                paint.setColor(withOpacity(backgroundColor, opacity));
            }
            canvas.drawPath(path, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth((style == DesktopConfig.BACK_STYLE_RAINBOW ? 1.25f : .85f) * density);
            if (style == DesktopConfig.BACK_STYLE_RAINBOW) {
                paint.setShader(rainbowOutline);
                paint.setColor(withOpacity(Color.WHITE, opacity));
            } else {
                paint.setColor(withOpacity(outlineColor, opacity));
            }
            canvas.drawPath(path, paint);
            paint.setShader(null);
            if (style == DesktopConfig.BACK_STYLE_NEON) {
                paint.setStrokeWidth(3.2f * density);
                paint.setColor(withOpacity(arrowColor, opacity * bodyOpacity * .09f));
                canvas.drawPath(path, paint);
                paint.setStrokeWidth(1.1f * density);
                paint.setColor(withOpacity(arrowColor, opacity * bodyOpacity * .62f));
                canvas.drawPath(path, paint);
            }
            float center = bounds.left + bounds.width() * .53f;
            if (style == DesktopConfig.BACK_STYLE_RIPPLE && depth > 12f * density) {
                float radius = Math.min(25f * density, halfHeight + 3f * density);
                bounds.set(center - radius, -radius, center + radius, radius);
                paint.setStrokeWidth(1.1f * density);
                paint.setColor(withOpacity(outlineColor, opacity * .6f));
                canvas.drawArc(bounds, -135f, 270f, false, paint);
                paint.setColor(withOpacity(arrowColor, opacity * bodyOpacity * .52f));
                canvas.drawArc(bounds, -135f, 35f + 235f * smoothstep(progress), false, paint);
            }
            if (arrowAlpha > 0 && depth > 8f * density) {
                float halfWidth = (2.6f + .65f * response) * density;
                float arrowHeight = (5.4f + 1.1f * response) * density;
                paint.setColor(withOpacity(arrowColor, opacity * Math.min(1f, arrowAlpha)));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.1f * density);
                path.rewind();
                path.moveTo(center - halfWidth, -arrowHeight);
                path.lineTo(center + halfWidth, 0);
                path.lineTo(center - halfWidth, arrowHeight);
                canvas.drawPath(path, paint);
            }
        } finally {
            paint.setShader(null);
            canvas.restoreToCount(saved);
        }
    }

    private void roundedPill(RectF rect, float nearRadius, float farRadius) {
        float limit = Math.min(rect.width() / 2f, rect.height() / 2f);
        float near = Math.min(nearRadius, limit);
        float far = Math.min(farRadius, limit);
        path.moveTo(rect.left + near, rect.top);
        path.lineTo(rect.right - far, rect.top);
        path.quadTo(rect.right, rect.top, rect.right, rect.top + far);
        path.lineTo(rect.right, rect.bottom - far);
        path.quadTo(rect.right, rect.bottom, rect.right - far, rect.bottom);
        path.lineTo(rect.left + near, rect.bottom);
        path.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - near);
        path.lineTo(rect.left, rect.top + near);
        path.quadTo(rect.left, rect.top, rect.left + near, rect.top);
        path.close();
    }

    static float response(int style, float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        return switch (style) {
            case DesktopConfig.BACK_STYLE_CAPSULE -> 1f - (float) Math.pow(1f - p, 1.8);
            case DesktopConfig.BACK_STYLE_RIPPLE -> p * (2f - p);
            case DesktopConfig.BACK_STYLE_NEON -> smoothstep(p);
            case DesktopConfig.BACK_STYLE_RAINBOW -> p + .08f * (float) Math.sin(Math.PI * p);
            default -> p + .06f * (float) Math.sin(Math.PI * p);
        };
    }

    private void configureRainbow(int seed, boolean isNight) {
        if (rainbowFill != null && rainbowSeed == seed && rainbowNight == isNight) return;
        rainbowSeed = seed;
        rainbowNight = isNight;
        rainbowFill = new LinearGradient(0, 0, 1, 1,
                BackGesturePalette.rainbow(seed, isNight, false), null, Shader.TileMode.MIRROR);
        rainbowOutline = new LinearGradient(0, 0, 1, 1,
                BackGesturePalette.rainbow(seed, isNight, true), null, Shader.TileMode.MIRROR);
    }

    private static float smoothstep(float value) {
        float p = Math.max(0f, Math.min(1f, value));
        return p * p * (3f - 2f * p);
    }

    private static int withOpacity(int color, float opacity) {
        return (color & 0x00FFFFFF) | (Math.round(Color.alpha(color)
                * Math.max(0f, Math.min(1f, opacity))) << 24);
    }
}

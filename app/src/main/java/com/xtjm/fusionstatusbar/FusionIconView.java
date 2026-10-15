package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

/** Draws the fused battery, network and signal icon in one design space. */
public class FusionIconView extends View {
    static final float DESIGN_W = FusionIconGeometry.DESIGN_W;
    static final float DESIGN_H = FusionIconGeometry.DESIGN_H;
    private static final float RING_CENTER_Y = FusionIconGeometry.RING_CENTER_Y;
    private static final float RING_RADIUS = FusionIconGeometry.RING_RADIUS;
    private static final float RING_STROKE_WIDTH = FusionIconGeometry.RING_STROKE_WIDTH;
    private static final float BATTERY_SIDE_SWEEP = 75f;
    private static final float WIFI_ANCHOR_Y = FusionIconGeometry.WIFI_ANCHOR_Y;
    private static final float TEXT_MAX_WIDTH = 132f;
    private static final float BATTERY_TEXT_SIZE = 108f;
    private static final float HEADER_TEXT_SIZE = 74f;
    private static final float CENTER_CONTENT_Y = 151f;
    private static final int CHARGING_COLOR = Color.rgb(68, 141, 70);
    private static final int FAST_CHARGING_COLOR = Color.rgb(246, 195, 75);
    private static final int POWER_SAVE_COLOR = Color.rgb(191, 155, 39);
    private static final int DANGER_COLOR = Color.rgb(240, 68, 61);
    private static final Typeface BASE_TYPEFACE = Typeface.create("sans-serif", Typeface.NORMAL);

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint networkText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path lightning = new Path();
    private final Path networkTextPath = new Path();
    private final RectF networkTextBounds = new RectF();
    private final StatusMonitor.Listener listener = this::onStatus;

    private boolean preview;
    private int batteryPercent = -1;
    private boolean charging;
    private boolean fastCharging;
    private boolean powerSave;
    private boolean showWifi;
    private int wifiLevel;
    private String networkLabel = "";
    private boolean networkTextDirty = true;
    private float networkReferenceWidth;
    private int signalLevel;
    private int signalLevel2;
    private int tint = Color.WHITE;
    private FusionConfig displayConfig = FusionConfig.defaults();
    private FusionIconGeometry.ContentLayout contentLayout = FusionIconGeometry.contentLayout(
            displayConfig.fusionContent, displayConfig.strokeScale, showWifi);
    private ContentObserver configObserver;

    public FusionIconView(Context context) {
        this(context, null);
    }

    public FusionIconView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FusionIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        body.setStyle(Paint.Style.STROKE);
        body.setStrokeJoin(Paint.Join.ROUND);
        body.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(Typeface.create(BASE_TYPEFACE, 600, false));
        text.setTextAlign(Paint.Align.CENTER);
        text.setStyle(Paint.Style.FILL);
        networkText.setTypeface(text.getTypeface());
        networkText.setTextSize(100f);
        networkText.setStyle(Paint.Style.FILL);
        lightning.moveTo(147f, 105f);
        lightning.lineTo(99f, 159f);
        lightning.quadTo(96f, 163f, 102f, 163f);
        lightning.lineTo(130f, 163f);
        lightning.lineTo(122f, 197f);
        lightning.quadTo(120f, 203f, 125f, 198f);
        lightning.lineTo(173f, 144f);
        lightning.quadTo(176f, 140f, 170f, 140f);
        lightning.lineTo(142f, 140f);
        lightning.lineTo(150f, 107f);
        lightning.quadTo(151f, 101f, 147f, 105f);
        lightning.close();
    }

    public void showSample(int battery, boolean wifi, String label, int level, int color) {
        showSample(battery, wifi, label, wifi ? 4 : 0, level, level, color);
    }

    public void showSample(
            int battery, boolean wifi, String label, int level1, int level2, int color) {
        showSample(battery, wifi, label, wifi ? 4 : 0, level1, level2, color);
    }

    void showSample(int battery, boolean wifi, String label, int wifiStrength,
            int level1, int level2, int color) {
        showSample(new StatusMonitor.Snapshot(battery, wifi, label, level1, level2,
                clamp(wifiStrength), false), color);
    }

    void showSample(StatusMonitor.Snapshot snapshot, int color) {
        preview = true;
        StatusMonitor.removeListener(listener);
        tint = color;
        render(snapshot);
        requestLayout();
        invalidate();
    }

    void setIconTint(int color) {
        if (preview) {
            return;
        }
        int next = Color.alpha(color) == 0 ? Color.WHITE : color;
        if (next == tint) {
            return;
        }
        tint = next;
        invalidate();
    }

    void setDisplayConfig(FusionConfig config) {
        displayConfig = config == null ? FusionConfig.defaults() : config;
        contentLayout = FusionIconGeometry.contentLayout(
                displayConfig.fusionContent, displayConfig.strokeScale, showWifi);
        int weight = Math.max(400, Math.min(900,
                600 + (displayConfig.strokeScale - 100) * 5));
        Typeface typeface = Typeface.create(BASE_TYPEFACE, weight, false);
        text.setTypeface(typeface);
        networkText.setTypeface(typeface);
        networkTextDirty = true;
        requestLayout();
        invalidate();
    }

    void setPreviewConfig(FusionConfig config) {
        setDisplayConfig(config);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isPreview()) {
            preview = true;
            if (batteryPercent < 0) {
                showSample(91, true, "", 4, 3, Color.WHITE);
            }
            return;
        }
        reloadConfig();
        configObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                reloadConfig();
            }
        };
        try {
            getContext().getContentResolver().registerContentObserver(
                    FusionConfig.contentUri(), true, configObserver);
        } catch (Throwable ignored) {
            // Settings remain usable after the next SystemUI layout pass.
        }
        StatusMonitor.start(getContext().getApplicationContext());
        StatusMonitor.addListener(listener);
        render(StatusMonitor.snapshot());
    }

    @Override
    protected void onDetachedFromWindow() {
        StatusMonitor.removeListener(listener);
        if (configObserver != null) {
            try {
                getContext().getContentResolver().unregisterContentObserver(configObserver);
            } catch (Throwable ignored) {
                // The observer is process-local and can be discarded safely.
            }
            configObserver = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isPreview()) {
            int height = MeasureSpec.getSize(heightMeasureSpec);
            if (height <= 0) {
                height = dp(180);
            }
            setMeasuredDimension(Math.round(height * DESIGN_W / DESIGN_H), height);
            return;
        }
        boolean spanRows = displayConfig.doubleRow && displayConfig.spanRows;
        int availableHeight = spanRows ? dp(displayConfig.statusBarHeight)
                : displayConfig.doubleRow ? Math.max(1, dp(displayConfig.statusBarHeight) / 2)
                : Math.min(dp(40), dp(displayConfig.statusBarHeight));
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int size = MeasureSpec.getSize(heightMeasureSpec);
        if (!spanRows && mode != MeasureSpec.UNSPECIFIED && size > 0) {
            availableHeight = Math.min(availableHeight, size);
        }
        int height = FusionIconPlacement.iconHeight(availableHeight, displayConfig.iconScale);
        int width = Math.round(height * DESIGN_W / DESIGN_H);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            int exact = MeasureSpec.getSize(widthMeasureSpec);
            if (exact > 0) {
                width = exact;
            }
        }
        setMeasuredDimension(Math.max(1, width), Math.max(1, height));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }
        float scale = Math.min(width / DESIGN_W, height / DESIGN_H);
        canvas.save();
        canvas.translate((width - DESIGN_W * scale) / 2f, (height - DESIGN_H * scale) / 2f);
        canvas.scale(scale, scale);
        drawBattery(canvas);
        saveContentTransform(canvas, FusionContentConfig.BATTERY);
        drawBatteryContent(canvas);
        canvas.restore();
        saveContentTransform(canvas, FusionContentConfig.NETWORK);
        drawNetworkContent(canvas);
        canvas.restore();
        saveContentTransform(canvas, FusionContentConfig.SIGNAL);
        drawSignals(canvas);
        canvas.restore();
        canvas.restore();
    }

    private void saveContentTransform(Canvas canvas, int part) {
        canvas.save();
        float size = displayConfig.fusionContent.scaleFor(part) / 100f;
        boolean flip = FusionIconGeometry.flipContent(part, displayConfig.fusionContent);
        canvas.translate(FusionIconGeometry.RING_CENTER_X, contentLayout.centerY(part));
        canvas.scale(size, flip ? -size : size);
        canvas.translate(-FusionIconGeometry.RING_CENTER_X,
                -FusionIconGeometry.contentSourceY(part, displayConfig.strokeScale, showWifi));
    }

    private void drawBattery(Canvas canvas) {
        float cx = FusionIconGeometry.RING_CENTER_X;
        float cy = RING_CENTER_Y;
        body.setColor(tint);
        body.setStyle(Paint.Style.STROKE);
        body.setStrokeWidth(stroke(RING_STROKE_WIDTH));
        body.setStrokeCap(Paint.Cap.ROUND);
        body.setAlpha(Math.round(Color.alpha(tint) * 0.24f));
        canvas.drawArc(cx - RING_RADIUS, cy - RING_RADIUS,
                cx + RING_RADIUS, cy + RING_RADIUS,
                225f, -BATTERY_SIDE_SWEEP, false, body);
        canvas.drawArc(cx - RING_RADIUS, cy - RING_RADIUS,
                cx + RING_RADIUS, cy + RING_RADIUS,
                315f, BATTERY_SIDE_SWEEP, false, body);
        body.setColor(batteryTint());
        float progress = batteryPercent < 0 ? BATTERY_SIDE_SWEEP * 2f
                : BATTERY_SIDE_SWEEP * 2f * Math.max(0, Math.min(100, batteryPercent)) / 100f;
        float leftProgress = Math.min(BATTERY_SIDE_SWEEP, progress);
        float rightProgress = Math.max(0f, progress - BATTERY_SIDE_SWEEP);
        if (leftProgress > 0f) {
            canvas.drawArc(cx - RING_RADIUS, cy - RING_RADIUS,
                    cx + RING_RADIUS, cy + RING_RADIUS,
                    225f - BATTERY_SIDE_SWEEP, leftProgress, false, body);
        }
        if (rightProgress > 0f) {
            canvas.drawArc(cx - RING_RADIUS, cy - RING_RADIUS,
                    cx + RING_RADIUS, cy + RING_RADIUS,
                    315f, rightProgress, false, body);
        }
    }

    private void drawBatteryContent(Canvas canvas) {
        if (charging) {
            drawLightning(canvas);
            return;
        }
        boolean middle = displayConfig.fusionContent.batteryPosition == FusionContentConfig.MIDDLE;
        drawPercent(canvas, FusionIconGeometry.BATTERY_CONTENT_Y,
                middle ? BATTERY_TEXT_SIZE : HEADER_TEXT_SIZE, middle ? TEXT_MAX_WIDTH : 110f);
    }

    private void drawNetworkContent(Canvas canvas) {
        if (showWifi) {
            if (displayConfig.wifiIcon) drawWifi(canvas);
        } else if (!networkLabel.isEmpty()) {
            drawNetworkLabel(canvas);
        }
    }

    private void drawNetworkLabel(Canvas canvas) {
        if (networkTextDirty) {
            networkTextPath.rewind();
            networkText.getTextPath("5G", 0, 2, 0f, 0f, networkTextPath);
            networkTextPath.computeBounds(networkTextBounds, true);
            networkReferenceWidth = networkTextBounds.width();
            if (!"5G".equals(networkLabel)) {
                networkTextPath.rewind();
                networkText.getTextPath(networkLabel, 0, networkLabel.length(),
                        0f, 0f, networkTextPath);
                networkTextPath.computeBounds(networkTextBounds, true);
            }
            networkTextDirty = false;
        }
        if (networkTextBounds.isEmpty()) return;

        // Fit actual ink to the same visible frame as Wi-Fi, independently of its slot.
        // The 5G reference keeps short labels narrow; long labels stay inside the frame.
        float scaleX = FusionIconGeometry.networkContentWidth(displayConfig.strokeScale)
                / Math.max(networkReferenceWidth, networkTextBounds.width());
        float scaleY = FusionIconGeometry.networkContentHeight(displayConfig.strokeScale)
                / networkTextBounds.height();
        networkText.setColor(tint);
        canvas.save();
        canvas.translate(FusionIconGeometry.RING_CENTER_X, FusionIconGeometry.MIDDLE_CONTENT_Y);
        canvas.scale(scaleX, scaleY);
        canvas.translate(-networkTextBounds.centerX(), -networkTextBounds.centerY());
        canvas.drawPath(networkTextPath, networkText);
        canvas.restore();
    }

    private void drawPercent(Canvas canvas, float centerY, float size, float maxWidth) {
        if (batteryPercent >= 0) {
            drawText(canvas, Integer.toString(Math.max(0, Math.min(100, batteryPercent))),
                    centerY, size, maxWidth);
        }
    }

    private void drawText(Canvas canvas, String value, float centerY, float size, float maxWidth) {
        text.setColor(tint);
        text.setTextSize(size);
        float measured = text.measureText(value);
        if (measured > maxWidth) {
            text.setTextSize(size * maxWidth / measured);
        }
        Paint.FontMetrics metrics = text.getFontMetrics();
        canvas.drawText(value, FusionIconGeometry.RING_CENTER_X,
                centerY - (metrics.ascent + metrics.descent) / 2f, text);
    }

    private void drawWifi(Canvas canvas) {
        float cx = FusionIconGeometry.RING_CENTER_X;
        float cy = WIFI_ANCHOR_Y;
        int strength = clamp(wifiLevel);
        body.setColor(tint);
        body.setStyle(Paint.Style.STROKE);
        body.setStrokeCap(Paint.Cap.ROUND);
        body.setStrokeWidth(stroke(FusionIconGeometry.WIFI_STROKE_WIDTH));
        int outerArcAlpha = strength >= 4 ? 255 : strength == 3 ? 160 : 52;
        drawWifiArc(canvas, cx, cy, FusionIconGeometry.WIFI_OUTER_RADIUS, outerArcAlpha);
        drawWifiArc(canvas, cx, cy, FusionIconGeometry.WIFI_INNER_RADIUS, strength >= 2 ? 255 : 52);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(tint);
        fill.setAlpha(Math.round(Color.alpha(tint) * (strength >= 1 ? 1f : 52f / 255f)));
        canvas.drawCircle(cx, cy, stroke(FusionIconGeometry.WIFI_DOT_RADIUS), fill);
    }

    private void drawLightning(Canvas canvas) {
        body.setColor(fastCharging ? FAST_CHARGING_COLOR : tint);
        body.setStyle(Paint.Style.FILL_AND_STROKE);
        body.setStrokeWidth(stroke(5f));
        body.setStrokeJoin(Paint.Join.ROUND);
        canvas.save();
        boolean middle = displayConfig.fusionContent.batteryPosition == FusionContentConfig.MIDDLE;
        if (!middle) {
            canvas.translate(FusionIconGeometry.RING_CENTER_X, CENTER_CONTENT_Y);
            canvas.scale(0.65f, 0.65f);
            canvas.translate(-FusionIconGeometry.RING_CENTER_X, -CENTER_CONTENT_Y);
        } else if (displayConfig.fusionContent.signalPosition == FusionContentConfig.BOTTOM) {
            // Heavier signal dots reach further into the center; keep the bolt clear of them.
            canvas.translate(0f, -Math.max(0, displayConfig.strokeScale - 100) * 0.32f);
        }
        canvas.drawPath(lightning, body);
        canvas.restore();
    }

    private void drawWifiArc(Canvas canvas, float centerX, float centerY,
            float radius, int alpha) {
        body.setAlpha(Math.round(Color.alpha(tint) * alpha / 255f));
        canvas.drawArc(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius, 220f, 100f, false, body);
        body.setAlpha(Color.alpha(tint));
    }

    private void drawSignals(Canvas canvas) {
        float innerRowY = FusionIconGeometry.innerSignalRowY(displayConfig.strokeScale);
        float outerRowY = FusionIconGeometry.outerSignalRowY(displayConfig.strokeScale);
        drawSignalRow(canvas, signalLevel, innerRowY);
        drawSignalRow(canvas, signalLevel2, outerRowY);
    }

    private void drawSignalRow(Canvas canvas, int level, float centerY) {
        // Keep both rows on one axis, while following the shallow U shape of the reference icon.
        int lit = clamp(level);
        int activeTint = tint;
        int solid = Color.alpha(activeTint);
        int dim = Math.round(solid * 0.22f);
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {
            int alpha = i < lit ? solid : dim;
            fill.setColor(Color.argb(alpha, Color.red(activeTint), Color.green(activeTint),
                    Color.blue(activeTint)));
            float x = FusionIconGeometry.signalX(i);
            float y = FusionIconGeometry.signalY(centerY, i);
            canvas.drawCircle(x, y, stroke(10.5f), fill);
        }
    }

    private int batteryTint() {
        if (charging) {
            return CHARGING_COLOR;
        }
        if (batteryPercent >= 0 && batteryPercent <= 20) {
            return DANGER_COLOR;
        }
        if (powerSave) {
            return POWER_SAVE_COLOR;
        }
        return tint;
    }

    private void onStatus() {
        if (preview) {
            return;
        }
        render(StatusMonitor.snapshot());
    }

    private void render(StatusMonitor.Snapshot snapshot) {
        batteryPercent = snapshot.batteryPercent;
        charging = snapshot.charging;
        fastCharging = snapshot.fastCharging;
        powerSave = snapshot.powerSave;
        showWifi = snapshot.showWifi;
        wifiLevel = clamp(snapshot.wifiLevel);
        if (!networkLabel.equals(snapshot.networkLabel)) {
            networkLabel = snapshot.networkLabel;
            networkTextDirty = true;
        }
        signalLevel = clamp(snapshot.signalLevel);
        signalLevel2 = clamp(snapshot.signalLevel2);
        contentLayout = FusionIconGeometry.contentLayout(
                displayConfig.fusionContent, displayConfig.strokeScale, showWifi);
        setContentDescription(describe());
        invalidate();
    }

    private String describe() {
        StringBuilder builder = new StringBuilder();
        if (batteryPercent >= 0) {
            builder.append("\u7535\u91cf ").append(batteryPercent);
        }
        if (charging) {
            appendPart(builder, fastCharging ? "快充" : "充电");
        } else if (powerSave) {
            appendPart(builder, "省电");
        }
        if (showWifi) {
            appendPart(builder, "Wi-Fi " + clamp(wifiLevel) + "/4");
        } else if (networkLabel != null && !networkLabel.isEmpty()) {
            appendPart(builder, networkLabel);
        }
        appendPart(builder, "\u4fe1\u53f71 " + clamp(signalLevel));
        appendPart(builder, "\u4fe1\u53f72 " + clamp(signalLevel2));
        return builder.toString();
    }

    private static void appendPart(StringBuilder builder, String part) {
        if (builder.length() > 0) {
            builder.append('\uff0c');
        }
        builder.append(part);
    }

    private void reloadConfig() {
        if (preview) {
            return;
        }
        setDisplayConfig(FusionConfig.read(getContext()));
    }

    private boolean isPreview() {
        return preview || "preview".equals(getTag());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float stroke(float designWidth) {
        return FusionIconGeometry.stroke(designWidth, displayConfig.strokeScale);
    }

    int preferredStatusHeight() {
        return dp(displayConfig.doubleRow ? 40 : 40);
    }

    private static int clamp(int level) {
        if (level < 0) {
            return 0;
        }
        return Math.min(level, 4);
    }
}

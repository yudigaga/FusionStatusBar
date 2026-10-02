package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
    // The Wi-Fi arcs extend further above their anchor than the dot extends below it.
    private static final float WIFI_ANCHOR_Y = FusionIconGeometry.WIFI_ANCHOR_Y;
    private static final float TEXT_MAX_WIDTH = 116f;
    private static final float BATTERY_TEXT_SIZE = 62f;
    private static final float LABEL_MAX_WIDTH = 172f;
    private static final float NETWORK_LABEL_TEXT_SIZE = 70f;
    private static final Typeface BASE_TYPEFACE = Typeface.create("sans-serif", Typeface.NORMAL);

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final StatusMonitor.Listener listener = this::onStatus;

    private boolean preview;
    private int batteryPercent = -1;
    private boolean charging;
    private boolean showWifi;
    private String networkLabel = "";
    private int signalLevel;
    private int signalLevel2;
    private int tint = Color.WHITE;
    private FusionConfig displayConfig = FusionConfig.defaults();
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
        text.setTypeface(Typeface.create(BASE_TYPEFACE, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        text.setStyle(Paint.Style.FILL);
    }

    public void showSample(int battery, boolean wifi, String label, int level, int color) {
        showSample(battery, wifi, label, level, level, color);
    }

    public void showSample(
            int battery, boolean wifi, String label, int level1, int level2, int color) {
        preview = true;
        StatusMonitor.removeListener(listener);
        batteryPercent = battery;
        showWifi = wifi;
        networkLabel = label == null ? "" : label;
        signalLevel = clamp(level1);
        signalLevel2 = clamp(level2);
        tint = color;
        setContentDescription(describe());
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
        int weight = Math.max(400, Math.min(900,
                700 + (displayConfig.strokeScale - 100) * 5));
        text.setTypeface(Typeface.create(BASE_TYPEFACE, weight, false));
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
        drawPercent(canvas);
        drawCenter(canvas);
        drawSignals(canvas);
        canvas.restore();
    }

    private void drawBattery(Canvas canvas) {
        float cx = 135.5f;
        float cy = RING_CENTER_Y;
        body.setColor(renderTint());
        body.setStyle(Paint.Style.STROKE);
        body.setStrokeWidth(stroke(RING_STROKE_WIDTH));
        body.setStrokeCap(Paint.Cap.ROUND);
        body.setAlpha(72);
        canvas.drawArc(cx - RING_RADIUS, cy - RING_RADIUS,
                cx + RING_RADIUS, cy + RING_RADIUS,
                225f, -90f, false, body);
        canvas.drawArc(cx - RING_RADIUS, cy - RING_RADIUS,
                cx + RING_RADIUS, cy + RING_RADIUS,
                315f, 90f, false, body);
        body.setAlpha(255);
        float progress = batteryPercent < 0 ? 180f
                : 180f * Math.max(0, Math.min(100, batteryPercent)) / 100f;
        float leftProgress = Math.min(90f, progress);
        float rightProgress = Math.max(0f, progress - 90f);
        if (leftProgress > 0f) {
            canvas.drawArc(cx - RING_RADIUS, cy - RING_RADIUS,
                    cx + RING_RADIUS, cy + RING_RADIUS,
                    225f, -leftProgress, false, body);
        }
        if (rightProgress > 0f) {
            canvas.drawArc(cx - RING_RADIUS, cy - RING_RADIUS,
                    cx + RING_RADIUS, cy + RING_RADIUS,
                    315f, rightProgress, false, body);
        }
    }

    private void drawPercent(Canvas canvas) {
        if (batteryPercent < 0) {
            return;
        }
        float centerX = 135.5f;
        float centerY = 62f;
        String value = Integer.toString(Math.max(0, Math.min(100, batteryPercent)));
        text.setColor(renderTint());
        text.setTextSize(BATTERY_TEXT_SIZE);
        if (value.length() >= 3) {
            float measured = text.measureText(value);
            if (measured > TEXT_MAX_WIDTH) {
                text.setTextSize(BATTERY_TEXT_SIZE * TEXT_MAX_WIDTH / measured);
            }
        }
        Paint.FontMetrics metrics = text.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(value, centerX, baseline, text);
    }

    private void drawCenter(Canvas canvas) {
        float centerX = 135.5f;
        if (showWifi) {
            float cx = centerX;
            float cy = WIFI_ANCHOR_Y;
            if (!displayConfig.wifiIcon) {
                return;
            }
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(renderTint());
            canvas.drawCircle(cx, cy + 17f, stroke(11f), fill);
            body.setColor(renderTint());
            body.setStyle(Paint.Style.STROKE);
            body.setStrokeCap(Paint.Cap.ROUND);
            body.setStrokeWidth(stroke(14f));
            canvas.drawArc(cx - 34f, cy - 21f, cx + 34f, cy + 47f, 220f, 100f, false, body);
            canvas.drawArc(cx - 53f, cy - 40f, cx + 53f, cy + 66f, 220f, 100f, false, body);
            return;
        }
        if (networkLabel == null || networkLabel.isEmpty()) {
            return;
        }
        text.setColor(renderTint());
        text.setTextSize(NETWORK_LABEL_TEXT_SIZE);
        float measured = text.measureText(networkLabel);
        if (measured > LABEL_MAX_WIDTH) {
            text.setTextSize(NETWORK_LABEL_TEXT_SIZE * LABEL_MAX_WIDTH / measured);
        }
        Paint.FontMetrics metrics = text.getFontMetrics();
        float y = RING_CENTER_Y - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(networkLabel, centerX, y, text);
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
        int activeTint = renderTint();
        int solid = Color.alpha(activeTint);
        int dim = Math.round(solid * 0.22f);
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 4; i++) {
            int alpha = i < lit ? solid : dim;
            fill.setColor(Color.argb(alpha, Color.red(activeTint), Color.green(activeTint),
                    Color.blue(activeTint)));
            float x = FusionIconGeometry.signalX(i);
            float y = FusionIconGeometry.signalY(centerY, i);
            canvas.drawCircle(x, y, stroke(8.5f), fill);
        }
    }

    private int renderTint() {
        if (charging) {
            return Color.rgb(50, 220, 96);
        }
        if (batteryPercent >= 0 && batteryPercent <= 20) {
            return Color.rgb(245, 65, 62);
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
        showWifi = snapshot.showWifi;
        networkLabel = snapshot.networkLabel;
        signalLevel = snapshot.signalLevel;
        signalLevel2 = snapshot.signalLevel2;
        setContentDescription(describe());
        invalidate();
    }

    private String describe() {
        StringBuilder builder = new StringBuilder();
        if (batteryPercent >= 0) {
            builder.append("\u7535\u91cf ").append(batteryPercent);
        }
        if (showWifi) {
            appendPart(builder, "Wi-Fi");
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

package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.util.function.Supplier;

/** Keeps the group touch slot stable while scaling a member tile's surface and glyph together. */
@android.annotation.SuppressLint("ViewConstructor")
final class ControlCenterGroupEntry extends ViewGroup {
    private final View icon;
    private final View surface;
    private final float surfaceScaleX, surfaceScaleY;
    private final float surfacePivotX, surfacePivotY;
    private final boolean showLabel, nativeTile;
    private final int iconScale;
    private final TextView title, subtitle;
    private final Supplier<String[]> labels;
    private long refreshedAt;
    private final ViewTreeObserver.OnPreDrawListener refresh = () -> {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - refreshedAt >= 200) { refreshLabels(); refreshedAt = now; }
        return true;
    };
    private ViewTreeObserver observer;
    private int iconSize, baseIconSize, naturalSize, labelGap;

    View iconView() { return icon; }

    ControlCenterGroupEntry(Context context, View icon, boolean showLabel, boolean nativeTile, Supplier<String[]> labels) {
        this(context, icon, showLabel, nativeTile, ControlCenterGroupData.DEFAULT_ICON_SCALE, labels);
    }
    ControlCenterGroupEntry(Context context, View icon, boolean showLabel, boolean nativeTile, int iconScale, Supplier<String[]> labels) {
        super(context);
        if (iconScale < ControlCenterGroupData.MIN_ICON_SCALE || iconScale > ControlCenterGroupData.MAX_ICON_SCALE)
            throw new IllegalArgumentException("Invalid member icon scale");
        this.icon = icon; this.showLabel = showLabel; this.nativeTile = nativeTile; this.labels = labels;
        this.iconScale = iconScale;
        Object surfaceView = nativeTile ? ControlCenterRuntimeGrid.readField(icon, "icon") : null;
        surface = surfaceView instanceof View view && view != icon ? view : null;
        surfaceScaleX = surface == null ? 1f : surface.getScaleX();
        surfaceScaleY = surface == null ? 1f : surface.getScaleY();
        surfacePivotX = surface == null ? 0f : surface.getPivotX();
        surfacePivotY = surface == null ? 0f : surface.getPivotY();
        setClipChildren(true);
        addView(icon);
        title = text(14, Color.WHITE); subtitle = text(11, 0xffafb0b4);
        if (showLabel) { addView(title); addView(subtitle); }
        if (nativeTile) {
            setOnClickListener(v -> icon.performClick());
            setOnLongClickListener(v -> icon.performLongClick());
        }
        refreshLabels();
    }

    private TextView text(int size, int color) {
        TextView text = new TextView(getContext());
        text.setTextSize(size); text.setTextColor(color); text.setMaxLines(1);
        text.setEllipsize(TextUtils.TruncateAt.END); text.setIncludeFontPadding(false);
        text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return text;
    }
    void normalizeContent() {
        icon.setVisibility(VISIBLE); icon.setAlpha(1f); icon.setTranslationX(0); icon.setTranslationY(0);
        requestLayout();
    }

    void applySurface(ControlCenterSurface surface) {
        boolean light = surface != null && surface.neutralForeground() == 0xff202124;
        title.setTextColor(light ? 0xff202124 : Color.WHITE);
        subtitle.setTextColor(light ? 0xff555b62 : 0xffafb0b4);
    }
    private void refreshLabels() {
        if (!showLabel) return;
        String[] values = labels.get();
        if (!TextUtils.equals(title.getText(), values[0])) title.setText(values[0]);
        if (!TextUtils.equals(subtitle.getText(), values[1])) subtitle.setText(values[1]);
        setContentDescription(values[0] + " " + values[1]);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (nativeTile && showLabel) { observer = getViewTreeObserver(); observer.addOnPreDrawListener(refresh); }
    }
    @Override protected void onDetachedFromWindow() {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(refresh);
        observer = null;
        super.onDetachedFromWindow();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        baseIconSize = Math.max(1, Math.min(h, showLabel ? Math.min(w / 3, dp(42)) : w));
        labelGap = showLabel ? Math.min(dp(6), Math.max(0, w - 2)) : 0;
        int maximumSize = Math.max(1, Math.min(h, showLabel ? (w - labelGap) / 2 : w));
        iconSize = Math.max(1, Math.min(maximumSize,
                Math.round(baseIconSize * iconScale / 100f)));
        naturalSize = nativeTile ? ControlCenterGridGeometry.rowHeightPx(getContext()) : dp(72);
        icon.measure(MeasureSpec.makeMeasureSpec(naturalSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(naturalSize, MeasureSpec.EXACTLY));
        if (showLabel) {
            int labelWidth = Math.max(1, w - iconSize - labelGap);
            title.measure(MeasureSpec.makeMeasureSpec(labelWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.min(dp(20), h / 2), MeasureSpec.EXACTLY));
            subtitle.measure(MeasureSpec.makeMeasureSpec(labelWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.min(dp(16), h / 2), MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x = showLabel ? 0 : (getWidth() - iconSize) / 2;
        int y = (getHeight() - iconSize) / 2;
        icon.layout(x, y, x + naturalSize, y + naturalSize);
        icon.setPivotX(0); icon.setPivotY(0);
        float scale = (surface == null ? iconSize : baseIconSize)
                / (float) Math.max(1, naturalSize);
        icon.setScaleX(scale); icon.setScaleY(scale);
        if (surface != null) {
            float memberScale = iconSize / (float) Math.max(1, baseIconSize);
            surface.setPivotX(surface.getWidth() / 2f);
            surface.setPivotY(surface.getHeight() / 2f);
            surface.setScaleX(surfaceScaleX * memberScale);
            surface.setScaleY(surfaceScaleY * memberScale);
        }
        if (showLabel) {
            int top = Math.max(0, (getHeight() - title.getMeasuredHeight() - subtitle.getMeasuredHeight()) / 2);
            int left = iconSize + labelGap;
            title.layout(left, top, getWidth(), top + title.getMeasuredHeight());
            subtitle.layout(left, title.getBottom(), getWidth(), title.getBottom() + subtitle.getMeasuredHeight());
        }
    }

    void release() {
        if (surface != null) {
            surface.setScaleX(surfaceScaleX);
            surface.setScaleY(surfaceScaleY);
            surface.setPivotX(surfacePivotX);
            surface.setPivotY(surfacePivotY);
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

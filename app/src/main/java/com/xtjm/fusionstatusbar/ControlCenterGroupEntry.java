package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.ImageView;
import java.util.function.Supplier;

/** Keeps the native touch target while scaling its glyph inside stable member bounds. */
@android.annotation.SuppressLint("ViewConstructor")
final class ControlCenterGroupEntry extends ViewGroup {
    private final View icon;
    private final View glyph;
    private final float glyphScaleX, glyphScaleY;
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
    private int iconSize, naturalSize, labelGap;

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
        glyph = findGlyph(icon);
        glyphScaleX = glyph == null ? 1f : glyph.getScaleX();
        glyphScaleY = glyph == null ? 1f : glyph.getScaleY();
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
        int baseSize = Math.max(1, Math.min(h, showLabel ? Math.min(w / 3, dp(42)) : w));
        labelGap = showLabel ? Math.min(dp(6), Math.max(0, w - 2)) : 0;
        int maximumSize = Math.max(1, Math.min(h, showLabel ? (w - labelGap) / 2 : w));
        iconSize = glyph != null ? baseSize
                : Math.max(1, Math.min(maximumSize, Math.round(baseSize * iconScale / 100f)));
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
        float scale = iconSize / (float) Math.max(1, naturalSize);
        icon.setScaleX(scale); icon.setScaleY(scale);
        if (glyph != null) scaleGlyph();
        if (showLabel) {
            int top = Math.max(0, (getHeight() - title.getMeasuredHeight() - subtitle.getMeasuredHeight()) / 2);
            int left = iconSize + labelGap;
            title.layout(left, top, getWidth(), top + title.getMeasuredHeight());
            subtitle.layout(left, title.getBottom(), getWidth(), title.getBottom() + subtitle.getMeasuredHeight());
        }
    }
    private View findGlyph(View view) {
        if (view instanceof ControlCenterPreviewTile preview && preview.getChildCount() > 0)
            return preview.getChildAt(0);
        Object first = ControlCenterRuntimeGrid.readField(view, "icon");
        if (first instanceof View container && container != view) {
            Object nested = ControlCenterRuntimeGrid.readField(container, "icon");
            if (nested instanceof View image && image != container) return image;
            if (!(container instanceof ViewGroup)) return container;
            View image = findGlyph(container);
            if (image != null) return image;
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ImageView) return child;
            View nested = findGlyph(child);
            if (nested != null) return nested;
        }
        return null;
    }
    private void scaleGlyph() {
        if (iconScale == ControlCenterGroupData.DEFAULT_ICON_SCALE) return;
        int width = glyph.getWidth(), height = glyph.getHeight();
        if (width <= 0 || height <= 0) return;
        float factor = iconScale / 100f;
        float centerX = glyph.getLeft() + width / 2f, centerY = glyph.getTop() + height / 2f;
        View child = glyph;
        // Keep the glyph inside every native ancestor; its circular surface and touch slot stay fixed.
        while (child.getParent() instanceof View parent) {
            float availableX = Math.min(centerX, parent.getWidth() - centerX) * 2f;
            float availableY = Math.min(centerY, parent.getHeight() - centerY) * 2f;
            factor = Math.min(factor, Math.min(availableX / (width * Math.max(0.01f, Math.abs(glyphScaleX))),
                    availableY / (height * Math.max(0.01f, Math.abs(glyphScaleY)))));
            if (parent == icon) break;
            centerX += parent.getLeft(); centerY += parent.getTop(); child = parent;
        }
        factor = Math.max(0.01f, factor);
        glyph.setPivotX(width / 2f); glyph.setPivotY(height / 2f);
        glyph.setScaleX(glyphScaleX * factor); glyph.setScaleY(glyphScaleY * factor);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.util.function.Supplier;

/** Keeps a bound native tile as the icon/touch target, without resizing its OEM internals. */
@android.annotation.SuppressLint("ViewConstructor")
final class ControlCenterGroupEntry extends ViewGroup {
    private final View icon;
    private final boolean showLabel, nativeTile;
    private final TextView title, subtitle;
    private final Supplier<String[]> labels;
    private long refreshedAt;
    private final ViewTreeObserver.OnPreDrawListener refresh = () -> {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - refreshedAt >= 200) { refreshLabels(); refreshedAt = now; }
        return true;
    };
    private ViewTreeObserver observer;
    private int iconSize, naturalSize;

    View iconView() { return icon; }

    ControlCenterGroupEntry(Context context, View icon, boolean showLabel, boolean nativeTile, Supplier<String[]> labels) {
        super(context);
        this.icon = icon; this.showLabel = showLabel; this.nativeTile = nativeTile; this.labels = labels;
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
        iconSize = Math.max(1, Math.min(h, showLabel ? Math.min(w / 3, dp(42)) : w));
        naturalSize = nativeTile ? ControlCenterGridGeometry.rowHeightPx(getContext()) : dp(72);
        icon.measure(MeasureSpec.makeMeasureSpec(naturalSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(naturalSize, MeasureSpec.EXACTLY));
        if (showLabel) {
            int labelWidth = Math.max(1, w - iconSize - dp(6));
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
        if (showLabel) {
            int top = Math.max(0, (getHeight() - title.getMeasuredHeight() - subtitle.getMeasuredHeight()) / 2);
            int left = iconSize + dp(6);
            title.layout(left, top, getWidth(), top + title.getMeasuredHeight());
            subtitle.layout(left, title.getBottom(), getWidth(), title.getBottom() + subtitle.getMeasuredHeight());
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

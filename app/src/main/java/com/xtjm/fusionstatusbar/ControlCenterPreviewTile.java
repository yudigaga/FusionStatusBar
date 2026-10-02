package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** App-side tile content shared by the settings preview and the editable renderer. */
final class ControlCenterPreviewTile extends FrameLayout {
    static final int MODE_CIRCLE = 0;
    static final int MODE_COMPACT = 1;
    static final int MODE_LARGE = 2;
    private final GlyphView glyph;
    private TextView titleView, subtitleView;

    ControlCenterPreviewTile(Context context, String spec, String title, String subtitle,
            int mode, boolean on, int cornerRadius, int scale) {
        this(context, spec, title, subtitle, mode, on, cornerRadius, scale, true);
    }

    ControlCenterPreviewTile(Context context, String spec, String title, String subtitle,
            int mode, boolean on, int cornerRadius, int scale, boolean drawSurface) {
        this(context, spec, title, subtitle, mode, on, cornerRadius, scale, drawSurface, null);
    }

    ControlCenterPreviewTile(Context context, String spec, String title, String subtitle,
            int mode, boolean on, int cornerRadius, int scale, boolean drawSurface, android.graphics.Bitmap nativeIcon) {
        super(context);
        float contentScale = Math.max(0.7f, Math.min(1.3f, scale / 100f));
        if (drawSurface) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(on ? ControlCenterCardView.ACTIVE_SURFACE : ControlCenterCardView.INACTIVE_SURFACE);
            background.setCornerRadius(mode == MODE_CIRCLE ? dp(100) : dp(Math.max(10, cornerRadius)));
            setBackground(background);
        }
        glyph = new GlyphView(context, spec, on, contentScale, nativeIcon);
        int backdrop = ControlCenterIcons.composite(on ? ControlCenterCardView.ACTIVE_SURFACE
                : ControlCenterCardView.INACTIVE_SURFACE, 100, 0xff080808);
        if (mode == MODE_CIRCLE) {
            addView(glyph, new LayoutParams(dp(48), dp(48), Gravity.CENTER));
            setBackdropColor(backdrop);
            return;
        }
        boolean large = mode == MODE_LARGE;
        setPadding(dp(large ? 16 : 8), dp(large ? 8 : 4), dp(large ? 12 : 8), dp(large ? 8 : 4));
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int glyphSize = dp(large ? 40 : 22);
        row.addView(glyph, new LinearLayout.LayoutParams(glyphSize, glyphSize));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        titleView = text(context, title, (large ? 16 : 11) * contentScale, Color.WHITE);
        labels.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        if (large) {
            subtitleView = text(context, subtitle, 13 * contentScale, Color.WHITE);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
            statusParams.topMargin = dp(3);
            labels.addView(subtitleView, statusParams);
        }
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.leftMargin = dp(large ? 8 : 5);
        row.addView(labels, labelParams);
        addView(row, new LayoutParams(-1, -1));
        setBackdropColor(backdrop);
    }

    void setBackdropColor(int backdrop) {
        int foreground = ControlCenterIcons.foregroundColor(backdrop);
        glyph.setBackdropColor(backdrop);
        if (titleView != null) titleView.setTextColor(foreground);
        if (subtitleView != null) subtitleView.setTextColor(ControlCenterIcons.composite(foreground, 80, backdrop));
    }

    private TextView text(Context context, String value, float size, int color) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setTextSize(size);
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setAutoSizeTextTypeUniformWithConfiguration(8, Math.max(9, Math.round(size)), 1,
                TypedValue.COMPLEX_UNIT_SP);
        return view;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final class GlyphView extends View {
        private final ControlCenterIcons.Artwork artwork;
        private final float scale;
        private final boolean horizontalBattery;

        GlyphView(Context context, String spec, boolean active, float scale, android.graphics.Bitmap nativeIcon) {
            super(context);
            artwork = new ControlCenterIcons.Artwork(context, spec, active, nativeIcon);
            this.scale = scale;
            horizontalBattery = !artwork.captured && ("battery".equalsIgnoreCase(spec) || "batterysaver".equalsIgnoreCase(spec));
        }

        void setBackdropColor(int backdrop) { artwork.setBackdrop(backdrop); invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            int save = canvas.save();
            if (horizontalBattery) canvas.rotate(-90, cx, cy);
            ControlCenterIcons.draw(canvas, artwork.drawable, cx, cy, Math.min(getWidth(), getHeight())
                    * Math.min(1f, 0.82f * scale));
            canvas.restoreToCount(save);
        }
    }
}

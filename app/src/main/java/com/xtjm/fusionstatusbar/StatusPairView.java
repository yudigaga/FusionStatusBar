package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A fixed-width readout with two values stacked inside its selected row. */
final class StatusPairView extends LinearLayout {
    private final TextView first;
    private final TextView second;
    private String lastText = "";
    private int tint = Color.WHITE;
    private int preferredWidth;

    StatusPairView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        first = line(context);
        second = line(context);
        addView(first, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        addView(second, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        second.setVisibility(GONE);
    }

    private static TextView line(Context context) {
        TextView view = new TextView(context);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextColor(Color.WHITE);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    void setLines(String text) {
        String value = text == null ? "" : text;
        if (value.equals(lastText)) return;
        lastText = value;
        int divider = value.indexOf('\n');
        first.setText(divider < 0 ? value : value.substring(0, divider));
        if (divider < 0) {
            if (second.getVisibility() != GONE) second.setVisibility(GONE);
        } else {
            second.setText(value.substring(divider + 1));
            if (second.getVisibility() != VISIBLE) second.setVisibility(VISIBLE);
        }
        setContentDescription(value.replace('\n', ' '));
    }

    void setTint(int color) {
        if (tint == color) return;
        tint = color;
        first.setTextColor(color);
        second.setTextColor(color);
    }

    void setSize(float sp) {
        first.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        second.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    int naturalWidth() {
        float width = Math.max(first.getPaint().measureText(
                        String.valueOf(first.getText())),
                second.getPaint().measureText(String.valueOf(second.getText())));
        return Math.max(1, (int) Math.ceil(width));
    }

    void setAlignment(int alignment) {
        int horizontal = alignment == TelemetryConfig.ALIGN_LEFT
                ? Gravity.START
                : alignment == TelemetryConfig.ALIGN_RIGHT
                ? Gravity.END : Gravity.CENTER_HORIZONTAL;
        int gravity = Gravity.CENTER_VERTICAL | horizontal;
        first.setGravity(gravity);
        second.setGravity(gravity);
    }

    void setBold(boolean value) {
        int style = value ? Typeface.BOLD : Typeface.NORMAL;
        first.setTypeface(Typeface.DEFAULT, style);
        second.setTypeface(Typeface.DEFAULT, style);
    }

    void setLineSpacing(int spacingDp) {
        float offset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                spacingDp / 2f, getResources().getDisplayMetrics());
        first.setTranslationY(-offset);
        second.setTranslationY(offset);
    }

    void setPreferredWidth(int width) {
        int value = Math.max(0, width);
        if (preferredWidth == value) return;
        preferredWidth = value;
        requestLayout();
    }

    int preferredWidth() {
        return preferredWidth;
    }
}

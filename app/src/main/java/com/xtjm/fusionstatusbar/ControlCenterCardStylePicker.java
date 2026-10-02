package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.function.UnaryOperator;

/** Edits only appearance; the caller returns the accepted draft, including rejected/locked edits. */
final class ControlCenterCardStylePicker extends LinearLayout {
    private final SettingsChoiceSelector presets;
    private final SeekBar radius;
    private final TextView value;
    private final int textColor;
    private final int surfaceColor;
    private final UnaryOperator<ControlCenterLayoutPlan.Item> update;
    private ControlCenterLayoutPlan.Item current;

    ControlCenterCardStylePicker(Context context, ControlCenterLayoutPlan.Item item, int textColor, int surfaceColor,
            UnaryOperator<ControlCenterLayoutPlan.Item> update) {
        super(context);
        this.update = update;
        this.textColor = textColor;
        this.surfaceColor = surfaceColor;
        setOrientation(VERTICAL);
        setPadding(0, dp(8), 0, dp(8));
        TextView heading = new TextView(context);
        heading.setText("卡片样式");
        heading.setTextSize(16);
        heading.setTextColor(textColor);
        addView(heading, new LayoutParams(-1, -2));
        ControlCenterCardStyle[] styles = ControlCenterCardStyle.values();
        String[] labels = new String[styles.length];
        boolean[] disabled = new boolean[styles.length];
        for (int index = 0; index < styles.length; index++) {
            labels[index] = styles[index].label;
            disabled[index] = styles[index] == ControlCenterCardStyle.CUSTOM;
        }
        presets = new SettingsChoiceSelector(context, "卡片样式", labels, 0, surfaceColor,
                textColor, Color.GRAY, Color.rgb(52, 130, 255), disabled,
                index -> styleSwatch(styles[index]), position -> {
            ControlCenterCardStyle style = styles[position];
            if (style != ControlCenterCardStyle.CUSTOM && style != ControlCenterCardStyle.of(current)) {
                bind(update.apply(style.apply(current)));
            }
        });
        addView(presets, new LayoutParams(-1, -2));
        LinearLayout radiusRow = new LinearLayout(context);
        radiusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(context);
        label.setText("独立圆角");
        label.setTextSize(14);
        label.setTextColor(textColor);
        radiusRow.addView(label, new LayoutParams(0, -2, 1));
        value = new TextView(context);
        value.setTextSize(13);
        value.setTextColor(textColor);
        radiusRow.addView(value, new LayoutParams(-2, -2));
        addView(radiusRow, new LayoutParams(-1, dp(32)));
        radius = new SeekBar(context);
        radius.setContentDescription("独立圆角");
        radius.setMax(64);
        radius.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(52, 130, 255)));
        radius.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(52, 130, 255)));
        radius.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.argb(72, Color.red(textColor), Color.green(textColor), Color.blue(textColor))));
        addView(radius, new LayoutParams(-1, dp(48)));
        bind(item);
        radius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && bar.isEnabled()) bind(update.apply(current.withShape(current.shape, progress)));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    private View styleSwatch(ControlCenterCardStyle style) {
        View swatch = new View(getContext());
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.rgb(136, 140, 147));
        shape.setShape(style == ControlCenterCardStyle.CIRCLE ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        shape.setCornerRadius(dp(style == ControlCenterCardStyle.CAPSULE ? 12
                : style == ControlCenterCardStyle.DEFAULT || style == ControlCenterCardStyle.CUSTOM ? 6
                : Math.min(11, style.radius / 4f)));
        swatch.setBackground(shape);
        return swatch;
    }

    void bind(ControlCenterLayoutPlan.Item item) {
        if (item == null) return;
        current = item;
        presets.setSelection(ControlCenterCardStyle.of(item).ordinal());
        presets.setEnabled(!item.locked);
        radius.setProgress(item.cornerRadius);
        radius.setEnabled(!item.locked && item.shape == ControlCenterLayoutPlan.Shape.RECTANGLE);
        value.setText(item.shape != ControlCenterLayoutPlan.Shape.RECTANGLE ? "自动"
                : item.cornerRadius == 0 ? "默认" : item.cornerRadius + " dp");
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

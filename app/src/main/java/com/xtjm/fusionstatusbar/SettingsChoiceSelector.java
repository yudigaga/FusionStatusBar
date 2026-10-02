package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.function.IntConsumer;
import java.util.function.Function;

/** An inline settings choice that stays inside its owning sheet window. */
final class SettingsChoiceSelector extends LinearLayout {
    private final String[] labels;
    private final IntConsumer update;
    private final int primary;
    private final int secondary;
    private final int accent;
    private final Function<Integer, View> optionDecoration;
    private final ArrayList<LinearLayout> options = new ArrayList<>();
    private final ArrayList<TextView> optionLabels = new ArrayList<>();
    private final ArrayList<TextView> selectionMarks = new ArrayList<>();
    private final ArrayList<Boolean> optionEnabled = new ArrayList<>();
    private TextView heading;
    private LinearLayout valueRow;
    private TextView selectedValue;
    private TextView disclosure;
    private LinearLayout optionList;
    private int selected;

    SettingsChoiceSelector(Context context, String title, String[] labels, int selected,
            int background, int primary, int secondary, int accent, IntConsumer update) {
        this(context, title, labels, selected, background, primary, secondary, accent,
                new boolean[labels == null ? 0 : labels.length], null, true, update);
    }

    SettingsChoiceSelector(Context context, String title, String[] labels, int selected,
            int background, int primary, int secondary, int accent, boolean[] disabledOptions,
            IntConsumer update) {
        this(context, title, labels, selected, background, primary, secondary, accent, disabledOptions,
                null, false, update);
    }

    SettingsChoiceSelector(Context context, String title, String[] labels, int selected,
            int background, int primary, int secondary, int accent, boolean[] disabledOptions,
            Function<Integer, View> optionDecoration, IntConsumer update) {
        this(context, title, labels, selected, background, primary, secondary, accent, disabledOptions,
                optionDecoration, false, update);
    }

    private SettingsChoiceSelector(Context context, String title, String[] labels, int selected,
            int background, int primary, int secondary, int accent, boolean[] disabledOptions,
            Function<Integer, View> optionDecoration, boolean allOptionsEnabled, IntConsumer update) {
        super(context);
        if (labels == null || labels.length == 0) throw new IllegalArgumentException("A choice needs at least one option.");
        if (disabledOptions == null || disabledOptions.length != labels.length) {
            throw new IllegalArgumentException("Option state must match the labels.");
        }
        this.labels = labels.clone();
        this.selected = Math.max(0, Math.min(selected, labels.length - 1));
        this.primary = primary;
        this.secondary = secondary;
        this.accent = accent;
        this.optionDecoration = optionDecoration;
        this.update = update;
        setOrientation(VERTICAL);
        setContentDescription(title);

        heading = new TextView(context);
        heading.setText(title);
        heading.setTextSize(15);
        heading.setTextColor(primary);
        addView(heading, wrap());

        valueRow = new LinearLayout(context);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        valueRow.setMinimumHeight(dp(52));
        valueRow.setPadding(dp(14), dp(4), dp(14), dp(4));
        valueRow.setBackground(surface(background, dp(8)));
        valueRow.setContentDescription("当前选择");
        valueRow.setFocusable(true);
        valueRow.setClickable(true);

        selectedValue = new TextView(context);
        selectedValue.setTextSize(15);
        valueRow.addView(selectedValue, new LinearLayout.LayoutParams(0, -2, 1));

        disclosure = new TextView(context);
        disclosure.setTextSize(18);
        disclosure.setGravity(Gravity.CENTER);
        valueRow.addView(disclosure, new LinearLayout.LayoutParams(dp(28), -2));
        valueRow.setOnClickListener(view -> {
            if (isEnabled()) setExpanded(optionList.getVisibility() != View.VISIBLE);
        });
        setOnClickListener(view -> {
            if (isEnabled()) setExpanded(optionList.getVisibility() != View.VISIBLE);
        });
        LinearLayout.LayoutParams valueParams = wrap();
        valueParams.topMargin = dp(6);
        addView(valueRow, valueParams);

        optionList = new LinearLayout(context);
        optionList.setOrientation(VERTICAL);
        optionList.setPadding(0, dp(4), 0, dp(4));
        optionList.setBackground(surface(background, dp(8)));
        optionList.setVisibility(View.GONE);
        LinearLayout.LayoutParams optionsParams = wrap();
        optionsParams.topMargin = dp(4);
        addView(optionList, optionsParams);

        for (int index = 0; index < this.labels.length; index++) {
            addOption(context, index, allOptionsEnabled || !disabledOptions[index]);
        }
        renderSelection();
        updateTextColors(isEnabled());
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (valueRow == null) return;
        valueRow.setEnabled(enabled);
        for (int index = 0; index < options.size(); index++) {
            options.get(index).setEnabled(enabled && optionEnabled.get(index));
        }
        if (!enabled) setExpanded(false);
        updateTextColors(enabled);
    }

    void setSelection(int index) {
        selected = Math.max(0, Math.min(index, labels.length - 1));
        renderSelection();
    }

    int selectedIndex() { return selected; }

    View optionView(int index) { return options.get(index); }

    void setOptionEnabled(int index, boolean enabled) {
        if (index < 0 || index >= options.size()) throw new IndexOutOfBoundsException("option=" + index);
        optionEnabled.set(index, enabled);
        options.get(index).setEnabled(enabled);
        options.get(index).setAlpha(enabled ? 1f : 0.45f);
        optionLabels.get(index).setTextColor(enabled ? primary : secondary);
    }

    private void addOption(Context context, int index, boolean enabled) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(14), dp(4), dp(14), dp(4));
        row.setFocusable(true);
        row.setClickable(true);
        row.setContentDescription(labels[index]);
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.45f);
        row.setOnClickListener(view -> {
            if (isEnabled() && row.isEnabled()) select(index);
        });

        if (optionDecoration != null) {
            View decoration = optionDecoration.apply(index);
            if (decoration != null) {
                row.addView(decoration, new LinearLayout.LayoutParams(dp(40), dp(28)));
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
                labelParams.leftMargin = dp(10);
                addLabel(context, row, index, enabled, labelParams);
            } else {
                addLabel(context, row, index, enabled, new LinearLayout.LayoutParams(0, -2, 1));
            }
        } else {
            addLabel(context, row, index, enabled, new LinearLayout.LayoutParams(0, -2, 1));
        }

        TextView mark = new TextView(context);
        mark.setText("✓");
        mark.setTextSize(18);
        mark.setGravity(Gravity.CENTER);
        mark.setContentDescription("已选择");
        row.addView(mark, new LinearLayout.LayoutParams(dp(28), -2));

        optionList.addView(row, wrap());
        options.add(row);
        optionEnabled.add(enabled);
        selectionMarks.add(mark);
    }

    private void addLabel(Context context, LinearLayout row, int index, boolean enabled,
            LinearLayout.LayoutParams params) {
        TextView label = new TextView(context);
        label.setText(labels[index]);
        label.setTextSize(15);
        label.setTextColor(enabled ? primary : secondary);
        row.addView(label, params);
        optionLabels.add(label);
    }

    private void select(int index) {
        try {
            if (index != selected) {
                update.accept(index);
                selected = index;
                renderSelection();
            }
        } finally {
            setExpanded(false);
        }
    }

    private void renderSelection() {
        selectedValue.setText(labels[selected]);
        selectedValue.setContentDescription(labels[selected]);
        valueRow.setStateDescription("当前值：" + labels[selected]);
        setStateDescription("当前值：" + labels[selected]);
        for (int index = 0; index < options.size(); index++) {
            boolean checked = index == selected;
            options.get(index).setSelected(checked);
            options.get(index).setStateDescription(checked ? "已选择" : null);
            selectionMarks.get(index).setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
            selectionMarks.get(index).setTextColor(accent);
        }
    }

    private void setExpanded(boolean expanded) {
        if (optionList == null) return;
        optionList.setVisibility(expanded ? View.VISIBLE : View.GONE);
        disclosure.setText(expanded ? "⌃" : "⌄");
        String state = "当前值：" + labels[selected] + (expanded ? "，已展开" : "，已收起");
        valueRow.setStateDescription(state);
        setStateDescription(state);
        if (expanded && isLaidOut()) {
            post(() -> {
                if (optionList.getVisibility() == View.VISIBLE && getParent() != null) {
                    requestRectangleOnScreen(new Rect(0, 0, getWidth(), getHeight()), true);
                }
            });
        }
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private void updateTextColors(boolean enabled) {
        int labelColor = enabled ? primary : secondary;
        heading.setTextColor(labelColor);
        selectedValue.setTextColor(labelColor);
        disclosure.setTextColor(enabled ? secondary : 0xffaaaaaa);
        for (TextView label : optionLabels) label.setTextColor(labelColor);
    }

    private GradientDrawable surface(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-1, -2); }
}

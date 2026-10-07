package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

/** Single-choice rows for settings sheets. */
final class SettingsRadioListAdapter extends BaseAdapter {
    private static final int MIN_ROW_HEIGHT_DP = 52;

    private final Context context;
    private final String[] labels;
    private final int background;
    private final int primary;
    private final int accent;
    private int selectedPosition;

    SettingsRadioListAdapter(Context context, String[] labels, int selectedPosition,
            int background, int primary, int accent) {
        if (labels == null || labels.length == 0) {
            throw new IllegalArgumentException("A choice list needs at least one label.");
        }
        this.context = context;
        this.labels = labels.clone();
        this.background = background;
        this.primary = primary;
        this.accent = accent;
        this.selectedPosition = clamp(selectedPosition);
    }

    @Override public int getCount() { return labels.length; }
    @Override public String getItem(int position) { return labels[position]; }
    @Override public long getItemId(int position) { return position; }

    int selectedPosition() { return selectedPosition; }

    void setSelectedPosition(int position) {
        int next = clamp(position);
        if (selectedPosition != next) {
            selectedPosition = next;
            notifyDataSetChanged();
        }
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout row;
        Holder holder;
        if (convertView instanceof LinearLayout && convertView.getTag() instanceof Holder) {
            row = (LinearLayout) convertView;
            holder = (Holder) row.getTag();
        } else {
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(MIN_ROW_HEIGHT_DP));
            row.setPadding(dp(14), dp(8), dp(14), dp(8));
            row.setFocusable(false);
            row.setBackground(createRippleBackground());

            RadioButton radio = new RadioButton(context);
            radio.setButtonTintList(new ColorStateList(
                    new int[][] { new int[] { android.R.attr.state_checked }, new int[0] },
                    new int[] { accent, primary }));
            radio.setFocusable(false);
            radio.setClickable(false);
            radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(radio, new LinearLayout.LayoutParams(dp(32), dp(32)));

            TextView label = new TextView(context);
            label.setTextSize(16);
            label.setTextColor(primary);
            label.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
            labelParams.setMarginStart(dp(12));
            row.addView(label, labelParams);

            holder = new Holder(radio, label);
            row.setTag(holder);
        }

        boolean checked = position == selectedPosition;
        String label = labels[position];
        holder.radio.setChecked(checked);
        holder.label.setText(label);
        row.setSelected(checked);
        row.setContentDescription(label);
        row.setStateDescription(checked ? "已选择" : "未选择");
        return row;
    }

    private RippleDrawable createRippleBackground() {
        int rippleColor = (accent & 0x00ffffff) | 0x26000000;
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(8));
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                new ColorDrawable(background), mask);
    }

    private int clamp(int position) {
        return position < 0 || position >= labels.length ? -1 : position;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Holder {
        final RadioButton radio;
        final TextView label;

        Holder(RadioButton radio, TextView label) {
            this.radio = radio;
            this.label = label;
        }
    }
}

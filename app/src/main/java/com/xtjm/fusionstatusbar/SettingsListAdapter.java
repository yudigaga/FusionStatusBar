package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntFunction;

/** Theme-aware choice rows shared by the settings sheets. */
final class SettingsListAdapter extends BaseAdapter {
    private final Context context;
    private final String[] labels;
    private final int background;
    private final int primary;
    private final int accent;
    private final IntFunction<Drawable> icons;

    SettingsListAdapter(Context context, String[] labels, int background, int primary, int accent,
            IntFunction<Drawable> icons) {
        this.context = context;
        this.labels = labels.clone();
        this.background = background;
        this.primary = primary;
        this.accent = accent;
        this.icons = icons;
    }

    @Override public int getCount() { return labels.length; }
    @Override public String getItem(int position) { return labels[position]; }
    @Override public long getItemId(int position) { return position; }

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
            row.setMinimumHeight(dp(52));
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            row.setBackground(new RippleDrawable(ColorStateList.valueOf((accent & 0x00ffffff) | 0x26000000),
                    surface(background), surface(Color.WHITE)));

            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

            TextView label = new TextView(context);
            label.setTextSize(16);
            label.setTextColor(primary);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
            labelParams.setMarginStart(dp(12));
            row.addView(label, labelParams);
            holder = new Holder(icon, label);
            row.setTag(holder);
        }
        holder.label.setText(labels[position]);
        holder.icon.setImageDrawable(icons == null ? null : icons.apply(position));
        row.setContentDescription(labels[position]);
        return row;
    }

    private GradientDrawable surface(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private static final class Holder {
        final ImageView icon;
        final TextView label;
        Holder(ImageView icon, TextView label) { this.icon = icon; this.label = label; }
    }
}

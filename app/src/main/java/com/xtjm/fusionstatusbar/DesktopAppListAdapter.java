package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Application choice rows keep selection tied to the unfiltered catalog. */
final class DesktopAppListAdapter extends BaseAdapter {
    private static final int ICON_CACHE_SIZE = 128;

    private final Context context;
    private final PackageManager packageManager;
    private final List<DesktopAppCatalog.App> apps;
    private final List<Integer> visibleIndices = new ArrayList<>();
    private final boolean[] checked;
    private final int background;
    private final int primary;
    private final int secondary;
    private final int accent;
    private final LruCache<String, Drawable> icons = new LruCache<>(ICON_CACHE_SIZE);
    private int selectedSourceIndex;

    DesktopAppListAdapter(Context context, List<DesktopAppCatalog.App> apps, boolean[] checked,
            int selectedSourceIndex, int background, int primary, int secondary, int accent) {
        if (apps == null || checked != null && checked.length != apps.size()) {
            throw new IllegalArgumentException("Application selection must match its catalog.");
        }
        this.context = context;
        packageManager = context.getPackageManager();
        this.apps = new ArrayList<>(apps);
        this.checked = checked;
        this.selectedSourceIndex = selectedSourceIndex >= 0 && selectedSourceIndex < apps.size()
                ? selectedSourceIndex : -1;
        this.background = background;
        this.primary = primary;
        this.secondary = secondary;
        this.accent = accent;
        setQuery("");
    }

    void setQuery(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        visibleIndices.clear();
        for (int i = 0; i < apps.size(); i++) {
            DesktopAppCatalog.App app = apps.get(i);
            if (needle.isEmpty() || app.label.toLowerCase(Locale.ROOT).contains(needle)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(needle)) {
                visibleIndices.add(i);
            }
        }
        notifyDataSetChanged();
    }

    int sourceIndex(int visiblePosition) { return visibleIndices.get(visiblePosition); }

    void toggle(int visiblePosition) {
        int sourceIndex = sourceIndex(visiblePosition);
        if (checked == null) selectedSourceIndex = sourceIndex;
        else checked[sourceIndex] = !checked[sourceIndex];
        notifyDataSetChanged();
    }

    @Override public int getCount() { return visibleIndices.size(); }
    @Override public String getItem(int position) {
        return apps.get(sourceIndex(position)).displayLabel(context);
    }
    @Override public long getItemId(int position) { return sourceIndex(position); }
    @Override public boolean hasStableIds() { return true; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout row;
        Holder holder;
        if (convertView instanceof LinearLayout && convertView.getTag() instanceof Holder
                && ((Holder) convertView.getTag()).multiple == (checked != null)) {
            row = (LinearLayout) convertView;
            holder = (Holder) row.getTag();
        } else {
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(64));
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setFocusable(false);
            row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            row.setBackground(createRippleBackground());

            ImageView icon = new ImageView(context);
            icon.setId(android.R.id.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
            labelParams.setMarginStart(dp(12));
            row.addView(labels, labelParams);

            TextView title = new TextView(context);
            title.setId(android.R.id.text1);
            title.setTextSize(16);
            title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            title.setTextColor(primary);
            title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            labels.addView(title, new LinearLayout.LayoutParams(-1, -2));

            TextView subtitle = new TextView(context);
            subtitle.setId(android.R.id.text2);
            subtitle.setTextSize(12);
            subtitle.setTextColor(secondary);
            subtitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            labels.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

            CompoundButton choice = checked == null ? new RadioButton(context) : new CheckBox(context);
            choice.setButtonTintList(new ColorStateList(
                    new int[][] { new int[] { android.R.attr.state_checked }, new int[0] },
                    new int[] { accent, secondary }));
            choice.setFocusable(false);
            choice.setClickable(false);
            choice.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            choiceParams.setMarginStart(dp(12));
            row.addView(choice, choiceParams);

            holder = new Holder(icon, title, subtitle, choice, checked != null);
            row.setTag(holder);
        }

        int sourceIndex = sourceIndex(position);
        DesktopAppCatalog.App app = apps.get(sourceIndex);
        boolean selected = checked == null ? sourceIndex == selectedSourceIndex : checked[sourceIndex];
        holder.icon.setImageDrawable(icon(app.packageName));
        holder.title.setText(app.label);
        int stateLabel = app.stateLabel();
        holder.subtitle.setText(app.packageName
                + (stateLabel == 0 ? "" : " \u00b7 " + context.getString(stateLabel)));
        holder.choice.setChecked(selected);
        row.setSelected(selected);
        row.setContentDescription(app.displayLabel(context));
        row.setStateDescription(selected ? "\u5df2\u9009\u62e9"
                : context.getString(R.string.desktop_not_selected));
        return row;
    }

    private Drawable icon(String packageName) {
        Drawable drawable = icons.get(packageName);
        if (drawable == null) {
            try {
                drawable = packageManager.getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { }
            if (drawable == null) drawable = packageManager.getDefaultActivityIcon();
            icons.put(packageName, drawable);
        }
        Drawable.ConstantState state = drawable.getConstantState();
        return state == null ? drawable : state.newDrawable(context.getResources());
    }

    private RippleDrawable createRippleBackground() {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(8));
        return new RippleDrawable(ColorStateList.valueOf((accent & 0x00ffffff) | 0x26000000),
                new ColorDrawable(background), mask);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Holder {
        final ImageView icon;
        final TextView title;
        final TextView subtitle;
        final CompoundButton choice;
        final boolean multiple;

        Holder(ImageView icon, TextView title, TextView subtitle, CompoundButton choice,
                boolean multiple) {
            this.icon = icon;
            this.title = title;
            this.subtitle = subtitle;
            this.choice = choice;
            this.multiple = multiple;
        }
    }
}

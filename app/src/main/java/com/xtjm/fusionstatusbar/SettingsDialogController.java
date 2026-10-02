package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Shared settings sheet sizing and lifetime; pages supply only their controls. */
final class SettingsDialogController implements AutoCloseable {
    private final Activity activity;
    private final ArrayList<Dialog> dialogs = new ArrayList<>();
    SettingsDialogController(Activity activity) { this.activity = activity; }

    void show(String title, Consumer<LinearLayout> builder, int background, int textColor, int accent) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Sheet sheet = createSheet(title, R.string.editor_done, background, textColor, accent);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        builder.accept(content);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        sheet.shell.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        showSheet(sheet);
    }

    Dialog showList(String title, ListAdapter adapter, IntConsumer selection,
            int background, int textColor, int accent) {
        if (activity.isFinishing() || activity.isDestroyed()) return null;
        Sheet sheet = createSheet(title, R.string.backup_cancel, background, textColor, accent);
        ListView list = new ListView(activity);
        list.setId(android.R.id.list);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setPadding(0, dp(8), 0, 0);
        list.setAdapter(adapter);
        boolean[] selected = {false};
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (selected[0] || !sheet.dialog.isShowing() || !adapter.isEnabled(position)) return;
            selected[0] = true;
            // A choice owns only this list window; its parent properties stay open.
            sheet.dialog.dismiss();
            selection.accept(position);
        });
        sheet.shell.addView(list, new LinearLayout.LayoutParams(-1, -2));
        showSheet(sheet);
        return sheet.dialog;
    }

    private Sheet createSheet(String title, int dismissLabel, int background, int textColor, int accent) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int maxSheetHeight = Math.round(metrics.heightPixels * .88f);
        LinearLayout shell = new SheetLayout(activity, maxSheetHeight);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(22), dp(18), dp(22), dp(18));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(background); surface.setCornerRadius(dp(28)); shell.setBackground(surface);
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(activity);
        heading.setText(title); heading.setTextSize(22); heading.setTextColor(textColor);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setMaxLines(2);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView done = new TextView(activity);
        done.setText(dismissLabel); done.setTextColor(accent); done.setTextSize(15);
        done.setGravity(Gravity.CENTER); done.setOnClickListener(view -> dialog.dismiss());
        header.addView(done, new LinearLayout.LayoutParams(dp(64), dp(48)));
        shell.addView(header, new LinearLayout.LayoutParams(-1, -2));
        return new Sheet(dialog, shell);
    }

    private void showSheet(Sheet sheet) {
        Dialog dialog = sheet.dialog;
        dialog.setContentView(sheet.shell);
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setWindowAnimations(R.style.SettingsSheetWindowAnimation);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = .42f; window.setAttributes(attributes);
            window.setGravity(Gravity.BOTTOM);
            int width = Math.min(metrics.widthPixels - dp(24), dp(420));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnDismissListener(ignored -> dialogs.remove(dialog));
        dialogs.add(dialog);
        dialog.show();
    }

    private static final class Sheet {
        final Dialog dialog;
        final LinearLayout shell;
        Sheet(Dialog dialog, LinearLayout shell) { this.dialog = dialog; this.shell = shell; }
    }

    @Override public void close() {
        for (Dialog dialog : new ArrayList<>(dialogs)) dialog.dismiss();
        dialogs.clear();
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private static final class SheetLayout extends LinearLayout {
        private final int maxHeight;

        SheetLayout(Context context, int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int requested = MeasureSpec.getSize(heightMeasureSpec);
            int limit = mode == MeasureSpec.UNSPECIFIED ? maxHeight : Math.min(maxHeight, requested);
            // Limit the whole sheet so the actual header height and IME constraints
            // determine the remaining scroll viewport during the same layout pass.
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
        }
    }
}

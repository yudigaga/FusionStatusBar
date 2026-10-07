package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.BooleanSupplier;

/** Shared settings sheet sizing and lifetime; pages supply only their controls. */
final class SettingsDialogController implements AutoCloseable {
    private final Activity activity;
    private final MiuixSheetHost miuix;
    private final ArrayList<Dialog> dialogs = new ArrayList<>();
    SettingsDialogController(Activity activity) {
        this.activity = activity;
        this.miuix = activity instanceof androidx.activity.ComponentActivity owner
                ? new MiuixSheetHost(owner) : null;
    }

    void show(String title, Consumer<LinearLayout> builder, int background, int textColor, int accent) {
        show(title, builder, background, textColor, accent, () -> true);
    }

    void show(String title, Consumer<LinearLayout> builder, int background, int textColor,
            int accent, BooleanSupplier confirm) {
        show(title, builder, background, textColor, accent, R.string.editor_done, confirm);
    }

    void show(String title, Consumer<LinearLayout> builder, int background, int textColor,
            int accent, int confirmLabel, BooleanSupplier confirm) {
        if (miuix != null) {
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            builder.accept(content);
            miuix.showNative(title, content, activity.getString(confirmLabel), confirm);
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) return;
        Sheet sheet = createSheet(title, confirmLabel, background, textColor, accent,
                confirm);
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
        if (miuix != null) {
            return miuix.showAdapter(title, adapter, selection);
        }
        if (activity.isFinishing() || activity.isDestroyed()) return null;
        Sheet sheet = createSheet(title, R.string.backup_cancel, background, textColor, accent,
                null);
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

    Dialog showSearchableList(String title, ListAdapter adapter, Consumer<String> filter,
            IntConsumer selection, int background, int textColor, int accent,
            BooleanSupplier confirm) {
        if (activity.isFinishing() || activity.isDestroyed()) return null;
        Sheet sheet = createSheet(title, confirm == null ? R.string.backup_cancel : R.string.editor_done,
                background, textColor, accent, confirm);
        sheet.shell.setFocusableInTouchMode(true);

        LinearLayout searchBar = new LinearLayout(activity);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setMinimumHeight(dp(48));
        searchBar.setPaddingRelative(dp(12), 0, 0, 0);
        boolean dark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int secondary = dark ? 0xffa7a7ad : 0xff77777d;
        GradientDrawable field = new GradientDrawable();
        field.setColor(dark ? 0xff2c2c2e : 0xfff0f0f2);
        field.setCornerRadius(dp(8));
        searchBar.setBackground(field);
        ImageView searchIcon = new ImageView(activity);
        searchIcon.setImageResource(android.R.drawable.ic_menu_search);
        searchIcon.setImageTintList(ColorStateList.valueOf(secondary));
        searchIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        searchBar.addView(searchIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        EditText search = new EditText(activity);
        search.setId(R.id.settings_list_search);
        search.setHint(R.string.desktop_search_apps);
        search.setContentDescription(activity.getString(R.string.desktop_search_apps));
        search.setTextSize(15);
        search.setTextColor(textColor);
        search.setHintTextColor(secondary);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPaddingRelative(dp(8), dp(10), 0, dp(10));
        searchBar.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton clear = new ImageButton(activity);
        clear.setId(R.id.settings_list_search_clear);
        clear.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        clear.setImageTintList(ColorStateList.valueOf(secondary));
        clear.setBackgroundColor(Color.TRANSPARENT);
        clear.setContentDescription(activity.getString(R.string.desktop_clear_search));
        clear.setTooltipText(activity.getString(R.string.desktop_clear_search));
        clear.setVisibility(View.INVISIBLE);
        clear.setOnClickListener(view -> search.setText(""));
        searchBar.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, -2);
        searchParams.topMargin = dp(8);
        searchParams.bottomMargin = dp(8);
        sheet.shell.addView(searchBar, searchParams);

        ListView list = new ListView(activity);
        list.setId(android.R.id.list);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setAdapter(adapter);
        boolean[] selected = {false};
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (!sheet.dialog.isShowing() || selected[0] || !adapter.isEnabled(position)) return;
            if (confirm == null) {
                selected[0] = true;
                sheet.dialog.dismiss();
            }
            selection.accept(position);
        });
        int viewportHeight = Math.min(dp(420),
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * .56f));
        FrameLayout viewport = new SearchViewport(activity, viewportHeight);
        viewport.addView(list, new FrameLayout.LayoutParams(-1, -1));
        TextView empty = new TextView(activity);
        empty.setText(R.string.desktop_search_empty);
        empty.setTextSize(14);
        empty.setTextColor(secondary);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(8), dp(8), dp(8), dp(8));
        viewport.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty);
        sheet.shell.addView(viewport, new LinearLayout.LayoutParams(-1, -2));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                filter.accept(text.toString());
                list.setSelection(0);
                clear.setVisibility(text.length() == 0 ? View.INVISIBLE : View.VISIBLE);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        search.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            InputMethodManager keyboard = activity.getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
            return true;
        });
        showSheet(sheet);
        sheet.shell.requestFocus();
        return sheet.dialog;
    }

    private Sheet createSheet(String title, int dismissLabel, int background, int textColor,
            int accent, BooleanSupplier confirm) {
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
        done.setGravity(Gravity.CENTER);
        done.setOnClickListener(view -> {
            if (confirm == null || confirm.getAsBoolean()) dialog.dismiss();
        });
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
        if (miuix != null) miuix.close();
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

    private static final class SearchViewport extends FrameLayout {
        private final int preferredHeight;

        SearchViewport(Context context, int preferredHeight) {
            super(context);
            this.preferredHeight = preferredHeight;
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int available = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? preferredHeight : MeasureSpec.getSize(heightMeasureSpec);
            // Filtering keeps the viewport stable; the keyboard may reduce its available height.
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                    Math.min(preferredHeight, available), MeasureSpec.EXACTLY));
        }
    }
}

package com.xtjm.fusionstatusbar;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Group-specific controls embedded in the existing item property dialog. */
@android.annotation.SuppressLint("ViewConstructor")
final class ControlCenterGroupEditor extends LinearLayout {
    interface ChoicePicker {
        void show(List<String> specs, java.util.function.IntConsumer selected);
    }
    private final Supplier<ControlCenterLayoutPlan.Item> current;
    private final Supplier<List<String>> candidates;
    private final Consumer<ControlCenterGroupData> update;
    private final Function<String, String> label;
    private final int color;
    private final Runnable split;
    private final ChoicePicker picker;

    ControlCenterGroupEditor(Context context, Supplier<ControlCenterLayoutPlan.Item> current,
            Supplier<List<String>> candidates, Consumer<ControlCenterGroupData> update,
            Function<String, String> label, Runnable split, int color, ChoicePicker picker) {
        super(context);
        this.current = current; this.candidates = candidates; this.update = update;
        this.label = label; this.split = split; this.color = color;
        this.picker = picker;
        setOrientation(VERTICAL);
        refresh();
    }

    void refresh() {
        removeAllViews();
        ControlCenterLayoutPlan.Item item = current.get();
        if (item == null) return;
        addView(text(tr(R.string.ui_control_group_contents_count,
                item.group.members.size(), ControlCenterGroupData.MAX_MEMBERS)));
        SettingsChoiceSelector arrangement = options(tr(R.string.ui_control_group_arrangement), ControlCenterGroupData.Arrangement.values(), item.group.arrangement.ordinal(), index -> {
            ControlCenterLayoutPlan.Item latest = current.get();
            if (latest != null) commit(latest.group.arrange(ControlCenterGroupData.Arrangement.values()[index]));
        });
        arrangement.setEnabled(!item.locked);
        for (int i = 0; i < item.group.members.size(); i++) {
            int index = i;
            ControlCenterGroupData.Member member = item.group.members.get(i);
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView name = text(label.apply(member.spec));
            name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LayoutParams(0, -2, 1));
            icon(row, android.R.drawable.arrow_up_float, tr(R.string.ui_control_group_move_up_action, label.apply(member.spec)), !item.locked && i > 0,
                    () -> commit(current.get().group.move(index, index - 1)));
            icon(row, android.R.drawable.arrow_down_float, tr(R.string.ui_control_group_move_down_action, label.apply(member.spec)), !item.locked && i + 1 < item.group.members.size(),
                    () -> commit(current.get().group.move(index, index + 1)));
            icon(row, android.R.drawable.ic_menu_edit, tr(R.string.ui_control_group_edit_action, label.apply(member.spec)), !item.locked, () -> edit(member.spec));
            icon(row, android.R.drawable.ic_menu_close_clear_cancel, tr(R.string.ui_control_group_remove_action, label.apply(member.spec)), !item.locked,
                    () -> commit(current.get().group.remove(member.spec)));
            addView(row, new LayoutParams(-1, dp(48)));
        }
        Button add = getContext() instanceof androidx.activity.ComponentActivity
                ? new MiuixControls.Command(getContext()) : new Button(getContext());
        add.setText(tr(R.string.ui_control_group_add_member)); add.setEnabled(!item.locked && item.group.members.size() < 7);
        add.setTextColor(Color.WHITE);
        add.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff286dd1));
        add.setOnClickListener(v -> {
            List<String> specs = candidates.get();
            if (specs.isEmpty()) { Toast.makeText(getContext(), tr(R.string.ui_control_group_no_candidates), Toast.LENGTH_SHORT).show(); return; }
            picker.show(specs, position -> {
                ControlCenterLayoutPlan.Item latest = current.get();
                if (latest != null && !latest.locked && candidates.get().contains(specs.get(position))) {
                    try { commit(latest.group.add(specs.get(position))); }
                    catch (IllegalArgumentException error) {
                        Toast.makeText(getContext(), tr(R.string.ui_control_group_no_space), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
        if (getContext() instanceof androidx.activity.ComponentActivity) {
            MiuixControls.attachButton(this, add, true, new LayoutParams(-1, -2));
        } else addView(add, new LayoutParams(-1, dp(48)));
        Button ungroup = getContext() instanceof androidx.activity.ComponentActivity
                ? new MiuixControls.Command(getContext()) : new Button(getContext());
        ungroup.setText(tr(R.string.ui_control_group_ungroup)); ungroup.setEnabled(!item.locked && !item.group.members.isEmpty());
        ungroup.setTextColor(Color.WHITE);
        ungroup.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff45474c));
        ungroup.setOnClickListener(v -> split.run());
        if (getContext() instanceof androidx.activity.ComponentActivity) {
            MiuixControls.attachButton(this, ungroup, false, new LayoutParams(-1, -2));
        } else addView(ungroup, new LayoutParams(-1, dp(48)));
    }

    private void edit(String spec) {
        ControlCenterLayoutPlan.Item item = current.get();
        if (item == null || item.locked) return;
        ControlCenterGroupData.Member member = item.group.members.stream().filter(m -> m.spec.equals(spec)).findFirst().orElse(null);
        if (member == null) return;
        if (getContext() instanceof MainActivity activity) {
            editMiuix(activity, spec, member);
            return;
        }
        LinearLayout fields = new LinearLayout(getContext());
        fields.setOrientation(VERTICAL); fields.setPadding(dp(20), dp(8), dp(20), dp(8));
        fields.setBackgroundColor(Color.red(color) > 128 ? 0xff242528 : Color.WHITE);
        int[] values = {member.x, member.y, member.width, member.height, member.iconScale};
        String[] names = {tr(R.string.ui_control_group_x), tr(R.string.ui_control_group_y), tr(R.string.ui_control_group_width), tr(R.string.ui_control_group_height), tr(R.string.ui_control_group_icon_scale)};
        EditText[] inputs = new EditText[values.length];
        for (int i = 0; i < values.length; i++) {
            LinearLayout row = new LinearLayout(getContext());
            TextView name = text(names[i]);
            row.addView(name, new LayoutParams(0, dp(48), 1));
            EditText input = new EditText(getContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setSingleLine(true);
            input.setTextColor(color);
            input.setText(String.valueOf(values[i])); input.setContentDescription(names[i]);
            row.addView(input, new LayoutParams(dp(68), dp(48))); inputs[i] = input;
            fields.addView(row);
        }
        SeekBar scale = new SeekBar(getContext());
        scale.setMin(ControlCenterGroupData.MIN_ICON_SCALE); scale.setMax(ControlCenterGroupData.MAX_ICON_SCALE);
        scale.setProgress(member.iconScale); scale.setContentDescription(tr(R.string.ui_control_group_icon_scale_slider));
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                String value = String.valueOf(progress);
                if (!value.contentEquals(inputs[4].getText())) inputs[4].setText(value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        inputs[4].addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                try {
                    int value = Integer.parseInt(text.toString());
                    if (value >= ControlCenterGroupData.MIN_ICON_SCALE && value <= ControlCenterGroupData.MAX_ICON_SCALE)
                        scale.setProgress(value);
                } catch (NumberFormatException ignored) { }
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        fields.addView(scale, new LayoutParams(-1, dp(48)));
        CheckBox show = new CheckBox(getContext());
        show.setText(tr(R.string.ui_control_group_show_name_state)); show.setTextColor(color); show.setChecked(member.showLabel); fields.addView(show);
        AlertDialog dialog = new AlertDialog.Builder(getContext(), Color.red(color) > 128
                ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle(label.apply(spec)).setView(fields)
                .setNegativeButton(tr(R.string.ui_control_group_cancel), null).setPositiveButton(tr(R.string.ui_control_group_apply), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                ControlCenterLayoutPlan.Item latest = current.get();
                if (latest == null || latest.locked) { dialog.dismiss(); return; }
                int iconScale;
                try { iconScale = Integer.parseInt(inputs[4].getText().toString()); }
                catch (NumberFormatException error) { inputs[4].setError(tr(R.string.ui_control_group_scale_number_error,
                        ControlCenterGroupData.MIN_ICON_SCALE, ControlCenterGroupData.MAX_ICON_SCALE)); return; }
                if (iconScale < ControlCenterGroupData.MIN_ICON_SCALE || iconScale > ControlCenterGroupData.MAX_ICON_SCALE) {
                    inputs[4].setError(tr(R.string.ui_control_group_scale_range_error,
                            ControlCenterGroupData.MIN_ICON_SCALE, ControlCenterGroupData.MAX_ICON_SCALE)); return;
                }
                ControlCenterGroupData.Member changed = new ControlCenterGroupData.Member(spec,
                        Integer.parseInt(inputs[0].getText().toString()), Integer.parseInt(inputs[1].getText().toString()),
                        Integer.parseInt(inputs[2].getText().toString()), Integer.parseInt(inputs[3].getText().toString()), show.isChecked(), iconScale);
                commit(latest.group.edit(changed)); dialog.dismiss();
            } catch (IllegalArgumentException error) {
                inputs[0].setError(tr(R.string.ui_control_group_geometry_error));
            }
        }));
        dialog.show();
    }

    private void editMiuix(MainActivity activity, String spec, ControlCenterGroupData.Member member) {
        LinearLayout fields = new LinearLayout(getContext());
        fields.setOrientation(VERTICAL);
        int[] values = {member.x, member.y, member.width, member.height, member.iconScale};
        String[] names = {tr(R.string.ui_control_group_x), tr(R.string.ui_control_group_y), tr(R.string.ui_control_group_width), tr(R.string.ui_control_group_height), tr(R.string.ui_control_group_icon_scale)};
        EditText[] inputs = new EditText[values.length];
        for (int i = 0; i < values.length; i++) inputs[i] = MiuixControls.numericInput(fields, names[i], values[i]);
        LinearLayout scaleRow = MiuixControls.slider(fields, tr(R.string.ui_control_group_icon_scale_slider), "", ControlCenterGroupData.MIN_ICON_SCALE,
                ControlCenterGroupData.MAX_ICON_SCALE, member.iconScale, "%", next -> inputs[4].setText(String.valueOf(next)));
        SeekBar scale = (SeekBar) scaleRow.getTag();
        inputs[4].addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                try {
                    int value = Integer.parseInt(text.toString());
                    if (value >= ControlCenterGroupData.MIN_ICON_SCALE && value <= ControlCenterGroupData.MAX_ICON_SCALE) {
                        scale.setProgress(value - ControlCenterGroupData.MIN_ICON_SCALE);
                    }
                } catch (NumberFormatException ignored) { }
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        android.widget.Switch show = MiuixControls.toggle(fields, tr(R.string.ui_control_group_show_name_state), "", member.showLabel, checked -> { });
        activity.utilitySheets().showNative(label.apply(spec), fields, tr(R.string.ui_control_group_apply), () -> {
            try {
                ControlCenterLayoutPlan.Item latest = current.get();
                if (latest == null || latest.locked) return true;
                int iconScale = Integer.parseInt(inputs[4].getText().toString());
                if (iconScale < ControlCenterGroupData.MIN_ICON_SCALE || iconScale > ControlCenterGroupData.MAX_ICON_SCALE) {
                    inputs[4].setError(tr(R.string.ui_control_group_scale_range_error,
                            ControlCenterGroupData.MIN_ICON_SCALE, ControlCenterGroupData.MAX_ICON_SCALE));
                    return false;
                }
                ControlCenterGroupData.Member changed = new ControlCenterGroupData.Member(spec,
                        Integer.parseInt(inputs[0].getText().toString()), Integer.parseInt(inputs[1].getText().toString()),
                        Integer.parseInt(inputs[2].getText().toString()), Integer.parseInt(inputs[3].getText().toString()),
                        show.isChecked(), iconScale);
                commit(latest.group.edit(changed));
                return true;
            } catch (IllegalArgumentException error) {
                inputs[0].setError(tr(R.string.ui_control_group_geometry_error));
                return false;
            }
        });
    }

    private void commit(ControlCenterGroupData group) {
        ControlCenterLayoutPlan.Item item = current.get();
        if (item == null || item.locked || group.equals(item.group)) return;
        update.accept(group); refresh();
    }
    private SettingsChoiceSelector options(String title, Object[] values, int selected, Consumer<Integer> action) {
        String[] labels = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            labels[index] = value instanceof ControlCenterGroupData.Arrangement arrangement
                    ? arrangement.label(getContext())
                    : value instanceof ControlCenterGroupData.Surface surface
                            ? surface.label(getContext()) : value.toString();
        }
        SettingsChoiceSelector selector = new SettingsChoiceSelector(getContext(), title, labels, selected,
                Color.red(color) > 128 ? 0xfff0f0f2 : 0xff2c2c2e, color,
                Color.red(color) > 128 ? 0xff77777d : 0xffa7a7ad, 0xff3482ff, action::accept);
        addView(selector, new LayoutParams(-1, -2));
        return selector;
    }
    private TextView text(String value) { TextView text = new TextView(getContext()); text.setText(value); text.setTextSize(14); text.setTextColor(color); return text; }
    private void icon(LinearLayout row, int resource, String description, boolean enabled, Runnable action) {
        if (getContext() instanceof androidx.activity.ComponentActivity) {
            MiuixControls.icon(row, resource, description, enabled, action);
            return;
        }
        ImageButton button = new ImageButton(getContext()); button.setImageResource(resource); button.setColorFilter(color);
        button.setBackgroundColor(Color.TRANSPARENT); button.setContentDescription(description); button.setTooltipText(description);
        button.setEnabled(enabled); button.setAlpha(enabled ? 1 : 0.35f); button.setOnClickListener(v -> action.run());
        row.addView(button, new LayoutParams(dp(40), dp(48)));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String tr(int resource, Object... args) {
        return ModuleStrings.get(getContext(), resource, args);
    }
}

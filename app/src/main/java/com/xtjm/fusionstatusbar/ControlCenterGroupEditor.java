package com.xtjm.fusionstatusbar;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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
        addView(text("卡片内容 · " + item.group.members.size() + " / 7"));
        SettingsChoiceSelector arrangement = options("内部排列", ControlCenterGroupData.Arrangement.values(), item.group.arrangement.ordinal(), index -> {
            ControlCenterLayoutPlan.Item latest = current.get();
            if (latest != null) commit(latest.group.arrange(ControlCenterGroupData.Arrangement.values()[index]));
        });
        arrangement.setEnabled(!item.locked);
        SettingsChoiceSelector background = options("容器背景", ControlCenterGroupData.Surface.values(), item.group.surface.ordinal(), index -> {
            ControlCenterLayoutPlan.Item latest = current.get();
            if (latest != null) commit(latest.group.withAppearance(ControlCenterGroupData.Surface.values()[index], latest.group.border));
        });
        background.setEnabled(!item.locked);
        CheckBox border = new CheckBox(getContext());
        border.setText("显示描边"); border.setTextColor(color); border.setChecked(item.group.border); border.setEnabled(!item.locked);
        border.setOnCheckedChangeListener((button, checked) -> {
            ControlCenterLayoutPlan.Item latest = current.get();
            if (latest != null) commit(latest.group.withAppearance(latest.group.surface, checked));
        });
        addView(border);
        for (int i = 0; i < item.group.members.size(); i++) {
            int index = i;
            ControlCenterGroupData.Member member = item.group.members.get(i);
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView name = text(label.apply(member.spec));
            name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LayoutParams(0, -2, 1));
            icon(row, android.R.drawable.arrow_up_float, "上移 " + label.apply(member.spec), !item.locked && i > 0,
                    () -> commit(current.get().group.move(index, index - 1)));
            icon(row, android.R.drawable.arrow_down_float, "下移 " + label.apply(member.spec), !item.locked && i + 1 < item.group.members.size(),
                    () -> commit(current.get().group.move(index, index + 1)));
            icon(row, android.R.drawable.ic_menu_edit, "编辑 " + label.apply(member.spec), !item.locked, () -> edit(member.spec));
            icon(row, android.R.drawable.ic_menu_close_clear_cancel, "移除 " + label.apply(member.spec), !item.locked,
                    () -> commit(current.get().group.remove(member.spec)));
            addView(row, new LayoutParams(-1, dp(48)));
        }
        Button add = new Button(getContext());
        add.setText("添加成员"); add.setEnabled(!item.locked && item.group.members.size() < 7);
        add.setTextColor(Color.WHITE);
        add.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff286dd1));
        add.setOnClickListener(v -> {
            List<String> specs = candidates.get();
            if (specs.isEmpty()) { Toast.makeText(getContext(), "没有可添加的磁贴", Toast.LENGTH_SHORT).show(); return; }
            picker.show(specs, position -> {
                ControlCenterLayoutPlan.Item latest = current.get();
                if (latest != null && !latest.locked && candidates.get().contains(specs.get(position))) {
                    try { commit(latest.group.add(specs.get(position))); }
                    catch (IllegalArgumentException error) {
                        Toast.makeText(getContext(), "组合内没有空位，请调整排列", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
        addView(add, new LayoutParams(-1, dp(48)));
        Button ungroup = new Button(getContext());
        ungroup.setText("拆分卡片"); ungroup.setEnabled(!item.locked && !item.group.members.isEmpty());
        ungroup.setTextColor(Color.WHITE);
        ungroup.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff45474c));
        ungroup.setOnClickListener(v -> split.run());
        addView(ungroup, new LayoutParams(-1, dp(48)));
    }

    private void edit(String spec) {
        ControlCenterLayoutPlan.Item item = current.get();
        if (item == null || item.locked) return;
        ControlCenterGroupData.Member member = item.group.members.stream().filter(m -> m.spec.equals(spec)).findFirst().orElse(null);
        if (member == null) return;
        LinearLayout fields = new LinearLayout(getContext());
        fields.setOrientation(VERTICAL); fields.setPadding(dp(20), dp(8), dp(20), dp(8));
        fields.setBackgroundColor(Color.red(color) > 128 ? 0xff242528 : Color.WHITE);
        int[] values = {member.x, member.y, member.width, member.height};
        String[] names = {"内部横坐标", "内部纵坐标", "内部宽度", "内部高度"};
        EditText[] inputs = new EditText[4];
        for (int i = 0; i < 4; i++) {
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
        CheckBox show = new CheckBox(getContext());
        show.setText("显示名称与状态"); show.setTextColor(color); show.setChecked(member.showLabel); fields.addView(show);
        AlertDialog dialog = new AlertDialog.Builder(getContext(), Color.red(color) > 128
                ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle(label.apply(spec)).setView(fields)
                .setNegativeButton("取消", null).setPositiveButton("应用", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                ControlCenterLayoutPlan.Item latest = current.get();
                if (latest == null || latest.locked) { dialog.dismiss(); return; }
                ControlCenterGroupData.Member changed = new ControlCenterGroupData.Member(spec,
                        Integer.parseInt(inputs[0].getText().toString()), Integer.parseInt(inputs[1].getText().toString()),
                        Integer.parseInt(inputs[2].getText().toString()), Integer.parseInt(inputs[3].getText().toString()), show.isChecked());
                commit(latest.group.edit(changed)); dialog.dismiss();
            } catch (IllegalArgumentException error) {
                inputs[0].setError("位置或尺寸超出 12×12 范围，或与其他成员重叠");
            }
        }));
        dialog.show();
    }

    private void commit(ControlCenterGroupData group) {
        ControlCenterLayoutPlan.Item item = current.get();
        if (item == null || item.locked || group.equals(item.group)) return;
        update.accept(group); refresh();
    }
    private SettingsChoiceSelector options(String title, Object[] values, int selected, Consumer<Integer> action) {
        String[] labels = new String[values.length];
        for (int index = 0; index < values.length; index++) labels[index] = values[index].toString();
        SettingsChoiceSelector selector = new SettingsChoiceSelector(getContext(), title, labels, selected,
                Color.red(color) > 128 ? 0xfff0f0f2 : 0xff2c2c2e, color,
                Color.red(color) > 128 ? 0xff77777d : 0xffa7a7ad, 0xff3482ff, action::accept);
        addView(selector, new LayoutParams(-1, -2));
        return selector;
    }
    private TextView text(String value) { TextView text = new TextView(getContext()); text.setText(value); text.setTextSize(14); text.setTextColor(color); return text; }
    private void icon(LinearLayout row, int resource, String description, boolean enabled, Runnable action) {
        ImageButton button = new ImageButton(getContext()); button.setImageResource(resource); button.setColorFilter(color);
        button.setBackgroundColor(Color.TRANSPARENT); button.setContentDescription(description); button.setTooltipText(description);
        button.setEnabled(enabled); button.setAlpha(enabled ? 1 : 0.35f); button.setOnClickListener(v -> action.run());
        row.addView(button, new LayoutParams(dp(40), dp(48)));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

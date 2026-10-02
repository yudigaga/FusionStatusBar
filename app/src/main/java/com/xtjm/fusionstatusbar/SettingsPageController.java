package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

/** Settings pages own their controls, validation and dependent-control state. */
final class SettingsPageController {
    interface Model {
        FusionConfig current();
        void update(FusionConfig config);
    }
    private final Activity activity;
    private final Model model;
    private final SettingsDialogController dialogs;

    SettingsPageController(Activity activity, Model model, SettingsDialogController dialogs) {
        this.activity = activity; this.model = model; this.dialogs = dialogs;
    }

    void showFusion() {
        show("融合图标", content -> {
            section(content, "显示");
            toggle(content, "显示无线网络图标", "关闭后仅保留移动网络状态", model.current().wifiIcon,
                    value -> edit(current -> current.withWifiIcon(value)));
            section(content, "尺寸");
            slider(content, "整体大小", "融合图标整体等比例缩放", 50, 150, model.current().iconScale, "%",
                    value -> edit(current -> current.withIconScale(value)));
            slider(content, "整体粗细", "圆环和内部图形同步变粗或变细", 50, 150, model.current().strokeScale, "%",
                    value -> edit(current -> current.withStrokeScale(value)));
        });
    }

    void showClock() {
        show("时间与天气", content -> {
            section(content, "时间");
            Switch custom = toggle(content, "自定义时间格式", "仅应用于状态栏时间", model.current().customClock, value -> { });
            EditText pattern = pattern(content, model.current().clockPattern, model.current().customClock, false,
                    "例如：HH:mm E", value -> edit(current -> current.withClockPattern(value)));
            custom.setOnCheckedChangeListener((button, value) -> {
                pattern.setEnabled(value);
                edit(current -> current.withCustomClock(value));
            });
            section(content, "天气");
            toggle(content, "显示天气", "在时间后显示天气摘要", model.current().showWeather,
                    value -> edit(current -> current.withShowWeather(value)));
        });
    }

    void showNotificationClock() {
        show("通知中心时间", content -> {
            NotificationClockConfig initial = model.current().notificationClock;
            section(content, "时间与日期");
            Switch enabled = toggle(content, "自定义通知中心时间", "时间和日期使用同一个格式", initial.enabled, value -> { });
            EditText format = pattern(content, initial.combinedPattern(), initial.enabled, true,
                    "例如：HH:mm\nMM/dd E", value -> notification(current -> current.withPattern(value)));
            enabled.setOnCheckedChangeListener((button, value) -> {
                format.setEnabled(value);
                notification(current -> current.withEnabled(value));
            });
            section(content, "样式");
            toggle(content, "时间居中", "通知中心时间水平居中", initial.timeCentered,
                    value -> notification(current -> current.withTimeCentered(value)));
            toggle(content, "日期自动居中", "合并布局中仅调整日期这一行的对齐", initial.dateCentered,
                    value -> notification(current -> current.withDateCentered(value)));
            toggle(content, "隐藏通知设置按钮", "隐藏通知中心右侧的设置图标", initial.hideSettings,
                    value -> notification(current -> current.withHideSettings(value)));
            optionalSize(content, "自定义字号", "关闭时保留系统原始字号", "字号", "通知中心大时间",
                    initial.sizeSp, 52, 24, 180, value -> notification(current -> current.withSize(value)));
            optionalSize(content, "自定义日期字号", "与时间同一布局，单独调整第二行字号", "日期字号",
                    "时间和日期仍属于同一个通知中心布局", initial.dateSizeSp, 16, 10, 72,
                    value -> notification(current -> current.withDateSize(value)));
            slider(content, "上下位置", "相对于系统原始位置微调", -24, 64, initial.offsetYDp, " dp",
                    value -> notification(current -> current.withOffsetY(value)));
        });
    }

    void showNotificationDate() {
        show("通知中心日期", content -> {
            NotificationClockConfig initial = model.current().notificationClock;
            section(content, "日期");
            Switch enabled = toggle(content, "自定义通知中心日期", "与通知中心时间单独设置", initial.dateEnabled, value -> { });
            EditText format = pattern(content, initial.datePattern, initial.dateEnabled, false,
                    "例如：MM/dd E", value -> notification(current -> current.withDatePattern(value)));
            enabled.setOnCheckedChangeListener((button, value) -> {
                format.setEnabled(value);
                notification(current -> current.withDateEnabled(value));
            });
            section(content, "样式");
            toggle(content, "日期居中", "通知中心日期水平居中", initial.dateCentered,
                    value -> notification(current -> current.withDateCentered(value)));
            optionalSize(content, "自定义字号", "关闭时保留系统原始字号", "字号", "仅调整日期字号",
                    initial.dateSizeSp, 18, 10, 72, value -> notification(current -> current.withDateSize(value)));
            slider(content, "上下位置", "相对于系统原始位置微调", -24, 64, initial.dateOffsetYDp, " dp",
                    value -> notification(current -> current.withDateOffsetY(value)));
        });
    }

    void showNotificationList() {
        show("通知列表", content -> slider(content, "列表下移", "起始位置", 0, 128,
                model.current().notificationClock.listOffsetYDp, " dp",
                value -> notification(current -> current.withListOffsetY(value))));
    }

    void showTelemetry() {
        show("状态栏监测", content -> {
            int[] titles = {R.string.telemetry_temperatures, R.string.telemetry_power_current, R.string.telemetry_net_speed};
            String[] summaries = {"电池温度在上，GPU 温度在下；仅显示数值", "功率和电流上下显示", "实时上传和下载速度"};
            Switch[] options = new Switch[3];
            for (int metric = 0; metric < 3; metric++) {
                final int index = metric;
                TelemetryConfig initial = model.current().telemetry;
                section(content, activity.getString(titles[metric]));
                Switch enabled = toggle(content, "启用此信息块", summaries[metric], initial.enabled(metric), value -> { });
                enabled.setContentDescription(activity.getString(titles[metric]));
                LinearLayout details = new LinearLayout(activity); details.setOrientation(LinearLayout.VERTICAL);
                content.addView(details, wrap());
                choice(details, "显示位置", positions(), initial.position(metric), value -> telemetry(current -> current.withPosition(index, value)));
                slider(details, "字号", "只影响当前信息块", 5, 14, initial.textSize(metric), " sp", value -> telemetry(current -> current.withGroupTextSize(index, value)));
                choice(details, "水平对齐", new String[]{"跟随所在侧", "左对齐", "居中", "右对齐"}, initial.alignment(metric), value -> telemetry(current -> current.withAlignment(index, value)));
                slider(details, "固定内容宽度", "0 dp 表示根据内容和可用空间自动分配", 0, 240, initial.fixedWidth(metric), " dp", value -> telemetry(current -> current.withFixedWidth(index, value)));
                slider(details, "左侧间距", "信息块左侧额外留白", 0, 32, initial.leftMargin(metric), " dp", value -> telemetry(current -> current.withLeftMargin(index, value)));
                slider(details, "右侧间距", "信息块右侧额外留白", 0, 32, initial.rightMargin(metric), " dp", value -> telemetry(current -> current.withRightMargin(index, value)));
                slider(details, "上下偏移", "微调当前信息块的垂直位置", -24, 24, initial.verticalOffset(metric), " dp", value -> telemetry(current -> current.withVerticalOffset(index, value)));
                toggle(details, "粗体", "提高小字号下的识别度", initial.bold(metric), value -> telemetry(current -> current.withBold(index, value)));
                slider(details, "双行间距", "只影响上下两行之间的距离", -4, 12, initial.lineSpacing(metric), " dp", value -> telemetry(current -> current.withLineSpacing(index, value)));
                enable(details, initial.enabled(metric));
                enabled.setOnCheckedChangeListener((button, checked) -> {
                    enable(details, checked);
                    if (index == 2 && options[0] != null) options[0].setEnabled(checked);
                    if (index == 1) {
                        if (options[1] != null) options[1].setEnabled(checked);
                        if (options[2] != null) options[2].setEnabled(checked);
                    }
                    telemetry(current -> current.withEnabled(index, checked));
                });
            }
            section(content, "显示方式");
            TelemetryConfig initial = model.current().telemetry;
            options[0] = toggle(content, "网速上下双排", "上传和下载分别占一行", initial.dualSpeed, value -> telemetry(current -> current.withDualSpeed(value)));
            options[1] = toggle(content, "电流使用 mA", "优先读取并显示毫安值", initial.currentInMilliamps, value -> telemetry(current -> current.withCurrentInMilliamps(value)));
            options[2] = toggle(content, "电流始终显示正值", "隐藏充放电方向符号", initial.currentPositive, value -> telemetry(current -> current.withCurrentPositive(value)));
            options[0].setEnabled(initial.enabled(2)); options[1].setEnabled(initial.enabled(1)); options[2].setEnabled(initial.enabled(1));
        });
    }

    void showLayout() {
        show("状态栏布局", content -> {
            FusionConfig initial = model.current();
            section(content, "基础布局");
            Switch doubleRow = toggle(content, "启用双排状态栏", "将时间、通知、系统图标和信息块分配到两排", initial.doubleRow, value -> { });
            slider(content, "状态栏高度", "双排时每排的可用空间", 32, 96, initial.statusBarHeight, " dp", value -> edit(current -> current.withStatusBarHeight(value)));
            section(content, "元素位置");
            LinearLayout placements = new LinearLayout(activity); placements.setOrientation(LinearLayout.VERTICAL); content.addView(placements, wrap());
            choice(placements, "时间", positions(), initial.clockSide * 2 + initial.clockRow, value -> edit(current -> current.withElementPosition(0, value / 2, value % 2)));
            choice(placements, "应用通知图标", positions(), initial.notificationSide * 2 + initial.notificationRow, value -> edit(current -> current.withElementPosition(1, value / 2, value % 2)));
            choice(placements, "系统图标", positions(), initial.systemSide * 2 + initial.systemRow, value -> edit(current -> current.withElementPosition(2, value / 2, value % 2)));
            choice(placements, "融合图标", positions(), initial.fusionSide * 2 + initial.fusionRow, value -> edit(current -> current.withFusionPosition(value / 2, value % 2)));
            section(content, "元素尺寸");
            slider(content, "系统图标大小", "原生系统图标的视觉大小", 50, 150, initial.systemIconScale, "%", value -> edit(current -> current.withSystemIconScale(value)));
            slider(content, "通知图标大小", "应用通知图标的视觉大小", 50, 150, initial.notificationIconScale, "%", value -> edit(current -> current.withNotificationIconScale(value)));
            section(content, "跨行融合图标");
            Switch span = toggle(content, "融合图标跨两排", "信息块和系统元素必须为融合图标让出空间", initial.spanRows, value -> { });
            LinearLayout offsets = new LinearLayout(activity); offsets.setOrientation(LinearLayout.VERTICAL); content.addView(offsets, wrap());
            slider(offsets, "左右位置", "向左或向右微调", -24, 24, initial.spanOffsetX, " dp", value -> edit(current -> current.withSpanOffsetX(value)));
            slider(offsets, "上下位置", "向上或向下微调", -24, 24, initial.spanOffsetY, " dp", value -> edit(current -> current.withSpanOffsetY(value)));
            Runnable refresh = () -> {
                enable(placements, doubleRow.isChecked()); span.setEnabled(doubleRow.isChecked());
                enable(offsets, doubleRow.isChecked() && span.isChecked());
            };
            doubleRow.setOnCheckedChangeListener((button, checked) -> { edit(current -> current.withDoubleRow(checked)); refresh.run(); });
            span.setOnCheckedChangeListener((button, checked) -> { edit(current -> current.withSpanRows(checked)); refresh.run(); });
            refresh.run();
        });
    }

    private static String[] positions() { return new String[]{"左侧 · 第一排", "左侧 · 第二排", "右侧 · 第一排", "右侧 · 第二排"}; }

    private SettingsChoiceSelector choice(LinearLayout content, String title, String[] labels,
            int selected, IntConsumer update) {
        SettingsChoiceSelector selector = new SettingsChoiceSelector(activity, title, labels, selected,
                inputColor(), primary(), secondary(), accent(), update);
        content.addView(selector, wrap());
        return selector;
    }

    private void optionalSize(LinearLayout content, String toggleTitle, String toggleHint,
            String title, String summary, int initial, int fallback, int minimum, int maximum, IntConsumer update) {
        Switch enabled = toggle(content, toggleTitle, toggleHint, initial != 0, value -> { });
        final int[] chosen = {initial == 0 ? fallback : initial};
        ViewGroup controls = slider(content, title, summary, minimum, maximum, chosen[0], " sp", value -> {
            chosen[0] = value; update.accept(value);
        });
        enable(controls, initial != 0);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            enable(controls, checked);
            update.accept(checked ? chosen[0] : 0);
        });
    }

    private EditText pattern(LinearLayout content, String initial, boolean enabled, boolean multiline,
            String hint, Consumer<String> update) {
        EditText input = new EditText(activity);
        input.setSingleLine(!multiline); input.setText(initial); input.setTextSize(15);
        input.setTextColor(primary()); input.setHintTextColor(secondary()); input.setHint(hint);
        input.setContentDescription("时间格式");
        input.setPadding(dp(14), 0, dp(14), 0); input.setBackground(background(inputColor(), 8));
        input.setEnabled(enabled); input.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams params = wrap(); params.topMargin = dp(8);
        if (multiline) {
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setMinLines(2); input.setMaxLines(2); input.setGravity(Gravity.TOP | Gravity.START);
            params.height = dp(84);
        }
        content.addView(input, params);
        TextView error = text("", 12, false); error.setTextColor(0xffc93434); error.setVisibility(View.GONE);
        content.addView(error, wrap());
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            public void onTextChanged(CharSequence value, int start, int before, int count) { }
            public void afterTextChanged(Editable value) {
                if (!input.isEnabled()) return;
                boolean valid = ClockTextFormatter.isValidPattern(value.toString());
                error.setText(valid ? "" : activity.getString(R.string.clock_format_invalid));
                error.setVisibility(valid ? View.GONE : View.VISIBLE);
                if (valid) update.accept(value.toString());
            }
        });
        return input;
    }

    private Switch toggle(LinearLayout content, String title, String summary, boolean value, Consumer<Boolean> update) {
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(58));
        LinearLayout labels = new LinearLayout(activity); labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 16, false), wrap());
        TextView hint = text(summary, 12, false); hint.setTextColor(secondary()); labels.addView(hint, wrap());
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch control = new Switch(activity); control.setContentDescription(title); control.setChecked(value);
        control.setMinimumWidth(dp(48)); control.setMinimumHeight(dp(48));
        control.setOnCheckedChangeListener((button, checked) -> update.accept(checked));
        row.addView(control, new LinearLayout.LayoutParams(-2, -2)); content.addView(row, wrap());
        return control;
    }

    private ViewGroup slider(LinearLayout content, String title, String summary, int minimum, int maximum,
            int value, String suffix, IntConsumer update) {
        LinearLayout block = new LinearLayout(activity); block.setOrientation(LinearLayout.VERTICAL); block.setPadding(0, dp(10), 0, dp(8));
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(activity); labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 16, false), wrap());
        TextView hint = text(summary, 12, false); hint.setTextColor(secondary()); labels.addView(hint, wrap());
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView reading = text(value + suffix, 14, false); reading.setTextColor(accent());
        reading.setGravity(Gravity.END); row.addView(reading, new LinearLayout.LayoutParams(dp(72), -2));
        block.addView(row, wrap());
        SeekBar bar = new SeekBar(activity); bar.setContentDescription(title); bar.setMax(maximum - minimum); bar.setProgress(value - minimum);
        bar.setMinimumHeight(dp(48)); block.addView(bar, wrap());
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar source, int progress, boolean fromUser) {
                int selected = minimum + progress; reading.setText(selected + suffix);
                if (fromUser) update.accept(selected);
            }
            public void onStartTrackingTouch(SeekBar source) { }
            public void onStopTrackingTouch(SeekBar source) { }
        });
        content.addView(block, wrap()); return block;
    }

    private void section(LinearLayout content, String title) {
        TextView text = text(title, 13, true); text.setTextColor(secondary()); text.setPadding(0, dp(18), 0, dp(8)); content.addView(text, wrap());
    }
    private void show(String title, Consumer<LinearLayout> build) { dialogs.show(title, build, night() ? 0xff1c1c1e : 0xffffffff, primary(), accent()); }
    private void edit(UnaryOperator<FusionConfig> change) { model.update(change.apply(model.current())); }
    private void notification(UnaryOperator<NotificationClockConfig> change) { edit(current -> current.withNotificationClock(change.apply(current.notificationClock))); }
    private void telemetry(UnaryOperator<TelemetryConfig> change) { edit(current -> current.withTelemetry(change.apply(current.telemetry))); }
    private TextView text(String value, float size, boolean bold) {
        TextView view = new TextView(activity); view.setText(value); view.setTextSize(size); view.setTextColor(primary());
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private static void enable(View view, boolean value) {
        view.setEnabled(value);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) enable(((ViewGroup) view).getChildAt(i), value);
    }
    private GradientDrawable background(int color, int radius) { GradientDrawable value = new GradientDrawable(); value.setColor(color); value.setCornerRadius(dp(radius)); return value; }
    private boolean night() { return (activity.getResources().getConfiguration().uiMode & 48) == 32; }
    private int primary() { return night() ? 0xfff5f5f7 : 0xff202124; }
    private int secondary() { return night() ? 0xffa7a7ad : 0xff77777d; }
    private int inputColor() { return night() ? 0xff2c2c2e : 0xfff0f0f2; }
    private int accent() { return night() ? 0xff72a7ff : 0xff3482ff; }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private static LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-1, -2); }
}

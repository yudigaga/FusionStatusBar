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
        show(tr(R.string.ui_control_settings_fusion_title), content -> {
            FusionConfig initial = model.current();
            section(content, tr(R.string.ui_control_settings_display));
            toggle(content, tr(R.string.ui_control_settings_wifi_icon), tr(R.string.ui_control_settings_wifi_icon_summary), model.current().wifiIcon,
                    value -> edit(current -> current.withWifiIcon(value)));
            toggle(content, tr(R.string.ui_control_settings_charging_icon), tr(R.string.ui_control_settings_charging_icon_summary),
                    initial.fusionContent.chargingIcon,
                    value -> edit(current -> current.withFusionChargingIcon(value)));
            section(content, tr(R.string.ui_control_settings_dimensions));
            slider(content, tr(R.string.ui_control_settings_icon_scale), tr(R.string.ui_control_settings_icon_scale_summary), 50, 150, model.current().iconScale, "%",
                    value -> edit(current -> current.withIconScale(value)));
            slider(content, tr(R.string.ui_control_settings_stroke_scale), tr(R.string.ui_control_settings_stroke_scale_summary), 50, 150, model.current().strokeScale, "%",
                    value -> edit(current -> current.withStrokeScale(value)));
            section(content, tr(R.string.ui_control_settings_content_position));
            String[] labels = {tr(R.string.ui_control_settings_top), tr(R.string.ui_control_settings_middle), tr(R.string.ui_control_settings_bottom)};
            String[] titles = {tr(R.string.ui_control_settings_battery_position), tr(R.string.ui_control_settings_network_position), tr(R.string.ui_control_settings_signal_position)};
            SettingsChoiceSelector[] selectors = new SettingsChoiceSelector[3];
            for (int part = 0; part < selectors.length; part++) {
                final int index = part;
                selectors[part] = choice(content, titles[part], labels,
                        initial.fusionContent.positionFor(part), value -> {
                            edit(current -> current.withFusionContentPosition(index, value));
                            for (int other = 0; other < selectors.length; other++) {
                                selectors[other].setSelection(model.current().fusionContent.positionFor(other));
                            }
                        });
            }
            section(content, tr(R.string.ui_control_settings_vertical_fine_tuning));
            slider(content, tr(R.string.ui_control_settings_wifi_vertical), tr(R.string.ui_control_settings_wifi_vertical_summary), -20, 20,
                    initial.fusionContent.wifiOffsetYPercent, "%",
                    value -> edit(current -> current.withFusionWifiOffsetYPercent(value)));
            slider(content, tr(R.string.ui_control_settings_mobile_vertical), tr(R.string.ui_control_settings_mobile_vertical_summary), -20, 20,
                    initial.fusionContent.mobileOffsetYPercent, "%",
                    value -> edit(current -> current.withFusionMobileOffsetYPercent(value)));
            section(content, tr(R.string.ui_control_settings_content_size));
            slider(content, tr(R.string.ui_control_settings_battery_size), tr(R.string.ui_control_settings_battery_size_summary), 50, 150,
                    initial.fusionContent.batteryScale, "%",
                    value -> edit(current -> current.withFusionContentScale(FusionContentConfig.BATTERY, value)));
            slider(content, tr(R.string.ui_control_settings_network_size), tr(R.string.ui_control_settings_network_size_summary), 50, 150,
                    initial.fusionContent.networkScale, "%",
                    value -> edit(current -> current.withFusionContentScale(FusionContentConfig.NETWORK, value)));
            slider(content, tr(R.string.ui_control_settings_signal_size), tr(R.string.ui_control_settings_signal_size_summary), 50, 150,
                    initial.fusionContent.signalScale, "%",
                    value -> edit(current -> current.withFusionContentScale(FusionContentConfig.SIGNAL, value)));
        });
    }

    void showClock() {
        show(tr(R.string.ui_control_settings_clock_weather), content -> {
            section(content, tr(R.string.ui_control_settings_time));
            FusionConfig initial = model.current();
            Switch custom = toggle(content, tr(R.string.ui_control_settings_custom_time), tr(R.string.ui_control_settings_custom_time_summary), model.current().customClock, value -> { });
            EditText pattern = pattern(content, model.current().clockPattern, model.current().customClock, false,
                    tr(R.string.ui_control_settings_time_example), value -> edit(current -> current.withClockPattern(value)));
            custom.setOnCheckedChangeListener((button, value) -> {
                pattern.setEnabled(value);
                edit(current -> current.withCustomClock(value));
            });
            optionalSize(content, tr(R.string.ui_control_settings_custom_font), tr(R.string.ui_control_settings_original_font_summary), tr(R.string.ui_control_settings_status_clock_size),
                    tr(R.string.ui_control_settings_status_clock_size_summary), initial.statusClockSizeSp,
                    16, 8, 48, value -> edit(current -> current.withStatusClockSizeSp(value)));
            section(content, tr(R.string.ui_control_settings_weather));
            toggle(content, tr(R.string.ui_control_settings_show_weather), tr(R.string.ui_control_settings_show_weather_summary), model.current().showWeather,
                    value -> edit(current -> current.withShowWeather(value)));
        });
    }

    void showNotificationClock() {
        show(tr(R.string.ui_control_settings_notification_clock), content -> {
            NotificationClockConfig initial = model.current().notificationClock;
            section(content, tr(R.string.ui_control_settings_date_time));
            Switch enabled = toggle(content, tr(R.string.ui_control_settings_custom_notification_clock), tr(R.string.ui_control_settings_custom_notification_clock_summary), initial.enabled, value -> { });
            EditText format = pattern(content, initial.combinedPattern(), initial.enabled, true,
                    tr(R.string.ui_control_settings_notification_clock_example), value -> notification(current -> current.withPattern(value)));
            enabled.setOnCheckedChangeListener((button, value) -> {
                format.setEnabled(value);
                notification(current -> current.withEnabled(value));
            });
            section(content, tr(R.string.ui_control_settings_appearance));
            toggle(content, tr(R.string.ui_control_settings_center_time), tr(R.string.ui_control_settings_center_time_summary), initial.timeCentered,
                    value -> notification(current -> current.withTimeCentered(value)));
            toggle(content, tr(R.string.ui_control_settings_auto_center_date), tr(R.string.ui_control_settings_auto_center_date_summary), initial.dateCentered,
                    value -> notification(current -> current.withDateCentered(value)));
            toggle(content, tr(R.string.ui_control_settings_hide_notification_settings), tr(R.string.ui_control_settings_hide_notification_settings_summary), initial.hideSettings,
                    value -> notification(current -> current.withHideSettings(value)));
            optionalSize(content, tr(R.string.ui_control_settings_custom_font), tr(R.string.ui_control_settings_keep_original_font_summary), tr(R.string.ui_control_settings_font_size), tr(R.string.ui_control_settings_large_notification_clock),
                    initial.sizeSp, 52, 24, 180, value -> notification(current -> current.withSize(value)));
            optionalSize(content, tr(R.string.ui_control_settings_custom_date_font), tr(R.string.ui_control_settings_custom_date_font_summary), tr(R.string.ui_control_settings_date_font_size),
                    tr(R.string.ui_control_settings_date_font_layout_summary), initial.dateSizeSp, 16, 10, 72,
                    value -> notification(current -> current.withDateSize(value)));
            slider(content, tr(R.string.ui_control_settings_vertical_position), tr(R.string.ui_control_settings_vertical_position_summary), -24, 64, initial.offsetYDp, " dp",
                    value -> notification(current -> current.withOffsetY(value)));
        });
    }

    void showNotificationDate() {
        show(tr(R.string.ui_control_settings_notification_date), content -> {
            NotificationClockConfig initial = model.current().notificationClock;
            section(content, tr(R.string.ui_control_settings_date));
            Switch enabled = toggle(content, tr(R.string.ui_control_settings_custom_notification_date), tr(R.string.ui_control_settings_custom_notification_date_summary), initial.dateEnabled, value -> { });
            EditText format = pattern(content, initial.datePattern, initial.dateEnabled, false,
                    tr(R.string.ui_control_settings_date_example), value -> notification(current -> current.withDatePattern(value)));
            enabled.setOnCheckedChangeListener((button, value) -> {
                format.setEnabled(value);
                notification(current -> current.withDateEnabled(value));
            });
            section(content, tr(R.string.ui_control_settings_appearance));
            toggle(content, tr(R.string.ui_control_settings_center_date), tr(R.string.ui_control_settings_center_date_summary), initial.dateCentered,
                    value -> notification(current -> current.withDateCentered(value)));
            optionalSize(content, tr(R.string.ui_control_settings_custom_font), tr(R.string.ui_control_settings_keep_original_font_summary), tr(R.string.ui_control_settings_font_size), tr(R.string.ui_control_settings_date_size_only),
                    initial.dateSizeSp, 18, 10, 72, value -> notification(current -> current.withDateSize(value)));
            slider(content, tr(R.string.ui_control_settings_vertical_position), tr(R.string.ui_control_settings_vertical_position_summary), -24, 64, initial.dateOffsetYDp, " dp",
                    value -> notification(current -> current.withDateOffsetY(value)));
        });
    }

    void showNotificationList() {
        show(tr(R.string.ui_control_settings_notification_list), content -> slider(content, tr(R.string.ui_control_settings_list_offset), tr(R.string.ui_control_settings_list_offset_summary), 0, 128,
                model.current().notificationClock.listOffsetYDp, " dp",
                value -> notification(current -> current.withListOffsetY(value))));
    }

    void showTelemetry() {
        show(tr(R.string.ui_control_settings_telemetry), content -> {
            int[] titles = {R.string.telemetry_temperatures, R.string.telemetry_power_current, R.string.telemetry_net_speed};
            String[] summaries = {tr(R.string.ui_control_settings_temperature_summary), tr(R.string.ui_control_settings_power_summary), tr(R.string.ui_control_settings_speed_summary)};
            Switch[] options = new Switch[3];
            for (int metric = 0; metric < 3; metric++) {
                final int index = metric;
                TelemetryConfig initial = model.current().telemetry;
                section(content, activity.getString(titles[metric]));
                Switch enabled = toggle(content, tr(R.string.ui_control_settings_enable_telemetry), summaries[metric], initial.enabled(metric), value -> { });
                enabled.setContentDescription(activity.getString(titles[metric]));
                LinearLayout details = new LinearLayout(activity); details.setOrientation(LinearLayout.VERTICAL);
                content.addView(details, wrap());
                choice(details, tr(R.string.ui_control_settings_telemetry_position), positions(), initial.position(metric), value -> telemetry(current -> current.withPosition(index, value)));
                slider(details, tr(R.string.ui_control_settings_font_size), tr(R.string.ui_control_settings_telemetry_size_summary), 5, 14, initial.textSize(metric), " sp", value -> telemetry(current -> current.withGroupTextSize(index, value)));
                choice(details, tr(R.string.ui_control_settings_horizontal_alignment), new String[]{tr(R.string.ui_control_settings_align_side), tr(R.string.ui_control_settings_align_left), tr(R.string.ui_control_settings_align_center), tr(R.string.ui_control_settings_align_right)}, initial.alignment(metric), value -> telemetry(current -> current.withAlignment(index, value)));
                slider(details, tr(R.string.ui_control_settings_fixed_width), tr(R.string.ui_control_settings_fixed_width_summary), 0, 240, initial.fixedWidth(metric), " dp", value -> telemetry(current -> current.withFixedWidth(index, value)));
                slider(details, tr(R.string.ui_control_settings_margin_left), tr(R.string.ui_control_settings_margin_left_summary), 0, 32, initial.leftMargin(metric), " dp", value -> telemetry(current -> current.withLeftMargin(index, value)));
                slider(details, tr(R.string.ui_control_settings_margin_right), tr(R.string.ui_control_settings_margin_right_summary), 0, 32, initial.rightMargin(metric), " dp", value -> telemetry(current -> current.withRightMargin(index, value)));
                slider(details, tr(R.string.ui_control_settings_vertical_offset), tr(R.string.ui_control_settings_vertical_offset_summary), -24, 24, initial.verticalOffset(metric), " dp", value -> telemetry(current -> current.withVerticalOffset(index, value)));
                toggle(details, tr(R.string.ui_control_settings_bold), tr(R.string.ui_control_settings_bold_summary), initial.bold(metric), value -> telemetry(current -> current.withBold(index, value)));
                slider(details, tr(R.string.ui_control_settings_line_spacing), tr(R.string.ui_control_settings_line_spacing_summary), -4, 12, initial.lineSpacing(metric), " dp", value -> telemetry(current -> current.withLineSpacing(index, value)));
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
            section(content, tr(R.string.ui_control_settings_display_mode));
            TelemetryConfig initial = model.current().telemetry;
            options[0] = toggle(content, tr(R.string.ui_control_settings_dual_speed), tr(R.string.ui_control_settings_dual_speed_summary), initial.dualSpeed, value -> telemetry(current -> current.withDualSpeed(value)));
            options[1] = toggle(content, tr(R.string.ui_control_settings_current_ma), tr(R.string.ui_control_settings_current_ma_summary), initial.currentInMilliamps, value -> telemetry(current -> current.withCurrentInMilliamps(value)));
            options[2] = toggle(content, tr(R.string.ui_control_settings_current_positive), tr(R.string.ui_control_settings_current_positive_summary), initial.currentPositive, value -> telemetry(current -> current.withCurrentPositive(value)));
            options[0].setEnabled(initial.enabled(2)); options[1].setEnabled(initial.enabled(1)); options[2].setEnabled(initial.enabled(1));
        });
    }

    void showLayout() {
        show(tr(R.string.ui_control_settings_layout_title), content -> {
            FusionConfig initial = model.current();
            section(content, tr(R.string.ui_control_settings_basic_layout));
            Switch doubleRow = toggle(content, tr(R.string.ui_control_settings_double_row), tr(R.string.ui_control_settings_double_row_summary), initial.doubleRow, value -> { });
            slider(content, tr(R.string.ui_control_settings_status_height), tr(R.string.ui_control_settings_status_height_summary), 32, 96, initial.statusBarHeight, " dp", value -> edit(current -> current.withStatusBarHeight(value)));
            section(content, tr(R.string.ui_control_settings_element_position));
            LinearLayout placements = new LinearLayout(activity); placements.setOrientation(LinearLayout.VERTICAL); content.addView(placements, wrap());
            choice(placements, tr(R.string.ui_control_settings_time), positions(), initial.clockSide * 2 + initial.clockRow, value -> edit(current -> current.withElementPosition(0, value / 2, value % 2)));
            choice(placements, tr(R.string.ui_control_settings_notification_icons), positions(), initial.notificationSide * 2 + initial.notificationRow, value -> edit(current -> current.withElementPosition(1, value / 2, value % 2)));
            choice(placements, tr(R.string.ui_control_settings_system_icons), positions(), initial.systemSide * 2 + initial.systemRow, value -> edit(current -> current.withElementPosition(2, value / 2, value % 2)));
            choice(placements, tr(R.string.ui_control_settings_fusion_title), positions(), initial.fusionSide * 2 + initial.fusionRow, value -> edit(current -> current.withFusionPosition(value / 2, value % 2)));
            section(content, tr(R.string.ui_control_settings_element_size));
            slider(content, tr(R.string.ui_control_settings_system_icon_size), tr(R.string.ui_control_settings_system_icon_size_summary), 50, 150, initial.systemIconScale, "%", value -> edit(current -> current.withSystemIconScale(value)));
            slider(content, tr(R.string.ui_control_settings_notification_icon_size), tr(R.string.ui_control_settings_notification_icon_size_summary), 50, 150, initial.notificationIconScale, "%", value -> edit(current -> current.withNotificationIconScale(value)));
            section(content, tr(R.string.ui_control_settings_spanning_fusion));
            Switch span = toggle(content, tr(R.string.ui_control_settings_span_rows), tr(R.string.ui_control_settings_span_rows_summary), initial.spanRows, value -> { });
            LinearLayout offsets = new LinearLayout(activity); offsets.setOrientation(LinearLayout.VERTICAL); content.addView(offsets, wrap());
            slider(offsets, tr(R.string.ui_control_settings_horizontal_position), tr(R.string.ui_control_settings_horizontal_position_summary), -24, 24, initial.spanOffsetX, " dp", value -> edit(current -> current.withSpanOffsetX(value)));
            slider(offsets, tr(R.string.ui_control_settings_vertical_position), tr(R.string.ui_control_settings_span_vertical_summary), -24, 24, initial.spanOffsetY, " dp", value -> edit(current -> current.withSpanOffsetY(value)));
            Runnable refresh = () -> {
                enable(placements, doubleRow.isChecked()); span.setEnabled(doubleRow.isChecked());
                enable(offsets, doubleRow.isChecked() && span.isChecked());
            };
            doubleRow.setOnCheckedChangeListener((button, checked) -> { edit(current -> current.withDoubleRow(checked)); refresh.run(); });
            span.setOnCheckedChangeListener((button, checked) -> { edit(current -> current.withSpanRows(checked)); refresh.run(); });
            refresh.run();
        });
    }

    private String[] positions() { return new String[]{tr(R.string.ui_control_settings_left_first), tr(R.string.ui_control_settings_left_second), tr(R.string.ui_control_settings_right_first), tr(R.string.ui_control_settings_right_second)}; }

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
        if (activity instanceof androidx.activity.ComponentActivity) {
            return MiuixControls.pattern(content, initial, enabled, multiline, hint, update);
        }
        EditText input = new EditText(activity);
        input.setSingleLine(!multiline); input.setText(initial); input.setTextSize(15);
        input.setTextColor(primary()); input.setHintTextColor(secondary()); input.setHint(hint);
        input.setContentDescription(tr(R.string.ui_control_settings_time_format));
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
        if (activity instanceof androidx.activity.ComponentActivity) {
            return MiuixControls.toggle(content, title, summary, value, update);
        }
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
        if (activity instanceof androidx.activity.ComponentActivity) {
            return MiuixControls.slider(content, title, summary, minimum, maximum, value, suffix, update);
        }
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
        if (activity instanceof androidx.activity.ComponentActivity) {
            content.addView(MiuixControls.section(activity, title), wrap());
            return;
        }
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

    private String tr(int resource, Object... args) {
        return ModuleStrings.get(activity, resource, args);
    }
}

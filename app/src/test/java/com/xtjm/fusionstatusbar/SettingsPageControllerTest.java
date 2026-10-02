package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35})
public class SettingsPageControllerTest {
    private ActivityController<Activity> activity;
    private SettingsDialogController dialogs;
    private SettingsPageController pages;
    private FusionConfig config;
    private int modelUpdates;

    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup();
        config = FusionConfig.defaults();
        dialogs = new SettingsDialogController(activity.get());
        pages = new SettingsPageController(activity.get(), new SettingsPageController.Model() {
            public FusionConfig current() { return config; }
            public void update(FusionConfig next) { config = next; modelUpdates++; }
        }, dialogs);
    }
    @After public void close() { dialogs.close(); activity.pause().stop().destroy(); }

    @Test public void clockFormatRejectsInvalidTextAndPreservesOtherSettings() {
        pages.showClock();
        Switch custom = (Switch) find("自定义时间格式");
        if (!custom.isChecked()) custom.performClick();
        EditText pattern = (EditText) find("时间格式");
        String baseline = config.clockPattern;
        pattern.setText("invalid ] 'format");
        assertEquals(baseline, config.clockPattern);
        config = config.withIconScale(125);
        pattern.setText("HH:mm:ss");
        assertEquals("HH:mm:ss", config.clockPattern);
        assertEquals(125, config.iconScale);
    }

    @Test public void notificationFontToggleRestoresSelectedSizeAfterDisabling() {
        pages.showNotificationClock();
        Switch custom = (Switch) find("自定义字号");
        if (!custom.isChecked()) custom.performClick();
        SeekBar size = (SeekBar) find("字号");
        Shadows.shadowOf(size).getOnSeekBarChangeListener().onProgressChanged(size, 72 - 24, true);
        assertEquals(72, config.notificationClock.sizeSp);
        custom.performClick(); assertEquals(0, config.notificationClock.sizeSp); assertFalse(size.isEnabled());
        custom.performClick(); assertEquals(72, config.notificationClock.sizeSp); assertTrue(size.isEnabled());
    }

    @Test public void notificationMultilineFormatAndDateSizeRemainIndependent() {
        pages.showNotificationClock();
        Switch enabled = (Switch) find("自定义通知中心时间");
        if (!enabled.isChecked()) enabled.performClick();
        ((EditText) find("时间格式")).setText("HH:mm\nMM/dd E");
        assertEquals("HH:mm\nMM/dd E", config.notificationClock.combinedPattern());
        Switch dateSize = (Switch) find("自定义日期字号");
        if (!dateSize.isChecked()) dateSize.performClick();
        assertEquals(16, config.notificationClock.dateSizeSp);
        assertEquals(0, config.notificationClock.sizeSp);
    }

    @Test public void closingControllerDismissesAllOwnedSettingsDialogs() {
        pages.showFusion(); Dialog first = ShadowDialog.getLatestDialog();
        pages.showNotificationList(); Dialog second = ShadowDialog.getLatestDialog();
        assertTrue(first.isShowing()); assertTrue(second.isShowing());
        dialogs.close(); assertFalse(first.isShowing()); assertFalse(second.isShowing());
    }

    @Test public void settingsDialogsUseTheDedicatedBottomSheetWindowTransition() {
        pages.showTelemetry();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertEquals(R.style.SettingsSheetWindowAnimation,
                dialog.getWindow().getAttributes().windowAnimations);
    }

    @Test public void layoutChoicesExpandInsideTheSheetAndOnlyChangedSelectionWritesBack() {
        config = config.withDoubleRow(true);
        pages.showLayout();
        Dialog sheet = ShadowDialog.getLatestDialog();
        View clockChoice = find("时间");
        assertNotNull(clockChoice);
        assertTrue("The selector exposes its setting label", "时间".contentEquals(clockChoice.getContentDescription()));
        assertEquals("Settings pages no longer use platform Spinner popups", 0,
                countSpinners(sheet.getWindow().getDecorView()));
        assertEquals(0, modelUpdates);

        clockChoice.performClick();
        View secondRow = findChoiceOption(sheet.getWindow().getDecorView(), "左侧 · 第二排");
        assertNotNull(secondRow);
        assertTrue(secondRow.isShown());
        assertTrue(clockChoice.getStateDescription().toString().contains("已展开"));
        assertSame("Opening a choice must not create a nested window", sheet, ShadowDialog.getLatestDialog());
        clockChoice.performClick();
        assertFalse("Tapping the current value cancels the open list", secondRow.isShown());
        assertEquals(0, modelUpdates);
        clockChoice.performClick();
        secondRow.performClick();

        assertEquals(1, modelUpdates);
        assertEquals(0, config.clockSide);
        assertEquals(1, config.clockRow);
        assertTrue(hasVisibleText(clockChoice, "左侧 · 第二排"));
        assertFalse(secondRow.isShown());

        clockChoice.performClick();
        View selectedAgain = findChoiceOption(sheet.getWindow().getDecorView(), "左侧 · 第二排");
        assertNotNull(selectedAgain);
        assertTrue(selectedAgain.isSelected());
        assertEquals("已选择", selectedAgain.getStateDescription().toString());
        selectedAgain.performClick();
        assertEquals("Selecting the current value must not write the model again", 1, modelUpdates);

        clockChoice.performClick();
        assertEquals(1, modelUpdates);
        sheet.cancel();
        assertFalse(sheet.isShowing());
    }

    @Test public void telemetrySheetUsesBoundedAdaptiveContentAndMiuixStyleGeometry() {
        pages.showTelemetry();
        Dialog dialog = ShadowDialog.getLatestDialog();
        android.util.DisplayMetrics metrics = activity.get().getResources().getDisplayMetrics();
        int expectedWidth = Math.min(metrics.widthPixels - Math.round(24 * metrics.density),
                Math.round(420 * metrics.density));
        assertEquals(expectedWidth, dialog.getWindow().getAttributes().width);
        assertEquals(android.view.WindowManager.LayoutParams.WRAP_CONTENT, dialog.getWindow().getAttributes().height);
        ScrollView scroll = findScroll(dialog.getWindow().getDecorView());
        assertNotNull(scroll);
        int maxSheetHeight = Math.round(metrics.heightPixels * .88f);
        View decor = dialog.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(expectedWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.AT_MOST));
        decor.layout(0, 0, expectedWidth, decor.getMeasuredHeight());
        assertTrue(decor.getMeasuredHeight() <= maxSheetHeight);
        assertTrue(scroll.getHeight() > 0 && scroll.getHeight() < maxSheetHeight);
        assertTrue(scroll.getChildAt(0).getHeight() > scroll.getHeight());
    }

    @Test public void layoutChoiceAccessibilityReportsTitleValueAndExpandedSelectionState() {
        config = config.withDoubleRow(true);
        pages.showLayout();
        View choice = find("时间");
        assertNotNull(choice);
        assertEquals("时间", choice.getContentDescription());
        assertEquals("当前值：左侧 · 第一排", choice.getStateDescription());

        choice.performClick();
        assertEquals("当前值：左侧 · 第一排，已展开", choice.getStateDescription());
        View selected = findChoiceOption(
                ShadowDialog.getLatestDialog().getWindow().getDecorView(), "左侧 · 第一排");
        View unselected = findChoiceOption(
                ShadowDialog.getLatestDialog().getWindow().getDecorView(), "左侧 · 第二排");
        assertNotNull(selected);
        assertNotNull(unselected);
        assertTrue(selected.isSelected());
        assertEquals("已选择", selected.getStateDescription());
        assertFalse(unselected.isSelected());
        assertNull(unselected.getStateDescription());

        choice.performClick();
        assertEquals("当前值：左侧 · 第一排，已收起", choice.getStateDescription());
        assertFalse(selected.isShown());
    }

    @Test public void disablingAnExpandedLayoutChoiceCollapsesItsOptionsImmediately() {
        config = config.withDoubleRow(true);
        pages.showLayout();
        View doubleRow = find("启用双排状态栏");
        View choice = find("时间");
        choice.performClick();
        View option = findChoiceOption(
                ShadowDialog.getLatestDialog().getWindow().getDecorView(), "左侧 · 第二排");
        assertNotNull(option);
        assertTrue(option.isShown());

        doubleRow.performClick();

        assertFalse(choice.isEnabled());
        assertFalse(option.isShown());
        assertEquals("当前值：左侧 · 第一排，已收起", choice.getStateDescription());
    }

    @Test public void doubleRowToggleUpdatesEveryDependentControl() {
        config = config.withDoubleRow(false).withSpanRows(true);
        pages.showLayout();
        Switch doubleRow = (Switch) find("启用双排状态栏");
        Switch span = (Switch) find("融合图标跨两排");
        SeekBar offset = (SeekBar) find("左右位置");
        View clockChoice = find("时间");
        View firstOption = findChoiceOption(
                ShadowDialog.getLatestDialog().getWindow().getDecorView(), "左侧 · 第一排");
        assertFalse(span.isEnabled()); assertFalse(offset.isEnabled());
        assertFalse(clockChoice.isEnabled());
        assertNotNull(firstOption);
        assertFalse(firstOption.isShown());
        clockChoice.performClick();
        assertFalse("Disabled choices must stay collapsed", firstOption.isShown());
        doubleRow.performClick();
        assertTrue(span.isEnabled()); assertTrue(offset.isEnabled());
        assertTrue(clockChoice.isEnabled());
        clockChoice.performClick();
        assertTrue(firstOption.isShown());
        span.performClick(); assertFalse(offset.isEnabled());
        assertTrue(config.doubleRow); assertFalse(config.spanRows);
        doubleRow.performClick();
        assertFalse(clockChoice.isEnabled());
        assertFalse("Disabling the layout collapses an open choice", firstOption.isShown());
        assertFalse(config.doubleRow);
    }

    @Test public void telemetryDependentOptionsFollowTheirOwnMetric() {
        config = config.withTelemetry(config.telemetry.withEnabled(1, false).withEnabled(2, false));
        pages.showTelemetry();
        Switch network = (Switch) find(activity.get().getString(R.string.telemetry_net_speed));
        Switch current = (Switch) find(activity.get().getString(R.string.telemetry_power_current));
        Switch dual = (Switch) find("网速上下双排");
        Switch milliamps = (Switch) find("电流使用 mA");
        assertFalse(dual.isEnabled()); assertFalse(milliamps.isEnabled());
        network.performClick(); assertTrue(dual.isEnabled()); assertFalse(milliamps.isEnabled());
        current.performClick(); assertTrue(milliamps.isEnabled());
        assertTrue(config.telemetry.enabled(1)); assertTrue(config.telemetry.enabled(2));
    }

    private View find(String description) {
        return find(ShadowDialog.getLatestDialog().getWindow().getDecorView(), description);
    }
    private View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = find(((ViewGroup) view).getChildAt(i), description); if (found != null) return found;
        }
        return null;
    }

    private View findChoiceOption(View view, String label) {
        CharSequence current = view.getContentDescription();
        if (view instanceof LinearLayout && label.contentEquals(current == null ? "" : current)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findChoiceOption(((ViewGroup) view).getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }
    private int countSpinners(View view) {
        int count = view instanceof Spinner ? 1 : 0;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            count += countSpinners(((ViewGroup) view).getChildAt(i));
        }
        return count;
    }

    private boolean hasVisibleText(View view, String expected) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof android.widget.TextView
                && expected.contentEquals(((android.widget.TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            if (hasVisibleText(((ViewGroup) view).getChildAt(i), expected)) return true;
        }
        return false;
    }

    private ScrollView findScroll(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            ScrollView found = findScroll(((ViewGroup) view).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
}

package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.CheckBox;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterGroupEditorTest {
    @Test public void memberPickerCommitsOnlyAfterSelection() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterLayoutPlan.Item[] current = {emptyGroup()};
        Picker picker = new Picker();
        ControlCenterGroupEditor editor = editor(activity, current, List.of("bt"), picker);

        button(editor, "添加成员").performClick();
        assertEquals(List.of("bt"), picker.specs);
        assertTrue(current[0].group.members.isEmpty());
        picker.selected.accept(0);
        assertEquals(1, current[0].group.members.size());
        assertEquals("bt", current[0].group.members.get(0).spec);
    }

    @Test public void memberPickerRechecksLockAndAvailabilityAfterOpening() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterLayoutPlan.Item[] current = {emptyGroup()};
        ArrayList<String> candidates = new ArrayList<>(List.of("bt"));
        Picker picker = new Picker();
        ControlCenterGroupEditor editor = editor(activity, current, candidates, picker);

        button(editor, "添加成员").performClick();
        current[0] = current[0].withLocked(true);
        picker.selected.accept(0);
        assertTrue(current[0].group.members.isEmpty());

        current[0] = current[0].withLocked(false);
        candidates.set(0, "wifi");
        picker.selected.accept(0);
        assertTrue(current[0].group.members.isEmpty());
    }

    @Test public void groupEditorUsesGlobalAppearanceAndPreservesLegacyFieldsDuringEdits() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        for (var surface : ControlCenterGroupData.Surface.values()) for (boolean border : new boolean[] {false, true}) {
            var data = ControlCenterGroupData.empty().add("wifi").withAppearance(surface, border);
            ControlCenterLayoutPlan.Item[] current = {emptyGroup().withGroup(data)};
            ControlCenterGroupEditor editor = editor(activity, current, List.of("bt"), new Picker());
            assertNull(text(editor, "容器背景"));
            assertNull(description(editor, "容器背景"));
            assertNull(text(editor, "显示描边"));
            var arrangement = (SettingsChoiceSelector) description(editor, "内部排列");
            assertNotNull(arrangement);
            assertNotNull(description(editor, "编辑 wifi"));
            arrangement.optionView(ControlCenterGroupData.Arrangement.HORIZONTAL.ordinal()).performClick();
            assertEquals(ControlCenterGroupData.Arrangement.HORIZONTAL, current[0].group.arrangement);
            assertEquals(surface, current[0].group.surface);
            assertEquals(border, current[0].group.border);
            assertEquals(100, current[0].group.members.get(0).iconScale);
        }
    }

    @Test public void memberIconSizeSliderAndNumericFieldStayInSyncAndPreserveArrangement() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var data = ControlCenterGroupData.empty().add("wifi").add("bt");
        var before = data.members.get(0);
        ControlCenterLayoutPlan.Item[] current = {emptyGroup().withGroup(data)};
        ControlCenterGroupEditor editor = editor(activity, current, List.of(), new Picker());
        description(editor, "编辑 wifi").performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        View decor = dialog.getWindow().getDecorView();
        EditText input = (EditText) description(decor, "图标与圆圈大小 (%)");
        SeekBar slider = (SeekBar) description(decor, "图标与圆圈大小滑杆");
        assertEquals(25, slider.getMin());
        assertEquals(200, slider.getMax());
        assertEquals("100", input.getText().toString());
        input.setText("65");
        assertEquals(65, slider.getProgress());
        slider.setProgress(135);
        assertEquals("135", input.getText().toString());
        ((CheckBox) text(decor, "显示名称与状态")).setChecked(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
        assertEquals(data.arrangement, current[0].group.arrangement);
        var after = current[0].group.members.get(0);
        assertEquals(135, after.iconScale);
        assertFalse(after.showLabel);
        assertEquals(before.x, after.x); assertEquals(before.y, after.y);
        assertEquals(before.width, after.width); assertEquals(before.height, after.height);
        assertEquals(data.members.get(1), current[0].group.members.get(1));
    }

    @Test public void invalidMemberIconSizeKeepsDialogOpenWithoutCommitting() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        var data = ControlCenterGroupData.empty().add("wifi");
        ControlCenterLayoutPlan.Item[] current = {emptyGroup().withGroup(data)};
        ControlCenterGroupEditor editor = editor(activity, current, List.of(), new Picker());
        description(editor, "编辑 wifi").performClick();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        EditText input = (EditText) description(dialog.getWindow().getDecorView(), "图标与圆圈大小 (%)");
        for (String value : List.of("24", "201", "")) {
            input.setText(value);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(dialog.isShowing());
            assertNotNull(input.getError());
            assertEquals(data, current[0].group);
        }
        input.setText("25");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
        assertEquals(25, current[0].group.members.get(0).iconScale);
    }

    private static ControlCenterGroupEditor editor(Activity activity, ControlCenterLayoutPlan.Item[] current,
            List<String> candidates, Picker picker) {
        return new ControlCenterGroupEditor(activity, () -> current[0], () -> new ArrayList<>(candidates),
                data -> current[0] = current[0].withGroup(data), spec -> spec, () -> {}, Color.BLACK, picker);
    }

    private static ControlCenterLayoutPlan.Item emptyGroup() {
        return ControlCenterLayoutPlan.Item.group("group:test", ControlCenterGroupData.empty(), 0, 0, 2, 2);
    }

    private static View button(ViewGroup parent, String text) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView label && text.contentEquals(label.getText())) return child;
        }
        throw new AssertionError("Missing button: " + text);
    }

    private static View description(View view, String value) {
        if (value.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = description(group.getChildAt(i), value);
            if (found != null) return found;
        }
        return null;
    }

    private static View text(View view, String value) {
        if (view instanceof TextView label && value.contentEquals(label.getText())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = text(group.getChildAt(i), value);
            if (found != null) return found;
        }
        return null;
    }

    private static final class Picker implements ControlCenterGroupEditor.ChoicePicker {
        List<String> specs;
        IntConsumer selected;
        @Override public void show(List<String> values, IntConsumer callback) {
            specs = values; selected = callback;
        }
    }
}

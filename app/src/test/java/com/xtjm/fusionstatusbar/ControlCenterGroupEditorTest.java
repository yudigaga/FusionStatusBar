package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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

    private static final class Picker implements ControlCenterGroupEditor.ChoicePicker {
        List<String> specs;
        IntConsumer selected;
        @Override public void show(List<String> values, IntConsumer callback) {
            specs = values; selected = callback;
        }
    }
}

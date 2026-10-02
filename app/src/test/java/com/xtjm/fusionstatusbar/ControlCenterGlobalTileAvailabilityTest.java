package com.xtjm.fusionstatusbar;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h800dp-mdpi")
public class ControlCenterGlobalTileAvailabilityTest {
    private static final String APPLICATION_SPEC = "custom(com.example.global/.apptile)";
    private static final String APPLICATION_LABEL = "测试应用开关 · 测试应用";
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Test public void applicationAliasesHaveOneRecoverableGlobalSwitch() throws Exception {
        String full = "custom(com.example.global/com.example.global.apptile)";
        launch(full);
        setField("controlCenterAppTiles", List.of(new ControlCenterAppTiles.Entry(
                "custom(com.example.global/.AppTile)", "测试应用开关", "测试应用", null)));
        strings("controlCenterCapturedSpecs").add(full);
        Dialog panel = openPanel();
        assertEquals(1, countLabels(panel.getWindow().getDecorView(), APPLICATION_LABEL));
        Switch toggle = requiredSwitch(panel, APPLICATION_LABEL);
        assertFalse(toggle.isChecked());
        assertTrue(activity.editorSession().draft().isHidden(full));
        assertTrue(activity.editorSession().draft().isHidden(APPLICATION_SPEC));
        toggle.performClick();
        assertFalse(activity.editorSession().draft().isHidden(full));
        assertFalse(activity.editorSession().draft().isHidden(APPLICATION_SPEC));
        assertEquals(1, candidates().stream().filter(spec -> spec.equals(full) || spec.equals(APPLICATION_SPEC)).count());
    }

    private static int countLabels(View view, String label) {
        int count = view instanceof TextView text && label.contentEquals(text.getText()) ? 1 : 0;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            count += countLabels(group.getChildAt(i), label);
        }
        return count;
    }

    @Before public void isolateApplicationRepository() throws Exception {
        Field repository = FusionConfigRepository.class.getDeclaredField("application");
        repository.setAccessible(true);
        repository.set(null, null);
    }

    @After public void close() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void discoveredApplicationCanBeDisabledAndRestoredWithoutPublishingOrChangingEitherLayout() throws Exception {
        launch("");
        setField("controlCenterAppTiles", List.of(new ControlCenterAppTiles.Entry(
                "custom(com.example.global/.AppTile)", "测试应用开关", "测试应用", null)));
        ControlCenterConfig originalDraft = activity.editorSession().draft();
        ControlCenterConfig published = FusionConfigStore.read(activity).controlCenter;
        assertTrue(candidates().contains(APPLICATION_SPEC));

        Dialog first = openPanel();
        Switch available = requiredSwitch(first, APPLICATION_LABEL);
        assertTrue(available.isChecked());
        available.performClick();
        assertTrue(activity.editorSession().draft().isHidden(APPLICATION_SPEC));
        assertFalse(candidates().contains(APPLICATION_SPEC));
        assertConfigEquals(originalDraft, activity.editorSession().draft(), true);
        assertConfigEquals(published, FusionConfigStore.read(activity).controlCenter, false);
        first.dismiss();

        Dialog reopened = openPanel();
        Switch disabled = requiredSwitch(reopened, APPLICATION_LABEL);
        assertFalse("A disabled application must remain reachable in the global panel", disabled.isChecked());
        disabled.performClick();
        assertFalse(activity.editorSession().draft().isHidden(APPLICATION_SPEC));
        assertTrue("Re-enabling an application returns it to add candidates", candidates().contains(APPLICATION_SPEC));
        assertConfigEquals(originalDraft, activity.editorSession().draft(), false);
        assertConfigEquals(published, FusionConfigStore.read(activity).controlCenter, false);
    }

    @Test public void undiscoveredLegacyHiddenApplicationRetainsARecoverySwitch() throws Exception {
        String missing = "custom(com.example.uninstalled/.oldtile)";
        launch(missing);
        setField("controlCenterAppTiles", List.of());
        ControlCenterConfig originalDraft = activity.editorSession().draft();
        ControlCenterConfig published = FusionConfigStore.read(activity).controlCenter;
        assertFalse(candidates().contains(missing));

        Dialog panel = openPanel();
        Switch recovery = requiredSwitch(panel, missing);
        assertFalse(recovery.isChecked());
        recovery.performClick();
        assertFalse(activity.editorSession().draft().isHidden(missing));
        assertConfigEquals(originalDraft, activity.editorSession().draft(), true);
        assertConfigEquals(published, FusionConfigStore.read(activity).controlCenter, false);
        assertTrue("Recovery remains a draft until explicitly published", published.isHidden(missing));
    }

    @Test public void capturedTilesAreManageableWhileLargeComponentsStayOutsideTheGlobalTilePanel() throws Exception {
        launch("");
        String captured = "oem_sensor_toggle";
        strings("controlCenterCapturedSpecs").add(captured);
        String[] components = {ControlCenterComponentSpec.MEDIA, ControlCenterComponentSpec.BRIGHTNESS,
                ControlCenterComponentSpec.VOLUME, ControlCenterComponentSpec.DEVICE_CENTER};
        strings("controlCenterNativeCatalog").addAll(List.of(components));
        ControlCenterConfig originalDraft = activity.editorSession().draft();
        ControlCenterConfig published = FusionConfigStore.read(activity).controlCenter;

        Dialog panel = openPanel();
        Switch toggle = requiredSwitch(panel, captured);
        assertTrue(toggle.isChecked());
        for (String component : components) {
            assertNull("Large components are controlled through the canvas: " + component,
                    findSwitch(panel.getWindow().getDecorView(), ControlCenterComponentSpec.label(component)));
        }
        toggle.performClick();
        assertTrue(activity.editorSession().draft().isHidden(captured));
        assertFalse(candidates().contains(captured));
        assertConfigEquals(originalDraft, activity.editorSession().draft(), true);
        assertConfigEquals(published, FusionConfigStore.read(activity).controlCenter, false);
    }

    private void launch(String hidden) throws Exception {
        ControlCenterLayoutPlan plan = ControlCenterLayoutPlan.of(
                new ControlCenterLayoutPlan.Mode(5, List.of(
                        ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1),
                        ControlCenterLayoutPlan.Item.tile(APPLICATION_SPEC, 2, 0, 1, 1))),
                new ControlCenterLayoutPlan.Mode(3, List.of(
                        ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1),
                        ControlCenterLayoutPlan.Item.tile(APPLICATION_SPEC, 1, 0, 1, 2)))).forPublication(false);
        ControlCenterConfig config = ControlCenterConfig.defaults().withEnabled(true)
                .withHidden(hidden).withLayoutPlan(plan.encode());
        FusionConfigStore.write(RuntimeEnvironment.getApplication(), FusionConfig.defaults().withControlCenter(config));
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while ((!activity.settingsLoaded() || (boolean) field("controlCenterAppTilesLoading"))
                && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(5);
        }
        ShadowLooper.idleMainLooper();
        assertTrue(activity.settingsLoaded());
        assertFalse((boolean) field("controlCenterAppTilesLoading"));
        assertEquals(plan.encode(), activity.editorSession().draft().layoutPlan);
    }

    private Dialog openPanel() {
        activity.showControlCenterTilesDialog();
        ShadowLooper.idleMainLooper();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }

    private static Switch requiredSwitch(Dialog dialog, String title) {
        Switch toggle = findSwitch(dialog.getWindow().getDecorView(), title);
        assertNotNull("Missing global switch for " + title, toggle);
        return toggle;
    }

    private static Switch findSwitch(View view, String title) {
        if (view instanceof LinearLayout row && row.getChildCount() == 2
                && row.getChildAt(0) instanceof LinearLayout texts
                && texts.getChildCount() > 0 && texts.getChildAt(0) instanceof TextView label
                && title.contentEquals(label.getText()) && row.getChildAt(1) instanceof Switch toggle) {
            return toggle;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                Switch match = findSwitch(group.getChildAt(i), title);
                if (match != null) return match;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> candidates() throws Exception {
        Method method = MainActivity.class.getDeclaredMethod("availableControlGridSpecs");
        method.setAccessible(true);
        return (List<String>) method.invoke(activity);
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(String name) throws Exception { return (List<String>) field(name); }

    private Object field(String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }

    private static void assertConfigEquals(ControlCenterConfig expected, ControlCenterConfig actual, boolean allowHiddenChange) {
        Bundle before = new Bundle(), after = new Bundle();
        expected.writeTo(before);
        actual.writeTo(after);
        assertEquals(before.keySet(), after.keySet());
        for (String key : before.keySet()) {
            if (allowHiddenChange && ControlCenterConfig.KEY_HIDDEN.equals(key)) continue;
            assertEquals(key, before.get(key), after.get(key));
        }
    }
}

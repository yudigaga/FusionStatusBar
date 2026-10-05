package com.xtjm.fusionstatusbar;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
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
public class SystemSurfaceSettingsUiTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before public void launch() throws Exception {
        Field repository = FusionConfigRepository.class.getDeclaredField("application");
        repository.setAccessible(true);
        repository.set(null, null);
        FusionConfigStore.write(RuntimeEnvironment.getApplication(), FusionConfig.defaults()
                .withControlCenter(ControlCenterConfig.defaults().withLayoutPlan(
                        ControlCenterLayoutPlan.blank(4).forPublication(false).encode())));
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!activity.settingsLoaded() && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(5);
        }
        ShadowLooper.idleMainLooper();
        assertTrue(activity.settingsLoaded());
    }

    @After public void close() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void sharedAppearanceControlsRemainDraftUntilExplicitPush() throws Exception {
        activity.showControlCenterSettingsDialog();
        Dialog dialog = ShadowDialog.getLatestDialog();
        View root = dialog.getWindow().getDecorView();
        for (String title : new String[] {"接管电源菜单", "接管音量菜单", "接管通知中心通知", "接管悬浮通知"}) {
            Switch toggle = (Switch) findDescription(root, title);
            assertNotNull(toggle);
            assertTrue(toggle.isChecked());
            toggle.performClick();
        }
        Switch extended = (Switch) findDescription(root, "三条电源菜单");
        assertFalse(extended.isChecked());
        extended.performClick();
        SettingsChoiceSelector length = (SettingsChoiceSelector) findDescription(root, "音量条长度");
        assertNotNull(length);
        length.performClick();
        length.optionView(1).performClick();
        ShadowLooper.idleMainLooper();

        SystemSurfaceConfig draft = activity.editorSession().draft().systemSurfaces;
        assertFalse(draft.powerMenu);
        assertFalse(draft.volumeDialog);
        assertFalse(draft.notifications);
        assertFalse(draft.headsUp);
        assertTrue(draft.extendedPowerMenu);
        assertTrue(draft.compactVolume);
        assertEquals(SystemSurfaceConfig.defaults(), FusionConfigStore.read(activity)
                .controlCenter.systemSurfaces);
        assertTrue(activity.editorSession().undo());
        assertFalse(activity.editorSession().draft().systemSurfaces.compactVolume);
        assertTrue(activity.editorSession().redo());
        dialog.dismiss();

        ((Button) field("controlCenterPushButton")).performClick();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (activity.savingControlCenter() && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(5);
        }
        ShadowLooper.idleMainLooper();
        assertFalse(activity.savingControlCenter());
        assertEquals(draft, FusionConfigStore.read(activity).controlCenter.systemSurfaces);
    }

    private Object field(String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null
                ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }
}

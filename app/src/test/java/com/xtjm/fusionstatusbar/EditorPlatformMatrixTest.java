package com.xtjm.fusionstatusbar;

import android.os.Bundle;
import android.os.Parcel;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35}, qualifiers = "w320dp-h640dp-mdpi")
public class EditorPlatformMatrixTest {
    private ActivityController<MainActivity> controller;

    @Before public void setup() throws Exception {
        Field field = FusionConfigRepository.class.getDeclaredField("application");
        field.setAccessible(true);
        field.set(null, null);
        RuntimeEnvironment.setFontScale(1.5f);
        FusionConfigStore.WriteResult initial = FusionConfigStore.write(RuntimeEnvironment.getApplication(),
                FusionConfig.defaults().withControlCenter(ControlCenterConfig.defaults()
                        .withLayoutPlan(ControlCenterLayoutPlan.blank(4).forPublication(false).encode())));
        assertTrue(initial.success);
        launch(null);
    }

    @After public void close() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void rapidEditsStayPrivateThroughRecreationAndPublishOnce() throws Exception {
        MainActivity activity = controller.get();
        long originalRevision = FusionConfigStore.read(activity).revision;
        Switch enabled = controlSwitch(activity);
        for (int i = 0; i < 51; i++) enabled.performClick();
        assertTrue(activity.editorSession().draft().enabled);
        assertTrue(activity.editorSession().undoCount() <= ControlCenterEditorSession.MAX_HISTORY);
        assertFalse(FusionConfigStore.read(activity).controlCenter.enabled);
        assertEquals(originalRevision, FusionConfigStore.read(activity).revision);
        View compact = text(activity.getWindow().getDecorView(), "紧凑布局");
        assertNotNull(compact);
        compact.performClick();
        Bundle state = new Bundle();
        controller.saveInstanceState(state).pause().stop().destroy();
        controller = null;
        Parcel parcel = Parcel.obtain();
        try { state.writeToParcel(parcel, 0); assertTrue(parcel.dataSize() < 64 * 1024); }
        finally { parcel.recycle(); }
        launch(state);
        MainActivity restored = controller.get();
        assertTrue(restored.editorSession().compact());
        assertTrue(restored.editorSession().draft().enabled);
        assertFalse(FusionConfigStore.read(restored).controlCenter.enabled);
        View push = description(restored.getWindow().getDecorView(), "推送控制中心修改到手机");
        assertNotNull(push);
        assertTrue(push.isEnabled());
        push.performClick();
        await(() -> !restored.savingControlCenter());
        FusionConfig published = FusionConfigStore.read(restored);
        assertEquals(originalRevision + 1, published.revision);
        assertTrue(published.controlCenter.enabled);
        assertEquals(ControlCenterLayoutPlan.RuntimeLayout.COMPACT,
                ControlCenterLayoutPlan.decode(published.controlCenter.layoutPlan).runtimeLayout);
        assertFalse(restored.editorSession().isDirty());
    }

    @Test public void navigationAndUndoLeavePublishedSettingsUntouched() throws Exception {
        MainActivity activity = controller.get();
        long revision = FusionConfigStore.read(activity).revision;
        controlSwitch(activity).performClick();
        for (String page : new String[] {"设置", "状态栏", "通知栏", "概述", "控制中心"}) {
            View entry = description(activity.getWindow().getDecorView(), page);
            assertNotNull(entry);
            entry.performClick();
        }
        View undo = text(activity.getWindow().getDecorView(), activity.getString(R.string.editor_undo));
        assertNotNull(undo);
        undo.performClick();
        assertFalse(activity.editorSession().isDirty());
        assertTrue(activity.editorSession().canRedo());
        assertEquals(revision, FusionConfigStore.read(activity).revision);
    }

    private void launch(Bundle state) throws Exception {
        controller = Robolectric.buildActivity(MainActivity.class);
        controller.create(state).start().resume().visible();
        await(() -> controller.get().settingsLoaded());
        description(controller.get().getWindow().getDecorView(), "控制中心").performClick();
    }

    private Switch controlSwitch(MainActivity activity) {
        TextView label = (TextView) text(activity.getWindow().getDecorView(), "自定义控制中心");
        assertNotNull(label);
        ViewGroup row = (ViewGroup) label.getParent().getParent();
        for (int i = 0; i < row.getChildCount(); i++) if (row.getChildAt(i) instanceof Switch) return (Switch) row.getChildAt(i);
        throw new AssertionError("Control-center switch missing");
    }

    private static void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            ShadowLooper.idleMainLooper();
            if (ready.getAsBoolean()) return;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        fail("Asynchronous operation did not finish");
    }

    private static View description(View view, String value) {
        if (value.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = description(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View text(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = text(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
    }
}

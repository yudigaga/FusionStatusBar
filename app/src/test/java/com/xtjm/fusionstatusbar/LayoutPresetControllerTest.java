package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Looper;
import java.io.File;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LayoutPresetControllerTest {
    @Test public void allPresetListsUseInjectedChoicesAndCancelLeavesSavedDataUntouched() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        new LayoutPresetStore(activity).save("日常布局", ControlCenterConfig.defaults(), false);
        File file = new File(activity.getFilesDir(), "control_center_presets_v1.json");
        byte[] before = Files.readAllBytes(file.toPath());
        Host host = new Host();
        Choices choices = new Choices(activity);
        try (LayoutPresetController controller = new LayoutPresetController(activity, host, choices)) {
            controller.show();
            assertEquals(activity.getString(R.string.preset_title), choices.title);
            assertArrayEquals(new String[] {activity.getString(R.string.preset_save_current),
                    activity.getString(R.string.preset_manage)}, choices.labels);
            choices.select(1);
            await(() -> choices.shown == 2);
            assertEquals(activity.getString(R.string.preset_manage), choices.title);
            assertArrayEquals(new String[] {"日常布局"}, choices.labels);
            choices.select(0);
            assertEquals(3, choices.shown);
            assertEquals("日常布局", choices.title);
            assertEquals(4, choices.labels.length);
            choices.dialog.cancel();
            assertFalse(choices.dialog.isShowing());
            assertArrayEquals(before, Files.readAllBytes(file.toPath()));
            assertEquals(0, host.loads);
        }
    }

    @Test public void closeDismissesInjectedDialogAndPreventsReopening() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Choices choices = new Choices(activity);
        LayoutPresetController controller = new LayoutPresetController(activity, new Host(), choices);
        controller.show();
        assertTrue(choices.dialog.isShowing());
        controller.close();
        assertFalse(choices.dialog.isShowing());
        controller.show();
        assertEquals(1, choices.shown);
    }

    @Test public void deleteStillRequiresConfirmationAndCancelPreservesPreset() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        LayoutPresetStore store = new LayoutPresetStore(activity);
        store.save("日常布局", ControlCenterConfig.defaults(), false);
        Choices choices = new Choices(activity);
        try (LayoutPresetController controller = new LayoutPresetController(activity, new Host(), choices)) {
            controller.show();
            choices.select(1);
            await(() -> choices.shown == 2);
            choices.select(0);
            choices.select(3);
            AlertDialog confirmation = ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(confirmation);
            assertTrue(confirmation.isShowing());
            assertEquals(1, store.list().size());
            confirmation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse(confirmation.isShowing());
            assertEquals("日常布局", store.list().get(0).name);
        }
    }

    private static void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(5);
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue("Preset list was not delivered", ready.getAsBoolean());
    }

    private static final class Host implements LayoutPresetController.Host {
        int loads;
        @Override public ControlCenterConfig draft() { return ControlCenterConfig.defaults(); }
        @Override public boolean compact() { return false; }
        @Override public boolean canEdit() { return true; }
        @Override public void loadPreset(ControlCenterConfig config, boolean compact) { loads++; }
    }

    private static final class Choices implements LayoutPresetController.Choices {
        final Activity activity;
        String title;
        String[] labels;
        IntConsumer selected;
        Dialog dialog;
        int shown;
        Choices(Activity activity) { this.activity = activity; }
        @Override public Dialog show(String title, String[] labels, IntConsumer selected) {
            this.title = title; this.labels = labels; this.selected = selected; shown++;
            dialog = new Dialog(activity);
            dialog.show();
            return dialog;
        }
        void select(int position) {
            dialog.dismiss();
            selected.accept(position);
        }
    }
}

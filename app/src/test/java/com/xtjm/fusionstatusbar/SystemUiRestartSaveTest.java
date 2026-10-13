package com.xtjm.fusionstatusbar;

import android.widget.Button;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
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
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w360dp-h800dp-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class SystemUiRestartSaveTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private Button restartButton;
    private final ArrayDeque<Runnable> writes = new ArrayDeque<>();
    private final ArrayDeque<Runnable> callbacks = new ArrayDeque<>();
    private FusionConfig saved;
    private int writeCount;
    private int restartCount;
    private boolean failWrite;

    @Before public void launch() throws Exception {
        Field application = FusionConfigRepository.class.getDeclaredField("application");
        application.setAccessible(true);
        application.set(null, null);
        FusionConfigStore.write(RuntimeEnvironment.getApplication(), FusionConfig.defaults());
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!activity.settingsLoaded() && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(5);
        }
        assertTrue(activity.settingsLoaded());
        restartButton = activity.findViewById(R.id.restart_systemui_button);
        saved = (FusionConfig) field("config").get(activity);
        FusionConfigRepository repository = new FusionConfigRepository(new FusionConfigRepository.Storage() {
            @Override public FusionConfig read() { return saved; }
            @Override public FusionConfigStore.WriteResult write(FusionConfig value) {
                writeCount++;
                if (failWrite) return new FusionConfigStore.WriteResult(false, saved, "disk_failure");
                saved = value.withRevision(saved.revision + 1);
                return new FusionConfigStore.WriteResult(true, saved, "");
            }
        }, writes::add, callbacks::add);
        ((FusionConfigRepository.Session) field("configSession").get(activity)).close();
        field("configSession").set(activity, repository.newSession());
    }

    @After public void close() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void restartFlushesDebouncedSettingsAndWaitsForSaveConfirmation() {
        activity.update(saved.withIconScale(123));
        requestRestart();
        requestRestart();
        assertEquals(0, restartCount);
        assertFalse(restartButton.isEnabled());
        assertEquals(1, writes.size());

        writes.remove().run();
        assertEquals(123, saved.iconScale);
        assertEquals(0, restartCount);
        callbacks.remove().run();

        assertEquals(1, restartCount);
        assertEquals(1, writeCount);
    }

    @Test public void desktopRestartWaitsForSavedSettingsAndRejectsDuplicateRequests() {
        Button desktop = new Button(activity);
        desktop.setText(R.string.restart_desktop);
        activity.update(saved.withIconScale(131));
        assertTrue(activity.restartDesktop(desktop, () -> restartCount++));
        assertFalse(activity.restartDesktop(desktop, () -> restartCount++));
        assertEquals(0, restartCount);
        assertEquals(activity.getString(R.string.restarting_desktop), desktop.getText());
        completeSave();
        assertEquals(131, saved.iconScale);
        assertEquals(1, restartCount);
    }

    @Test public void failedSaveCancelsDesktopRestartAndRestoresItsOwnLabel() {
        Button desktop = new Button(activity);
        desktop.setText(R.string.restart_desktop);
        activity.update(saved.withIconScale(131));
        failWrite = true;
        assertTrue(activity.restartDesktop(desktop, () -> restartCount++));
        completeSave();
        assertEquals(0, restartCount);
        assertTrue(desktop.isEnabled());
        assertEquals(activity.getString(R.string.restart_desktop), desktop.getText());
    }

    @Test public void restartWaitsForEditsQueuedBehindAnInFlightRegularSave() {
        activity.update(saved.withIconScale(123));
        activity.flushSettingsSave();
        activity.update(saved.withIconScale(139));
        requestRestart();
        completeSave();
        assertEquals(0, restartCount);
        assertEquals(123, saved.iconScale);

        completeSave();
        assertEquals(1, restartCount);
        assertEquals(139, saved.iconScale);
        assertEquals(2, writeCount);
    }

    @Test public void restartWaitsForPublicationAndFollowingRegularSave() throws Exception {
        activity.updateControlCenterDraft(activity.editorSession().draft()
                .withSurface(ControlCenterSurface.SOFT_GLASS));
        ((Button) field("controlCenterPushButton").get(activity)).performClick();
        assertTrue(activity.savingControlCenter());
        activity.update(saved.withIconScale(127));
        requestRestart();

        completeSave();
        assertEquals(0, restartCount);
        assertFalse(activity.savingControlCenter());
        assertFalse(activity.editorSession().isDirty());
        completeSave();

        assertEquals(1, restartCount);
        assertEquals(127, saved.iconScale);
        assertEquals(ControlCenterSurface.SOFT_GLASS, saved.controlCenter.surface);
    }

    @Test public void failedRegularSaveCancelsRestartAndLaterSaveDoesNotRestart() {
        activity.update(saved.withIconScale(123));
        failWrite = true;
        requestRestart();
        completeSave();
        assertEquals(0, restartCount);
        assertTrue(restartButton.isEnabled());
        assertEquals(activity.getString(R.string.restart_systemui), restartButton.getText());

        failWrite = false;
        activity.flushSettingsSave();
        completeSave();
        assertEquals(123, saved.iconScale);
        assertEquals(0, restartCount);
    }

    @Test public void failedPublicationCancelsRestartAndKeepsDraftForRetry() throws Exception {
        activity.updateControlCenterDraft(activity.editorSession().draft()
                .withSurface(ControlCenterSurface.DARK));
        ((Button) field("controlCenterPushButton").get(activity)).performClick();
        failWrite = true;
        requestRestart();
        completeSave();

        assertEquals(0, restartCount);
        assertTrue(restartButton.isEnabled());
        assertTrue(activity.editorSession().isDirty());
        assertEquals(ControlCenterSurface.DEFAULT, saved.controlCenter.surface);
        requestRestart();
        assertEquals(0, restartCount);
    }

    @Test public void restartDoesNotPublishUnsubmittedEditorDraft() {
        activity.updateControlCenterDraft(activity.editorSession().draft()
                .withSurface(ControlCenterSurface.SOFT_GLASS));
        requestRestart();

        assertEquals(1, restartCount);
        assertEquals(0, writeCount);
        assertTrue(writes.isEmpty());
        assertEquals(ControlCenterSurface.DEFAULT, saved.controlCenter.surface);
        assertEquals(ControlCenterSurface.SOFT_GLASS, activity.editorSession().draft().surface);
        assertTrue(activity.editorSession().isDirty());
    }

    @Test public void destroyedActivityDoesNotRestartAfterSubmittedSaveCompletes() {
        activity.update(saved.withIconScale(123));
        requestRestart();
        controller.pause().stop().destroy();
        controller = null;
        completeSave();

        assertEquals(123, saved.iconScale);
        assertEquals(0, restartCount);
    }

    private void requestRestart() {
        activity.restartSystemUi(restartButton, () -> restartCount++);
    }

    private void completeSave() {
        writes.remove().run();
        if (!callbacks.isEmpty()) callbacks.remove().run();
    }

    private Field field(String name) throws Exception {
        Field result = MainActivity.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}

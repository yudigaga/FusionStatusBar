package com.xtjm.fusionstatusbar;

import android.content.Intent;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.function.Consumer;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35})
public class SettingsStartupCoordinatorTest {
    @Test public void activityBecomesReadyOnlyAfterConfigurationAndDraftRestoreComplete() {
        Pipeline pipeline = new Pipeline();
        Host host = new Host();
        SettingsStartupCoordinator coordinator = new SettingsStartupCoordinator(pipeline, host);
        Bundle state = new Bundle();
        state.putInt("selected_page", 2);

        coordinator.start(state);
        assertEquals(1, pipeline.readRequests);
        assertEquals(0, host.readyCalls);

        SettingsStartupCoordinator.Snapshot snapshot = new SettingsStartupCoordinator.Snapshot(
                FusionConfig.defaults(), 17);
        pipeline.readCallback.accept(snapshot);
        assertSame(snapshot, pipeline.restoreSnapshot);
        assertSame(state, pipeline.restoreState);
        assertEquals(0, host.readyCalls);

        pipeline.restoreCompletion.run();
        assertEquals(1, host.readyCalls);
        assertSame(snapshot, host.readySnapshot);
        assertSame(pipeline.session, host.readySession);
        assertTrue(coordinator.isReady());

        pipeline.restoreCompletion.run();
        pipeline.readCallback.accept(snapshot);
        assertEquals("Startup readiness is delivered exactly once", 1, host.readyCalls);
    }

    @Test public void oneActivityResultIsReplayedAfterReadinessAndLaterResultsStayWithActivity() {
        Pipeline pipeline = new Pipeline();
        Host host = new Host();
        SettingsStartupCoordinator coordinator = new SettingsStartupCoordinator(pipeline, host);
        coordinator.start(null);

        Intent data = new Intent("test.backup");
        assertTrue(coordinator.deferActivityResult(7102, -1, data));
        assertTrue(coordinator.deferActivityResult(7102, 0, null));
        pipeline.readCallback.accept(new SettingsStartupCoordinator.Snapshot(FusionConfig.defaults(), 3));
        assertEquals(0, host.resultCalls);
        pipeline.restoreCompletion.run();

        assertEquals(1, host.resultCalls);
        assertEquals(7102, host.requestCode);
        assertEquals(0, host.resultCode);
        assertNull(host.resultData);
        assertFalse(coordinator.deferActivityResult(7102, -1, data));
    }

    @Test public void duplicateConfigurationReadDoesNotStartDraftRestoreAgain() {
        Pipeline pipeline = new Pipeline();
        Host host = new Host();
        SettingsStartupCoordinator coordinator = new SettingsStartupCoordinator(pipeline, host);
        coordinator.start(null);

        SettingsStartupCoordinator.Snapshot first = new SettingsStartupCoordinator.Snapshot(
                FusionConfig.defaults(), 11);
        SettingsStartupCoordinator.Snapshot duplicate = new SettingsStartupCoordinator.Snapshot(
                FusionConfig.defaults(), 12);
        pipeline.readCallback.accept(first);
        Runnable firstRestoreCompletion = pipeline.restoreCompletion;
        pipeline.readCallback.accept(duplicate);

        assertEquals(1, pipeline.restoreCalls);
        assertSame(first, pipeline.restoreSnapshot);
        assertSame(firstRestoreCompletion, pipeline.restoreCompletion);
        firstRestoreCompletion.run();
        assertSame(first, host.readySnapshot);
        assertEquals(1, host.readyCalls);
    }

    @Test public void closeDiscardsLateReadAndRestoreCallbacksAndDeferredResult() {
        Pipeline pipeline = new Pipeline();
        Host host = new Host();
        SettingsStartupCoordinator coordinator = new SettingsStartupCoordinator(pipeline, host);
        coordinator.start(null);
        assertTrue(coordinator.deferActivityResult(7102, -1, new Intent("test.backup")));
        coordinator.close();

        pipeline.readCallback.accept(new SettingsStartupCoordinator.Snapshot(FusionConfig.defaults(), 9));
        assertNull(pipeline.restoreCompletion);
        assertEquals(0, host.readyCalls);
        assertEquals(0, host.resultCalls);
        assertFalse(coordinator.deferActivityResult(7102, -1, new Intent("late")));
    }

    @Test public void destroyedHostDoesNotReceiveReadinessOrDeferredResult() {
        Pipeline pipeline = new Pipeline();
        Host host = new Host();
        host.alive = false;
        SettingsStartupCoordinator coordinator = new SettingsStartupCoordinator(pipeline, host);
        coordinator.start(null);
        coordinator.deferActivityResult(7102, -1, new Intent("test.backup"));
        pipeline.readCallback.accept(new SettingsStartupCoordinator.Snapshot(FusionConfig.defaults(), 4));
        pipeline.restoreCompletion.run();

        assertEquals(0, host.readyCalls);
        assertEquals(0, host.resultCalls);
        assertFalse(coordinator.deferActivityResult(7102, -1, null));
    }

    private static final class Pipeline implements SettingsStartupCoordinator.Pipeline {
        Consumer<SettingsStartupCoordinator.Snapshot> readCallback;
        Runnable restoreCompletion;
        SettingsStartupCoordinator.Snapshot restoreSnapshot;
        Bundle restoreState;
        int readRequests;
        int restoreCalls;
        final ControlCenterEditorSession session = new ControlCenterEditorSession(ControlCenterConfig.defaults());

        @Override public void read(Consumer<SettingsStartupCoordinator.Snapshot> callback) {
            readRequests++;
            readCallback = callback;
        }

        @Override public void restoreEditor(SettingsStartupCoordinator.Snapshot snapshot, Bundle state, Runnable ready) {
            restoreCalls++;
            restoreSnapshot = snapshot;
            restoreState = state;
            restoreCompletion = ready;
        }

        @Override public ControlCenterEditorSession editorSession() { return session; }
    }

    private static final class Host implements SettingsStartupCoordinator.Host {
        boolean alive = true;
        int readyCalls;
        SettingsStartupCoordinator.Snapshot readySnapshot;
        ControlCenterEditorSession readySession;
        int resultCalls;
        int requestCode;
        int resultCode;
        Intent resultData;

        @Override public boolean isAlive() { return alive; }

        @Override public void onSettingsReady(SettingsStartupCoordinator.Snapshot snapshot,
                ControlCenterEditorSession session, Bundle state) {
            readyCalls++;
            readySnapshot = snapshot;
            readySession = session;
        }

        @Override public void onDeferredActivityResult(int requestCode, int resultCode, Intent data) {
            resultCalls++;
            this.requestCode = requestCode;
            this.resultCode = resultCode;
            resultData = data;
        }
    }
}

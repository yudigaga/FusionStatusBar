package com.xtjm.fusionstatusbar;

import android.os.Bundle;
import android.os.Looper;
import android.os.Parcel;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35})
@LooperMode(LooperMode.Mode.PAUSED)
public class ControlCenterPageCoordinatorTest {
    private ControlCenterPageCoordinator coordinator(ControlCenterDraftStoreTest.Storage storage, Bundle state) {
        ControlCenterDraftStore store = new ControlCenterDraftStore(storage, Runnable::run,
                Runnable::run, (task, delay) -> {});
        ControlCenterPageCoordinator page = new ControlCenterPageCoordinator(store, () -> {});
        page.restore(ControlCenterConfig.defaults(), 8, state, () -> {});
        return page;
    }

    @Test public void largeDraftUsesSmallActivityStateAndSurvivesDiskRecovery() {
        ControlCenterDraftStoreTest.Storage storage = new ControlCenterDraftStoreTest.Storage();
        storage.succeeds = true;
        ControlCenterPageCoordinator page = coordinator(storage, null);
        StringBuilder specs = new StringBuilder();
        for (int i = 0; i < 3000; i++) specs.append("custom(example.package").append(i).append("/.Tile),");
        page.session().edit(page.session().draft().withOrder(specs.toString()));
        page.session().selectMode(true);
        page.session().select("wifi");
        Bundle state = new Bundle();
        page.saveState(state);
        assertFalse(state.containsKey(ControlCenterPageCoordinator.KEY_INLINE));
        Parcel parcel = Parcel.obtain();
        state.writeToParcel(parcel, 0);
        assertTrue(parcel.dataSize() < 4096);
        parcel.recycle();
        page.close();
        // An unknown token models process death with no retained in-memory session.
        state.putString(ControlCenterPageCoordinator.KEY_TOKEN, "new-process");
        ControlCenterPageCoordinator restored = coordinator(storage, state);
        assertEquals(page.session().draft().order, restored.session().draft().order);
        assertTrue(restored.session().compact());
        assertEquals("wifi", restored.session().selection());
        assertTrue(restored.session().isDirty());
        restored.close();
    }

    @Test public void recreationRecoversLatestMemoryDraftWhenDiskWriteFails() {
        ControlCenterDraftStoreTest.Storage storage = new ControlCenterDraftStoreTest.Storage();
        ControlCenterPageCoordinator page = coordinator(storage, null);
        page.session().edit(page.session().draft().withSpacing(19));
        Bundle state = new Bundle();
        page.saveState(state);
        assertTrue(page.draftFailed());
        assertTrue(page.draftRetrying());
        page.close();
        ControlCenterPageCoordinator restored = coordinator(storage, state);
        assertEquals(19, restored.session().draft().spacing);
        assertTrue(restored.session().canUndo());
        restored.close();
    }

    @Test public void missingReceiptTimesOutWhileExplicitWaitingForMountDoesNotFail() {
        ControlCenterPageCoordinator page = coordinator(new ControlCenterDraftStoreTest.Storage(), null);
        page.published(10);
        assertEquals(R.string.editor_saved_waiting, page.applicationMessage(new Bundle()));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20_001));
        assertEquals(R.string.editor_apply_timeout, page.applicationMessage(new Bundle()));
        assertTrue(page.canRetryApplication());
        Bundle features = new Bundle();
        features.putString(FusionActivationStatus.FEATURE_CONTROL_CENTER, FusionActivationStatus.WAITING);
        Bundle receipt = new Bundle();
        receipt.putLong(FusionActivationStatus.KEY_APPLIED_REVISION, 10);
        receipt.putBundle(FusionActivationStatus.KEY_FEATURES, features);
        assertEquals(R.string.editor_waiting_mount, page.applicationMessage(receipt));
        assertFalse(page.canRetryApplication());
        features.putString(FusionActivationStatus.FEATURE_CONTROL_CENTER, FusionActivationStatus.FAILED);
        assertEquals(R.string.editor_apply_failed, page.applicationMessage(receipt));
        assertTrue(page.canRetryApplication());
        features.putString(FusionActivationStatus.FEATURE_CONTROL_CENTER, FusionActivationStatus.APPLIED);
        assertEquals(R.string.editor_applied, page.applicationMessage(receipt));
        assertFalse(page.canRetryApplication());
        page.close();
    }

    @Test public void restoredTimeoutIsNotResetByRepeatedRecreation() {
        ControlCenterDraftStoreTest.Storage storage = new ControlCenterDraftStoreTest.Storage();
        storage.succeeds = true;
        ControlCenterPageCoordinator page = coordinator(storage, null);
        page.published(8);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20_001));
        Bundle state = new Bundle();
        page.saveState(state);
        page.close();
        ControlCenterPageCoordinator restored = coordinator(storage, state);
        assertEquals(R.string.editor_apply_timeout, restored.applicationMessage(new Bundle()));
        restored.close();
    }

    @Test public void processDeathPrefersNewerDiskDraftOverOlderInlineState() {
        ControlCenterDraftStoreTest.Storage storage = new ControlCenterDraftStoreTest.Storage();
        storage.succeeds = true;
        ControlCenterPageCoordinator page = coordinator(storage, null);
        page.session().edit(page.session().draft().withSpacing(12));
        Bundle state = new Bundle();
        page.saveState(state);
        assertTrue(state.containsKey(ControlCenterPageCoordinator.KEY_INLINE));
        page.session().edit(page.session().draft().withSpacing(20));
        page.persist();
        page.close();
        state.putString(ControlCenterPageCoordinator.KEY_TOKEN, "another-process");
        ControlCenterPageCoordinator restored = coordinator(storage, state);
        assertEquals(20, restored.session().draft().spacing);
        restored.close();
    }

    @Test public void importedConfigurationPreservesUnpublishedDraftAndHistory() {
        ControlCenterDraftStoreTest.Storage storage = new ControlCenterDraftStoreTest.Storage();
        storage.succeeds = true;
        ControlCenterPageCoordinator page = coordinator(storage, null);
        page.session().edit(page.session().draft().withSpacing(20));
        ControlCenterConfig imported = ControlCenterConfig.defaults().withEnabled(true);
        page.replacePublished(imported, 11);
        assertTrue(ControlCenterEditorSession.sameConfig(imported, page.session().baseline()));
        assertEquals(20, page.session().draft().spacing);
        assertFalse(page.session().draft().enabled);
        assertTrue(page.session().isDirty());
        assertTrue(page.session().canUndo());
        page.close();
    }
}

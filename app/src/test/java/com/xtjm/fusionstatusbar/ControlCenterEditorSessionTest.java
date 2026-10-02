package com.xtjm.fusionstatusbar;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterEditorSessionTest {
    @Test public void presetRestoresConfigurationAndModeInOneUndoTransaction() {
        ControlCenterEditorSession session = new ControlCenterEditorSession(baseline());
        session.select("wifi");
        ControlCenterConfig previous = session.draft();
        assertTrue(session.loadPreset(previous.withEnabled(true).withSpacing(15), true));
        assertEquals(1, session.undoCount());
        assertTrue(session.compact());
        assertEquals("", session.selection());
        assertTrue(session.isDirty());
        assertTrue(session.undo());
        assertFalse(session.compact());
        assertEquals("wifi", session.selection());
        assertTrue(ControlCenterEditorSession.sameConfig(previous, session.draft()));
        assertFalse(session.isDirty());
        assertTrue(session.redo());
        assertTrue(session.compact());
        assertEquals(15, session.draft().spacing);
    }

    private ControlCenterConfig baseline() {
        return ControlCenterConfig.defaults().withLayoutPlan(ControlCenterLayoutPlan.blank(4).forPublication(false).encode());
    }

    @Test public void restorationKeepsBothCanvasesSelectionAndRedoWithoutPublishing() {
        ControlCenterConfig saved = baseline();
        ControlCenterEditorSession session = new ControlCenterEditorSession(saved);
        session.edit(saved.withEnabled(true));
        session.selectMode(true);
        session.select("wifi");
        session.edit(session.draft().withSpacing(12));
        assertTrue(session.undo());
        ControlCenterEditorSession restored = ControlCenterEditorSession.restore(saved, session.serialize());
        assertTrue(restored.draft().enabled);
        assertTrue(restored.compact());
        assertEquals("wifi", restored.selection());
        assertTrue(restored.canRedo());
        assertTrue(restored.redo());
        assertEquals(12, restored.draft().spacing);
        assertFalse(saved.enabled);
        assertTrue(restored.isDirty());
    }

    @Test public void failedSaveLeavesDraftAndHistoryRetrySuccessAdvancesBaseline() {
        ControlCenterConfig saved = baseline();
        ControlCenterEditorSession session = new ControlCenterEditorSession(saved);
        session.edit(saved.withEnabled(true));
        session.edit(session.draft().withSpacing(12));
        String failedState = session.serialize();
        ControlCenterEditorSession retried = ControlCenterEditorSession.restore(saved, failedState);
        assertTrue(retried.isDirty());
        assertEquals(2, retried.undoCount());
        ControlCenterConfig submitted = retried.draft();
        retried.saved(submitted, submitted);
        assertFalse(retried.isDirty());
        assertFalse(retried.canUndo());
    }

    @Test public void saveCompletionDoesNotEraseEditsMadeWhileWriting() {
        ControlCenterEditorSession session = new ControlCenterEditorSession(baseline());
        session.edit(session.draft().withEnabled(true));
        ControlCenterConfig submitted = session.draft();
        session.edit(submitted.withSpacing(20));
        session.saved(submitted, submitted);
        assertEquals(20, session.draft().spacing);
        assertTrue(session.isDirty());
        assertTrue(session.canUndo());
    }

    @Test public void undoBackToBaselineIsCleanAndNewEditInvalidatesRedo() {
        ControlCenterEditorSession session = new ControlCenterEditorSession(baseline());
        session.edit(session.draft().withSpacing(12));
        session.undo();
        assertFalse(session.isDirty());
        session.edit(session.draft().withEnabled(true));
        assertFalse(session.canRedo());
        assertFalse(session.redo());
    }

    @Test public void boundedHistorySurvivesSerializationAndCorruptionFallsBack() {
        ControlCenterEditorSession session = new ControlCenterEditorSession(baseline());
        for (int i = 0; i < 80; i++) session.edit(session.draft().withEnabled(i % 2 == 0));
        assertEquals(ControlCenterEditorSession.MAX_HISTORY, session.undoCount());
        assertEquals(ControlCenterEditorSession.MAX_HISTORY,
                ControlCenterEditorSession.restore(baseline(), session.serialize()).undoCount());
        assertFalse(ControlCenterEditorSession.restore(baseline(), "{broken").isDirty());
    }

    @Test public void completedPublicationAfterProcessDeathDoesNotReappearAsDirty() {
        ControlCenterEditorSession session = new ControlCenterEditorSession(baseline());
        session.selectMode(true);
        session.edit(session.draft().withEnabled(true));
        String processState = session.serialize();
        ControlCenterConfig committed = session.draft().withLayoutPlan(
                ControlCenterLayoutPlan.decode(session.draft().layoutPlan).forPublication(true).encode());
        ControlCenterEditorSession restored = ControlCenterEditorSession.restore(committed, processState);
        assertFalse(restored.isDirty());
        assertTrue(restored.compact());
    }
}

package com.xtjm.fusionstatusbar;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Owns editor recovery, private draft durability and the published revision's application deadline. */
final class ControlCenterPageCoordinator implements AutoCloseable {
    static final int MAX_INLINE_CHARS = 8 * 1024;
    static final long APPLY_TIMEOUT_MILLIS = 20_000;
    static final String KEY_TOKEN = "editor_token";
    static final String KEY_INLINE = "editor_fallback";
    private static final String KEY_REVISION = "editor_revision";
    private static final String KEY_DEADLINE = "editor_deadline";
    private static final LinkedHashMap<String, String> RETAINED = new LinkedHashMap<>();
    private final ControlCenterDraftStore store;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable timeout;
    private String token = UUID.randomUUID().toString();
    private ControlCenterEditorSession session;
    private boolean draftFailed;
    private boolean draftRetrying;
    private long revision;
    private long deadline;
    private boolean closed;
    private int applicationMessage = R.string.editor_saved_waiting;

    ControlCenterPageCoordinator(ControlCenterDraftStore store, Runnable changed) {
        this.store = store; this.changed = changed;
        timeout = () -> { if (!closed) changed.run(); };
    }

    void restore(ControlCenterConfig saved, long savedRevision, Bundle state, Runnable ready) {
        store.read(stored -> {
            String encoded = stored;
            if (state != null) {
                token = state.getString(KEY_TOKEN, token);
                synchronized (RETAINED) {
                    String retained = RETAINED.get(token);
                    String fallback = retained != null ? retained
                            : state.getString(KEY_INLINE, state.getString("editor_session", ""));
                    encoded = ControlCenterEditorSession.latestSnapshot(fallback, stored);
                }
            }
            session = ControlCenterEditorSession.restore(saved, encoded);
            revision = savedRevision;
            deadline = state != null && state.getLong(KEY_REVISION, -1) == revision
                    ? state.getLong(KEY_DEADLINE, 0) : 0;
            if (deadline <= 0) deadline = SystemClock.elapsedRealtime() + APPLY_TIMEOUT_MILLIS;
            scheduleTimeout();
            ready.run();
            persist();
        });
    }

    ControlCenterEditorSession session() { return session; }
    boolean draftFailed() { return draftFailed; }
    boolean draftRetrying() { return draftRetrying; }

    void persist() {
        if (session == null || closed) return;
        String encoded = session.serialize();
        synchronized (RETAINED) {
            RETAINED.put(token, encoded);
            while (RETAINED.size() > 4) RETAINED.remove(RETAINED.keySet().iterator().next());
        }
        store.write(encoded, result -> {
            boolean wasFailed = draftFailed;
            boolean wasRetrying = draftRetrying;
            draftFailed = !result.success;
            draftRetrying = result.retrying;
            if (wasFailed != draftFailed || wasRetrying != draftRetrying) changed.run();
        });
    }

    void saveState(Bundle out) {
        persist();
        out.putString(KEY_TOKEN, token);
        out.putLong(KEY_REVISION, revision);
        out.putLong(KEY_DEADLINE, deadline);
        if (session != null) {
            String encoded = session.serialize();
            if (encoded.length() <= MAX_INLINE_CHARS) out.putString(KEY_INLINE, encoded);
        }
    }

    void retryDraft() { store.retry(); }

    void published(long value) {
        revision = value;
        deadline = SystemClock.elapsedRealtime() + APPLY_TIMEOUT_MILLIS;
        applicationMessage = R.string.editor_saved_waiting;
        scheduleTimeout();
    }

    private void scheduleTimeout() {
        main.removeCallbacks(timeout);
        main.postDelayed(timeout, Math.max(1, deadline - SystemClock.elapsedRealtime()));
    }

    int applicationMessage(Bundle status) {
        String state = "";
        if (status.getLong(FusionActivationStatus.KEY_APPLIED_REVISION, -1) == revision) {
            Bundle features = status.getBundle(FusionActivationStatus.KEY_FEATURES);
            state = features == null ? "" : features.getString(FusionActivationStatus.FEATURE_CONTROL_CENTER, "");
        }
        if (state.startsWith(FusionActivationStatus.APPLIED) || state.startsWith(FusionActivationStatus.DISABLED))
            applicationMessage = R.string.editor_applied;
        else if (state.startsWith(FusionActivationStatus.DEGRADED)) applicationMessage = R.string.editor_degraded;
        else if (state.startsWith(FusionActivationStatus.FAILED)) applicationMessage = R.string.editor_apply_failed;
        else if (state.startsWith(FusionActivationStatus.WAITING)) applicationMessage = R.string.editor_waiting_mount;
        else applicationMessage = SystemClock.elapsedRealtime() >= deadline
                ? R.string.editor_apply_timeout : R.string.editor_saved_waiting;
        return applicationMessage;
    }

    boolean canRetryApplication() {
        return applicationMessage == R.string.editor_apply_timeout || applicationMessage == R.string.editor_apply_failed
                || applicationMessage == R.string.editor_degraded;
    }

    void replacePublished(ControlCenterConfig saved, long savedRevision) {
        if (session != null && session.isDirty()) session.rebase(saved);
        else session = new ControlCenterEditorSession(saved);
        published(savedRevision);
        persist();
    }

    @Override public void close() {
        persist();
        closed = true;
        main.removeCallbacksAndMessages(null);
        store.close();
    }
}

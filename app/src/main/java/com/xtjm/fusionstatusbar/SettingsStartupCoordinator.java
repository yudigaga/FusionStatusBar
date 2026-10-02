package com.xtjm.fusionstatusbar;

import android.content.Intent;
import android.os.Bundle;
import java.util.function.Consumer;

/** Orders configuration loading, editor recovery, Activity readiness, and deferred picker results. */
final class SettingsStartupCoordinator implements AutoCloseable {
    interface Host {
        boolean isAlive();
        void onSettingsReady(Snapshot snapshot, ControlCenterEditorSession editorSession, Bundle state);
        void onDeferredActivityResult(int requestCode, int resultCode, Intent data);
    }

    interface Pipeline {
        void read(Consumer<Snapshot> callback);
        void restoreEditor(Snapshot snapshot, Bundle state, Runnable ready);
        ControlCenterEditorSession editorSession();
    }

    static final class Snapshot {
        final FusionConfig config;
        final long revision;

        Snapshot(FusionConfig config, long revision) {
            this.config = config;
            this.revision = revision;
        }
    }

    private static final class DeferredResult {
        final int requestCode;
        final int resultCode;
        final Intent data;

        DeferredResult(int requestCode, int resultCode, Intent data) {
            this.requestCode = requestCode;
            this.resultCode = resultCode;
            this.data = data;
        }
    }

    private final Pipeline pipeline;
    private final Host host;
    private boolean started;
    private boolean restoreStarted;
    private boolean closed;
    private boolean ready;
    private Bundle state;
    private Snapshot snapshot;
    private DeferredResult deferredResult;

    SettingsStartupCoordinator(FusionConfigRepository.Session configSession,
            ControlCenterPageCoordinator editorPage, Host host) {
        this(new Pipeline() {
            @Override public void read(Consumer<Snapshot> callback) {
                configSession.read(config -> callback.accept(new Snapshot(config, config.revision)));
            }

            @Override public void restoreEditor(Snapshot loaded, Bundle savedState, Runnable ready) {
                editorPage.restore(loaded.config.controlCenter, loaded.revision, savedState, ready);
            }

            @Override public ControlCenterEditorSession editorSession() { return editorPage.session(); }
        }, host);
    }

    SettingsStartupCoordinator(Pipeline pipeline, Host host) {
        if (pipeline == null || host == null) throw new IllegalArgumentException("missing_startup_dependency");
        this.pipeline = pipeline;
        this.host = host;
    }

    void start(Bundle savedState) {
        if (closed || started) return;
        started = true;
        state = savedState;
        pipeline.read(loaded -> {
            if (closed || loaded == null) return;
            if (restoreStarted) return;
            restoreStarted = true;
            snapshot = loaded;
            pipeline.restoreEditor(loaded, state, this::editorRestored);
        });
    }

    /** Returns true when the caller must let startup replay this Activity result later. */
    boolean deferActivityResult(int requestCode, int resultCode, Intent data) {
        if (closed || ready) return false;
        // Startup can launch at most one document picker; retain only its latest callback.
        deferredResult = new DeferredResult(requestCode, resultCode, data);
        return true;
    }

    boolean isReady() { return ready; }

    private void editorRestored() {
        if (closed || ready || snapshot == null) return;
        if (!host.isAlive()) {
            close();
            return;
        }
        ready = true;
        Bundle savedState = state;
        state = null;
        host.onSettingsReady(snapshot, pipeline.editorSession(), savedState);
        if (closed || !host.isAlive()) return;
        DeferredResult result = deferredResult;
        deferredResult = null;
        if (result != null) host.onDeferredActivityResult(result.requestCode, result.resultCode, result.data);
    }

    @Override public void close() {
        closed = true;
        state = null;
        snapshot = null;
        deferredResult = null;
    }
}

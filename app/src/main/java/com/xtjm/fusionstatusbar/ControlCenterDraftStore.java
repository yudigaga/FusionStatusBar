package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicLong;

/** Serial, latest-only draft persistence; a failed write remains available for retry. */
final class ControlCenterDraftStore {
    static final int MAX_ATTEMPTS = 3;
    private static final String PREFS = "control_center_editor_draft";
    private static final AtomicLong APPLICATION_SEQUENCE = new AtomicLong();
    private static final ScheduledExecutorService IO = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "FusionEditorDraft"); thread.setDaemon(true); return thread;
    });
    interface Storage { String read(); boolean write(String value); }
    interface Scheduler { void schedule(Runnable task, long delayMillis); }
    static final class Result {
        final boolean success;
        final boolean retrying;
        Result(boolean success, boolean retrying) { this.success = success; this.retrying = retrying; }
    }
    private final Storage storage;
    private final Executor worker;
    private final Executor callbacks;
    private final Scheduler scheduler;
    private final AtomicLong sequence;
    private boolean closed;
    private String pending;
    private String committed;
    private long generation;
    private int attempts;
    private boolean writing;
    private Consumer<Result> listener;
    private boolean deliveryPosted;
    private Result pendingResult;
    private long resultGeneration;

    ControlCenterDraftStore(Context context) {
        Context app = context.getApplicationContext();
        storage = new Storage() {
            public String read() { return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("session", ""); }
            public boolean write(String value) {
                return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("session", value).commit();
            }
        };
        worker = IO;
        Handler main = new Handler(Looper.getMainLooper());
        callbacks = main::post;
        scheduler = (task, delay) -> IO.schedule(task, delay, TimeUnit.MILLISECONDS);
        sequence = APPLICATION_SEQUENCE;
    }

    ControlCenterDraftStore(Storage storage, Executor worker, Executor callbacks, Scheduler scheduler) {
        this(storage, worker, callbacks, scheduler, new AtomicLong());
    }

    ControlCenterDraftStore(Storage storage, Executor worker, Executor callbacks, Scheduler scheduler, AtomicLong sequence) {
        this.storage = storage; this.worker = worker; this.callbacks = callbacks; this.scheduler = scheduler;
        this.sequence = sequence;
    }

    void read(Consumer<String> callback) {
        worker.execute(() -> {
            String value;
            try { value = storage.read(); }
            catch (RuntimeException error) { value = ""; }
            String result = value;
            callbacks.execute(() -> { synchronized (this) { if (!closed) callback.accept(result); } });
        });
    }

    synchronized void write(String session, Consumer<Result> callback) {
        if (closed) return;
        listener = callback;
        if (session.equals(committed) && pending == null) { report(generation, true, false); return; }
        if (!session.equals(pending)) {
            pending = session;
            generation = sequence.incrementAndGet();
            attempts = 0;
        }
        start();
    }

    synchronized void retry() {
        if (closed || pending == null) return;
        attempts = 0;
        start();
    }

    private void start() {
        if (!writing && pending != null && attempts < MAX_ATTEMPTS) {
            writing = true;
            worker.execute(this::attempt);
        }
    }

    private void attempt() {
        String value;
        long request;
        synchronized (this) {
            if (generation != sequence.get()) { pending = null; writing = false; return; }
            value = pending; request = generation; attempts++;
        }
        boolean success;
        try { success = storage.write(value); } catch (RuntimeException error) { success = false; }
        synchronized (this) {
            if (success) committed = value;
            if (request != generation) { writing = false; start(); return; }
            boolean retrying = !success && attempts < MAX_ATTEMPTS;
            if (success) pending = null;
            if (retrying) {
                scheduler.schedule(() -> {
                    synchronized (this) { writing = false; start(); }
                }, 500L * attempts);
            } else { writing = false; }
            report(request, success, retrying);
        }
    }

    private void report(long request, boolean success, boolean retrying) {
        pendingResult = new Result(success, retrying);
        resultGeneration = request;
        if (deliveryPosted || closed) return;
        deliveryPosted = true;
        callbacks.execute(this::deliver);
    }

    private synchronized void deliver() {
        deliveryPosted = false;
        Result result = pendingResult;
        pendingResult = null;
        if (!closed && resultGeneration == generation && listener != null && result != null) listener.accept(result);
    }

    synchronized void close() { closed = true; listener = null; }
}

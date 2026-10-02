package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Serial provider IO; only the newest requested, non-regressing snapshot reaches views. */
final class RuntimeConfigCoordinator {
    interface Reader { FusionConfig read(); }
    interface Listener {
        void loaded(FusionConfig config);
        void unavailable();
    }
    private final RuntimeWorkQueue work;
    private final Executor main;
    private final Reader reader;
    private final Listener listener;
    private final RuntimeAcknowledgements acknowledgements;
    private long generation;
    private long appliedRevision = -1;
    private Delivery pendingDelivery;
    private boolean deliveryPosted;

    private record Delivery(long request, FusionConfig config) { }

    RuntimeConfigCoordinator(Context context, Listener listener) {
        this(new RuntimeWorkQueue("fusion-config", 4),
                mainExecutor(new Handler(Looper.getMainLooper())),
                () -> FusionConfig.readFromProvider(context), listener);
    }

    RuntimeConfigCoordinator(RuntimeWorkQueue work, Executor main, Reader reader, Listener listener) {
        this.work = work;
        this.main = main;
        this.reader = reader;
        this.listener = listener;
        this.acknowledgements = new RuntimeAcknowledgements(work,
                RuntimeAcknowledgements.scheduler(new Handler(Looper.getMainLooper())),
                FusionActivationStatus::reportApplied);
    }

    void reload() {
        long request;
        synchronized (this) { request = ++generation; }
        if (!work.execute("read", this::readLatest)) offerDelivery(request, null);
    }

    private void readLatest() {
        long request;
        synchronized (this) { request = generation; }
        FusionConfig loaded;
        try { loaded = reader.read(); }
        catch (Throwable error) { loaded = null; }
        offerDelivery(request, loaded);
    }

    private void offerDelivery(long request, FusionConfig loaded) {
        synchronized (this) {
            if (request != generation) return;
            pendingDelivery = new Delivery(request, loaded);
            if (deliveryPosted) return;
            deliveryPosted = true;
        }
        try {
            main.execute(this::deliverPending);
        } catch (RejectedExecutionException rejected) {
            synchronized (this) {
                deliveryPosted = false;
                pendingDelivery = null;
            }
        }
    }

    private void deliverPending() {
        FusionConfig loaded;
        boolean unavailable;
        synchronized (this) {
            Delivery delivery = pendingDelivery;
            pendingDelivery = null;
            deliveryPosted = false;
            if (delivery == null || delivery.request != generation) return;
            loaded = delivery.config;
            unavailable = loaded == null || loaded.revision < appliedRevision;
            if (!unavailable) appliedRevision = loaded.revision;
        }
        // OEM callbacks may re-enter registration from another thread; never hold this lock during view work.
        if (unavailable) {
            listener.unavailable();
            return;
        }
        acknowledgements.advanceRevision(loaded.revision);
        listener.loaded(loaded);
    }

    void report(Context context, long revision, String feature, String state, String reason) {
        acknowledgements.report(context, revision, feature, state, reason);
    }

    private static Executor mainExecutor(Handler handler) {
        return task -> {
            if (!handler.post(task)) throw new RejectedExecutionException("Main looper stopped");
        };
    }
}

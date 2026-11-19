package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.view.View;
import android.view.WindowManager;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;

/** Tracks only the public cross-window API; it says nothing about Mi private blur. */
final class PublicWindowBlurObserver implements View.OnAttachStateChangeListener, AutoCloseable {
    enum Availability { UNKNOWN, FALSE, TRUE }

    interface Backend {
        boolean isEnabled();
        void add(Consumer<Boolean> callback);
        void remove(Consumer<Boolean> callback);
    }

    private final WeakReference<View> decor;
    private final Backend backend;
    private final Consumer<Availability> listener;
    private Consumer<Boolean> callback;
    private Availability availability = Availability.UNKNOWN;
    private long generation, publication;
    private boolean closed;

    PublicWindowBlurObserver(View decor, Consumer<Availability> listener) {
        this(decor, new AndroidBackend(decor.getContext().getApplicationContext()), listener);
    }

    PublicWindowBlurObserver(View decor, Backend backend, Consumer<Availability> listener) {
        this.decor = new WeakReference<>(decor);
        this.backend = backend;
        this.listener = listener;
        decor.addOnAttachStateChangeListener(this);
        if (decor.isAttachedToWindow()) register();
    }

    @Override public void onViewAttachedToWindow(View view) {
        if (view == decor.get()) register();
    }

    @Override public void onViewDetachedFromWindow(View view) {
        if (view == decor.get() && !closed) {
            unregister();
            publish(Availability.UNKNOWN);
        }
    }

    private void register() {
        if (closed || callback != null) return;
        final long registration = ++generation;
        Consumer<Boolean> next = enabled -> {
            if (!closed && generation == registration)
                publish(enabled == null ? Availability.UNKNOWN
                        : enabled ? Availability.TRUE : Availability.FALSE);
        };
        callback = next;
        try {
            publish(backend.isEnabled() ? Availability.TRUE : Availability.FALSE);
            if (!closed && generation == registration) backend.add(next);
        } catch (RuntimeException | LinkageError ignored) {
            // add can fail after retaining the consumer. Invalidate it before cleanup.
            if (generation == registration) {
                unregister();
                publish(Availability.UNKNOWN);
            }
        }
    }

    private void unregister() {
        ++generation;
        Consumer<Boolean> previous = callback;
        callback = null;
        if (previous != null) {
            try { backend.remove(previous); }
            catch (RuntimeException | LinkageError ignored) { }
        }
    }

    private void publish(Availability value) {
        if (closed || availability == value) return;
        final long registration = generation;
        final long delivery = ++publication;
        availability = value;
        try { listener.accept(value); }
        catch (RuntimeException | LinkageError ignored) {
            if (closed || generation != registration || publication != delivery) return;
            availability = Availability.UNKNOWN;
            final long recovery = ++publication;
            if (value != Availability.UNKNOWN && !closed && generation == registration
                    && publication == recovery) {
                try { listener.accept(Availability.UNKNOWN); }
                catch (RuntimeException | LinkageError fallbackIgnored) { }
            }
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        View view = decor.get();
        try { if (view != null) view.removeOnAttachStateChangeListener(this); }
        catch (RuntimeException | LinkageError ignored) { }
        unregister();
        availability = Availability.UNKNOWN;
        decor.clear();
    }

    private static final class AndroidBackend implements Backend {
        private final Context application;
        private WindowManager manager;

        AndroidBackend(Context application) { this.application = application; }

        @Override public boolean isEnabled() {
            if (application == null) throw new IllegalStateException("No application context");
            manager = application.getSystemService(WindowManager.class);
            if (manager == null) throw new IllegalStateException("No WindowManager");
            return manager.isCrossWindowBlurEnabled();
        }

        @Override public void add(Consumer<Boolean> callback) {
            manager.addCrossWindowBlurEnabledListener(application.getMainExecutor(), callback);
        }

        @Override public void remove(Consumer<Boolean> callback) {
            if (manager != null) manager.removeCrossWindowBlurEnabledListener(callback);
        }
    }
}

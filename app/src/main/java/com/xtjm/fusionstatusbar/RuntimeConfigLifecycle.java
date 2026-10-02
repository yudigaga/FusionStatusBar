package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Handler;

/** Owns lazy runtime-config startup, observer wiring, and recovery retry policy. */
final class RuntimeConfigLifecycle {
    interface Host {
        void apply(FusionConfig config) throws Throwable;
        void onApplyFailure(FusionConfig config, Throwable error);
        void onRecovered();
        void requestPreview(Context context);
    }

    interface ConfigAccess {
        void reload();
        void report(Context context, long revision, String feature, String state, String reason);
    }

    interface ObserverAccess { void ensureRegistered(); }

    interface Factory {
        ConfigAccess createConfig(Context context, RuntimeConfigCoordinator.Listener listener);
        ObserverAccess createObservers(Context context, Handler main, Runnable reload,
                Runnable preview);
    }

    interface Scheduler { boolean postDelayed(Runnable task, long delayMs); }

    private final Handler main;
    private final Factory factory;
    private final Scheduler scheduler;
    private final Host host;
    private Context context;
    private ConfigAccess config;
    private ObserverAccess observers;
    private boolean retryPosted;
    private int retryCount;

    RuntimeConfigLifecycle(Handler main, Host host) {
        this(main, (task, delay) -> main.postDelayed(task, delay), productionFactory(), host);
    }

    RuntimeConfigLifecycle(Handler main, Scheduler scheduler, Factory factory, Host host) {
        if (main == null || scheduler == null || factory == null || host == null)
            throw new IllegalArgumentException("missing_runtime_config_lifecycle_dependency");
        this.main = main;
        this.scheduler = scheduler;
        this.factory = factory;
        this.host = host;
    }

    synchronized void ensureStarted(Context source) {
        if (source == null) return;
        if (config == null) {
            Context application = source.getApplicationContext();
            Context selectedContext = application == null ? source : application;
            ConfigAccess createdConfig = factory.createConfig(selectedContext,
                    new RuntimeConfigCoordinator.Listener() {
                @Override public void loaded(FusionConfig loaded) { onLoaded(loaded); }
                @Override public void unavailable() { scheduleRetry(); }
            });
            ObserverAccess createdObservers = factory.createObservers(selectedContext, main,
                    RuntimeConfigLifecycle.this::reload, RuntimeConfigLifecycle.this::requestPreview);
            context = selectedContext;
            config = createdConfig;
            observers = createdObservers;
            config.reload();
        }
        observers.ensureRegistered();
    }

    void reload() {
        Context currentContext;
        synchronized (this) { currentContext = context; }
        if (currentContext == null) return;
        ensureStarted(currentContext);
        ConfigAccess current;
        synchronized (this) { current = config; }
        if (current != null) current.reload();
    }

    void report(long revision, String feature, String state, String reason) {
        ConfigAccess current;
        Context currentContext;
        synchronized (this) {
            current = config;
            currentContext = context;
        }
        if (current != null && currentContext != null)
            current.report(currentContext, revision, feature, state, reason);
    }

    synchronized Context context() { return context; }

    private void requestPreview() {
        Context current;
        synchronized (this) { current = context; }
        if (current != null) host.requestPreview(current);
    }

    private void onLoaded(FusionConfig loaded) {
        boolean recovered;
        synchronized (this) {
            recovered = retryCount > 0;
            retryCount = 0;
        }
        if (recovered) host.onRecovered();
        try {
            host.apply(loaded);
        } catch (Throwable error) {
            try { host.onApplyFailure(loaded, error); }
            finally { scheduleRetry(); }
        }
    }

    private void scheduleRetry() {
        long delay;
        synchronized (this) {
            if (retryPosted || context == null) return;
            retryPosted = true;
            delay = Math.min(30_000L, 2_000L << Math.min(retryCount++, 4));
        }
        boolean posted;
        try {
            posted = scheduler.postDelayed(() -> {
                synchronized (RuntimeConfigLifecycle.this) { retryPosted = false; }
                reload();
            }, delay);
        } catch (Throwable error) {
            posted = false;
        }
        if (!posted) {
            synchronized (this) { retryPosted = false; }
        }
    }

    private static Factory productionFactory() {
        return new Factory() {
            @Override public ConfigAccess createConfig(Context context,
                    RuntimeConfigCoordinator.Listener listener) {
                RuntimeConfigCoordinator coordinator = new RuntimeConfigCoordinator(context, listener);
                return new ConfigAccess() {
                    @Override public void reload() { coordinator.reload(); }
                    @Override public void report(Context reportContext, long revision,
                            String feature, String state, String reason) {
                        coordinator.report(reportContext, revision, feature, state, reason);
                    }
                };
            }

            @Override public ObserverAccess createObservers(Context context, Handler main,
                    Runnable reload, Runnable preview) {
                RuntimeObserverRegistration registration = new RuntimeObserverRegistration(
                        context, main, reload, preview);
                return registration::ensureRegistered;
            }
        };
    }
}

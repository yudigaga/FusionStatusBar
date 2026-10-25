package com.xtjm.fusionstatusbar;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Keeps capability evidence and retry budgets isolated to the loader owning the active panel. */
final class ControlCenterPluginCapabilities {
    private static final class Entry {
        final BoundedHookRetry retry;
        ControlCenterPluginHookInstaller.Result result;
        Entry(BoundedHookRetry.Scheduler scheduler) { retry = new BoundedHookRetry(scheduler, 3000L, 8); }
    }

    private final Map<ClassLoader, Entry> entries = new WeakHashMap<>();
    private final BoundedHookRetry.Scheduler scheduler;
    private WeakReference<ClassLoader> active = new WeakReference<>(null);

    ControlCenterPluginCapabilities(BoundedHookRetry.Scheduler scheduler) { this.scheduler = scheduler; }

    synchronized void record(ClassLoader loader, ControlCenterPluginHookInstaller.Result result) {
        if (loader != null) entries.computeIfAbsent(loader, ignored -> new Entry(scheduler)).result = result;
    }

    synchronized void activate(ClassLoader loader) {
        if (loader != null) active = new WeakReference<>(loader);
    }

    /** A built-in panel may be discovered before any plugin explicitly owns the host. */
    synchronized void activateIfUnclaimed(ClassLoader loader) {
        if (loader == null || active.get() != null) return;
        Entry entry = entries.get(loader);
        if (entry != null && entry.result != null && entry.result.layoutAvailable)
            active = new WeakReference<>(loader);
    }

    synchronized ControlCenterPluginHookInstaller.Result activeResult() {
        Entry entry = entries.get(active.get());
        return entry == null ? null : entry.result;
    }

    synchronized ControlCenterPluginHookInstaller.Result resultFor(ClassLoader loader) {
        Entry entry = loader == null ? null : entries.get(loader);
        return entry == null ? null : entry.result;
    }

    void scheduleRetries(Consumer<ClassLoader> installer) {
        ArrayList<ClassLoader> loaders;
        synchronized (this) { loaders = new ArrayList<>(entries.keySet()); }
        for (ClassLoader loader : loaders) scheduleRetry(loader, installer);
    }

    void scheduleRetry(ClassLoader loader, Consumer<ClassLoader> installer) {
        Entry entry;
        synchronized (this) { entry = entries.get(loader); }
        if (entry == null) return;
        WeakReference<ClassLoader> target = new WeakReference<>(loader);
        entry.retry.scheduleIfNeeded(() -> {
            synchronized (this) {
                return target.get() != null && entry.result != null && entry.result.needsRetry();
            }
        }, () -> {
            ClassLoader value = target.get();
            if (value != null) installer.accept(value);
        });
    }
}

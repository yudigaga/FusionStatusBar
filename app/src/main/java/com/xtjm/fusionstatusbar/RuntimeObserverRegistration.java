package com.xtjm.fusionstatusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import java.util.function.BiConsumer;

/** Registration failures retry independently; successful subscriptions remain registered once. */
final class RuntimeObserverRegistration {
    private final RuntimeCleanup.Step[] registrations;
    private final boolean[] registered;
    private final BiConsumer<Runnable, Long> scheduler;
    private int retries;
    private boolean retryPosted;
    private boolean exhausted;

    RuntimeObserverRegistration(Context context, Handler main, Runnable reload, Runnable preview) {
        this((task, delay) -> main.postDelayed(task, delay),
                () -> context.getContentResolver().registerContentObserver(
                        FusionConfig.contentUri(), true, observer(main, reload)),
                () -> context.getContentResolver().registerContentObserver(
                        FusionConfig.controlCenterPreviewUri(), true, observer(main, preview)),
                () -> context.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context ignored, Intent intent) { reload.run(); }
                }, new IntentFilter(Intent.ACTION_USER_UNLOCKED), Context.RECEIVER_NOT_EXPORTED));
    }

    RuntimeObserverRegistration(BiConsumer<Runnable, Long> scheduler, RuntimeCleanup.Step... registrations) {
        this.registrations = registrations;
        this.registered = new boolean[registrations.length];
        this.scheduler = scheduler;
    }

    synchronized void ensureRegistered() {
        if (retryPosted || exhausted) return;
        boolean complete = true;
        for (int i = 0; i < registrations.length; i++) {
            if (!registered[i]) {
                int index = i;
                RuntimeCleanup.run("register observer " + i, () -> {
                    registrations[index].run();
                    registered[index] = true;
                });
            }
            complete &= registered[i];
        }
        if (!complete && !retryPosted && retries < 6) {
            retryPosted = true;
            scheduler.accept(() -> {
                synchronized (RuntimeObserverRegistration.this) { retryPosted = false; }
                ensureRegistered();
            }, Math.min(30_000L, 1_000L << retries++));
        } else if (!complete) {
            exhausted = true;
        }
    }

    private static ContentObserver observer(Handler main, Runnable changed) {
        return new ContentObserver(main) {
            @Override public void onChange(boolean selfChange) { changed.run(); }
        };
    }
}

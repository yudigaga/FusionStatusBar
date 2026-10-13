package com.xtjm.fusionstatusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.io.File;

import io.github.libxposed.api.XposedModule;

/** Signature-protected updates and a durable system-owned snapshot for the startup gate. */
final class MiscSystemConfigExchange {
    static final String SYSTEM_PACKAGE = "android";
    static final String ACTION_PUBLISH = BuildConfig.APPLICATION_ID + ".action.PUBLISH_MISC_CONFIG";
    private static volatile MiscConfig config = MiscConfig.defaults();
    private static WeakReference<Context> runtimeContext = new WeakReference<>(null);
    private static WeakReference<XposedModule> reporter = new WeakReference<>(null);
    private static Handler worker;
    private static int retries;
    private static int registrationRetries;
    private static boolean receiverRegistered;
    private static boolean observerRegistered;
    private static final Runnable REFRESH = MiscSystemConfigExchange::refresh;
    private static FusionConfigStore.SnapshotFile cache = new BoundedAtomicDocument(
            new File(Environment.getDataDirectory(), "system/fusion_statusbar_misc_v1.json"), 4096);

    private MiscSystemConfigExchange() { }

    static MiscConfig current() { return config; }

    static void setReporter(XposedModule module) { reporter = new WeakReference<>(module); }

    static void report(String message) {
        XposedModule module = reporter.get();
        if (module != null) module.log(Log.INFO, FusionModule.TAG, message);
        else Log.i(FusionModule.TAG, message);
    }

    static synchronized void attach(Context source) {
        if (source == null || !SYSTEM_PACKAGE.equals(source.getPackageName())
                || runtimeContext.get() != null) return;
        runtimeContext = new WeakReference<>(source);
        try { config = MiscConfigStore.read(cache); }
        catch (RuntimeException error) { report("system start confirmation cache unavailable"); }
        report("system start confirmation config origin=cache enabled=" + config.removeChainStartDialog);
        HandlerThread thread = new HandlerThread("FusionSystemStartConfig");
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(MiscSystemConfigExchange::registerEndpoints);
        worker.post(REFRESH);
    }

    private static void registerEndpoints() {
        Context source = runtimeContext.get();
        if (source == null) return;
        try {
            if (!receiverRegistered) source.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (receive(context, intent)) {
                        worker.removeCallbacks(REFRESH);
                        report("system start confirmation config origin=broadcast enabled="
                                + config.removeChainStartDialog);
                    }
                }
            }, new IntentFilter(ACTION_PUBLISH), RuntimeConfigExchange.PUBLISH_PERMISSION, worker,
                    Context.RECEIVER_EXPORTED);
            receiverRegistered = true;
        } catch (RuntimeException error) {
            if (registrationRetries == 0) report("system start confirmation receiver awaiting system services");
        }
        try {
            if (!observerRegistered) source.getContentResolver().registerContentObserver(MiscConfig.contentUri(), false,
                    new ContentObserver(worker) {
                        @Override public void onChange(boolean selfChange) { refresh(); }
                    });
            observerRegistered = true;
        } catch (RuntimeException error) {
            if (registrationRetries == 0) report("system start confirmation observer awaiting system services");
        }
        if (receiverRegistered && observerRegistered) {
            report("system start confirmation config receiver ready permission=signature");
        } else if (++registrationRetries <= 30) {
            worker.postDelayed(MiscSystemConfigExchange::registerEndpoints, 1000L);
        } else {
            report("system start confirmation config registration incomplete");
        }
    }

    private static synchronized void refresh() {
        Context context = runtimeContext.get();
        if (context == null) return;
        MiscConfig loaded = MiscConfig.readFromProvider(context);
        if (loaded == null) {
            if (retries == 0) report("system start confirmation provider unavailable; cachedEnabled="
                    + config.removeChainStartDialog);
            if (++retries <= 10) worker.postDelayed(REFRESH, 1000L);
            return;
        }
        boolean changed = config.removeChainStartDialog != loaded.removeChainStartDialog;
        config = loaded;
        persist(context, loaded);
        if (changed || retries >= 0) report("system start confirmation config origin=provider enabled="
                + loaded.removeChainStartDialog);
        retries = -1;
    }

    static boolean receive(Context context, Intent intent) {
        if (context == null || !SYSTEM_PACKAGE.equals(context.getPackageName()) || intent == null
                || !ACTION_PUBLISH.equals(intent.getAction())
                || !SYSTEM_PACKAGE.equals(intent.getPackage())) return false;
        Bundle values = intent.getExtras();
        if (values == null || values.getInt(MiscConfig.KEY_SCHEMA, -1) != MiscConfig.SCHEMA_VERSION
                || !(values.get(MiscConfig.KEY_REMOVE_CHAIN_START_DIALOG) instanceof Boolean)) return false;
        MiscConfig loaded = MiscConfig.fromBundle(values);
        config = loaded;
        persist(context, loaded);
        return true;
    }

    private static void persist(Context context, MiscConfig loaded) {
        try {
            if (!MiscConfigStore.write(cache, loaded))
                report("system start confirmation runtime cache save failed");
        } catch (RuntimeException error) {
            report("system start confirmation runtime cache unavailable");
        }
    }

    static void publish(Context module, MiscConfig snapshot) {
        if (module == null || !BuildConfig.APPLICATION_ID.equals(module.getPackageName())) return;
        module.sendBroadcast(new Intent(ACTION_PUBLISH).setPackage(SYSTEM_PACKAGE)
                .putExtras(snapshot.toBundle()));
    }
}

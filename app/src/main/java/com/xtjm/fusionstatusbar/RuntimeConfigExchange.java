package com.xtjm.fusionstatusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.BroadcastOptions;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Settings publishes durable snapshots; requests reach only its live dynamic receivers. */
final class RuntimeConfigExchange {
    static final String ACTION_PUBLISH = BuildConfig.APPLICATION_ID + ".action.PUBLISH_CONFIG";
    static final String ACTION_REQUEST = BuildConfig.APPLICATION_ID + ".action.REQUEST_CONFIG";
    static final String ACTION_REPORT = BuildConfig.APPLICATION_ID + ".action.REPORT_CONFIG";
    static final String PUBLISH_PERMISSION = BuildConfig.APPLICATION_ID + ".permission.PUBLISH_CONFIG";
    static final String SYSTEM_UI_PERMISSION = "com.android.systemui.permission.SELF";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final String TAG = "FusionStatusBar";
    private static final Map<String, Runnable> HOST_CALLBACKS = new HashMap<>();

    private RuntimeConfigExchange() { }

    static boolean registerHost(Context context, Runnable changed) {
        if (context == null) return false;
        Context application = context.getApplicationContext();
        String packageName = application == null ? context.getPackageName()
                : application.getPackageName();
        if (SYSTEM_UI_PACKAGE.equals(packageName) || SYSTEM_UI_PACKAGE.equals(context.getPackageName())) {
            return registerSystemUi(context, changed);
        }
        if (LAUNCHER_PACKAGE.equals(packageName) || LAUNCHER_PACKAGE.equals(context.getPackageName())) {
            return registerLauncher(context, changed);
        }
        return false;
    }

    static boolean registerSystemUi(Context context, Runnable changed) {
        return registerHost(context, SYSTEM_UI_PACKAGE, changed);
    }

    static boolean registerLauncher(Context context, Runnable changed) {
        return registerHost(context, LAUNCHER_PACKAGE, changed);
    }

    private static synchronized boolean registerHost(Context source, String packageName,
            Runnable changed) {
        if (source == null || changed == null) return false;
        if (HOST_CALLBACKS.containsKey(packageName)) {
            HOST_CALLBACKS.put(packageName, changed);
            return true;
        }
        try {
            Context context = hostContext(source, packageName);
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) {
                    Runnable callback;
                    synchronized (RuntimeConfigExchange.class) {
                        callback = HOST_CALLBACKS.get(packageName);
                    }
                    receiveHostConfig(context, packageName, intent, callback);
                }
            }, new IntentFilter(ACTION_PUBLISH), PUBLISH_PERMISSION, null,
                    Context.RECEIVER_EXPORTED);
            HOST_CALLBACKS.put(packageName, changed);
            request(context);
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "runtime config receiver registration failed", error);
            return false;
        }
    }

    static AutoCloseable registerSettings(Context source) {
        if (source == null || !BuildConfig.APPLICATION_ID.equals(source.getPackageName())) {
            return () -> { };
        }
        Context application = source.getApplicationContext();
        Context context = application == null ? source : application;
        BroadcastReceiver requests = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                receiveSettingsRequest(context, intent);
            }
        };
        BroadcastReceiver reports = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                receiveSettingsReport(context, intent);
            }
        };
        boolean requestsRegistered = registerSettingsReceiver(context, requests,
                ACTION_REQUEST, null);
        boolean reportsRegistered = registerSettingsReceiver(context, reports,
                ACTION_REPORT, SYSTEM_UI_PERMISSION);
        publish(context);
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (!closed.compareAndSet(false, true)) return;
            if (requestsRegistered) unregister(context, requests);
            if (reportsRegistered) unregister(context, reports);
        };
    }

    static void request(Context host) {
        if (host == null) return;
        try {
            host.sendBroadcast(new Intent(ACTION_REQUEST).setPackage(BuildConfig.APPLICATION_ID));
        } catch (Throwable error) {
            Log.w(TAG, "runtime config request failed", error);
        }
    }

    static boolean sendStatus(Context host, Bundle values) {
        if (host == null || values == null) return false;
        try {
            Intent intent = new Intent(ACTION_REPORT).setPackage(BuildConfig.APPLICATION_ID)
                    .putExtras(values);
            if (Build.VERSION.SDK_INT >= 34) {
                host.sendBroadcast(intent, null, BroadcastOptions.makeBasic()
                        .setShareIdentityEnabled(true).toBundle());
            } else {
                host.sendBroadcast(intent);
            }
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "runtime application report failed", error);
            return false;
        }
    }

    static void publish(Context module) {
        if (module == null || !BuildConfig.APPLICATION_ID.equals(module.getPackageName())) return;
        try {
            Bundle systemUi = FusionConfigStore.read(module).toBundle();
            Bundle launcher = DesktopConfigStore.read(module).toBundle();
            DesktopActionBridgeToken.appendModuleToken(module, systemUi);
            DesktopActionBridgeToken.appendModuleToken(module, launcher);
            module.sendBroadcast(new Intent(ACTION_PUBLISH).setPackage(SYSTEM_UI_PACKAGE)
                    .putExtras(systemUi));
            module.sendBroadcast(new Intent(ACTION_PUBLISH).setPackage(LAUNCHER_PACKAGE)
                    .putExtras(launcher));
        } catch (Throwable error) {
            Log.w(TAG, "runtime config publication failed", error);
        }
    }

    static boolean receiveHostConfig(Context host, String packageName, Intent intent,
            Runnable changed) {
        if (host == null || intent == null || !ACTION_PUBLISH.equals(intent.getAction())
                || !packageName.equals(intent.getPackage())) return false;
        try {
            Bundle values = intent.getExtras();
            if (values == null || !DesktopActionBridgeToken.cacheHostToken(host, values)) return false;
            boolean cached;
            if (SYSTEM_UI_PACKAGE.equals(packageName)) {
                if (!values.containsKey(FusionConfig.KEY_REVISION)) return false;
                cached = RuntimeConfigCache.write(host, FusionConfig.fromBundle(values));
            } else if (LAUNCHER_PACKAGE.equals(packageName)) {
                if (!values.containsKey("desktop_schema_version")) return false;
                cached = DesktopConfigStore.write(host, DesktopConfig.fromBundle(values));
            } else {
                return false;
            }
            if (!cached) return false;
            if (changed != null) {
                try { changed.run(); }
                catch (Throwable error) { Log.w(TAG, "runtime config callback failed", error); }
            }
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "runtime config publication rejected", error);
            return false;
        }
    }

    static boolean receiveSettingsRequest(Context module, Intent intent) {
        if (module == null || !BuildConfig.APPLICATION_ID.equals(module.getPackageName())
                || intent == null || !ACTION_REQUEST.equals(intent.getAction())
                || !BuildConfig.APPLICATION_ID.equals(intent.getPackage())) return false;
        publish(module);
        return true;
    }

    static boolean receiveSettingsReport(Context module, Intent intent) {
        if (module == null || !BuildConfig.APPLICATION_ID.equals(module.getPackageName())
                || intent == null || !ACTION_REPORT.equals(intent.getAction())
                || !BuildConfig.APPLICATION_ID.equals(intent.getPackage())) return false;
        try {
            Bundle values = intent.getExtras();
            if (values == null) return false;
            String session = values.getString(FusionActivationStatus.KEY_RUNTIME_SESSION, "");
            long revision = values.getLong(FusionConfig.KEY_REVISION, -1L);
            Bundle features = values.getBundle(FusionActivationStatus.KEY_FEATURES);
            if (session == null || session.isEmpty() || session.length() > 80 || revision < 0L
                    || features == null || features.isEmpty() || features.size() > 16) return false;
            if (revision != FusionConfigStore.read(module).revision) return false;
            for (String feature : features.keySet()) {
                String state = features.getString(feature, "");
                if (feature.length() > 64 || state == null || state.length() > 512) return false;
                String status = state.split(":", 2)[0];
                if (!FusionActivationStatus.APPLIED.equals(status)
                        && !FusionActivationStatus.WAITING.equals(status)
                        && !FusionActivationStatus.DEGRADED.equals(status)
                        && !FusionActivationStatus.DISABLED.equals(status)
                        && !FusionActivationStatus.FAILED.equals(status)) return false;
            }
            FusionActivationStatus.reportRead(module, revision, session);
            return FusionActivationStatus.recordApplied(module, values, session);
        } catch (Throwable error) {
            Log.w(TAG, "runtime application report rejected", error);
            return false;
        }
    }

    private static Context hostContext(Context source, String packageName) throws Exception {
        Context application = source.getApplicationContext();
        if (application != null && packageName.equals(application.getPackageName())) return application;
        if (packageName.equals(source.getPackageName())) return source;
        return source.createPackageContext(packageName, 0);
    }

    private static boolean registerSettingsReceiver(Context context, BroadcastReceiver receiver,
            String action, String permission) {
        try {
            context.registerReceiver(receiver, new IntentFilter(action), permission, null,
                    Context.RECEIVER_EXPORTED);
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "settings config receiver registration failed", error);
            return false;
        }
    }

    private static void unregister(Context context, BroadcastReceiver receiver) {
        try { context.unregisterReceiver(receiver); }
        catch (Throwable error) { Log.w(TAG, "settings config receiver release failed", error); }
    }
}

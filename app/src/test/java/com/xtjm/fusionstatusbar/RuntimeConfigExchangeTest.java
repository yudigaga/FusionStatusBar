package com.xtjm.fusionstatusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class RuntimeConfigExchangeTest {
    private RecordingContext module;

    @Before public void clearStorage() {
        Context app = RuntimeEnvironment.getApplication();
        module = new RecordingContext(app, BuildConfig.APPLICATION_ID);
        app.getSharedPreferences("desktop_settings_v1", Context.MODE_PRIVATE).edit().clear().commit();
        app.createDeviceProtectedStorageContext().getSharedPreferences("desktop_settings_v1",
                Context.MODE_PRIVATE).edit().clear().commit();
        app.createDeviceProtectedStorageContext().getSharedPreferences("fusion_statusbar_runtime",
                Context.MODE_PRIVATE).edit().clear().commit();
        app.createDeviceProtectedStorageContext().getSharedPreferences("fusion_activation",
                Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test public void configRequestOnlyTargetsTheLiveSettingsReceiver() {
        RecordingContext launcher = new RecordingContext(module, "com.miui.home");

        RuntimeConfigExchange.request(launcher);

        assertEquals(1, launcher.broadcasts.size());
        Intent request = launcher.broadcasts.get(0);
        assertEquals(RuntimeConfigExchange.ACTION_REQUEST, request.getAction());
        assertEquals(BuildConfig.APPLICATION_ID, request.getPackage());
        assertTrue(request.getComponent() == null);
    }

    @Test public void publicationReadsOwnStoresAndOnlyTargetsKnownHosts() {
        DesktopConfig desktop = DesktopConfig.defaults().withGesture(0,
                DesktopConfig.ACTION_NOTIFICATIONS);
        assertTrue(DesktopConfigStore.write(module, desktop));
        FusionConfig systemUi = FusionConfigStore.read(module);
        Intent request = new Intent(RuntimeConfigExchange.ACTION_REQUEST)
                .setPackage(BuildConfig.APPLICATION_ID).putExtra("target", "com.example.other");

        assertTrue(RuntimeConfigExchange.receiveSettingsRequest(module, request));

        assertEquals(2, module.broadcasts.size());
        Intent systemSnapshot = module.broadcasts.get(0);
        Intent desktopSnapshot = module.broadcasts.get(1);
        assertEquals("com.android.systemui", systemSnapshot.getPackage());
        assertEquals("com.miui.home", desktopSnapshot.getPackage());
        assertEquals(systemUi.revision,
                systemSnapshot.getLongExtra(FusionConfig.KEY_REVISION, -1L));
        assertEquals(DesktopConfig.ACTION_NOTIFICATIONS,
                DesktopConfig.fromBundle(desktopSnapshot.getExtras()).gestureAction(0));
        assertNotNull(systemSnapshot.getStringExtra(DesktopActionBridgeToken.KEY_TOKEN));
        assertEquals(systemSnapshot.getStringExtra(DesktopActionBridgeToken.KEY_TOKEN),
                desktopSnapshot.getStringExtra(DesktopActionBridgeToken.KEY_TOKEN));
    }

    @Test public void applicationReportsOnlyTargetTheLiveSettingsReceiver() {
        RecordingContext systemUi = new RecordingContext(module, "com.android.systemui");
        Bundle values = report("session", 0L, "status_bar", "applied").getExtras();

        assertTrue(RuntimeConfigExchange.sendStatus(systemUi, values));

        assertEquals(1, systemUi.broadcasts.size());
        Intent sent = systemUi.broadcasts.get(0);
        assertEquals(RuntimeConfigExchange.ACTION_REPORT, sent.getAction());
        assertEquals(BuildConfig.APPLICATION_ID, sent.getPackage());
        assertEquals("session", sent.getStringExtra(FusionActivationStatus.KEY_RUNTIME_SESSION));
        assertTrue(sent.getComponent() == null);
    }

    @Test public void hostReceiversRequireTheModuleSignaturePermission() {
        RecordingContext systemUi = new RecordingContext(module, "com.android.systemui");
        RecordingContext launcher = new RecordingContext(module, "com.miui.home");

        assertTrue(RuntimeConfigExchange.registerSystemUi(systemUi, () -> { }));
        assertTrue(RuntimeConfigExchange.registerLauncher(launcher, () -> { }));

        assertEquals(1, systemUi.registrations.size());
        assertEquals(1, launcher.registrations.size());
        for (RecordingContext host : List.of(systemUi, launcher)) {
            Registration registration = host.registrations.get(0);
            assertEquals(RuntimeConfigExchange.PUBLISH_PERMISSION, registration.permission);
            assertTrue(registration.filter.hasAction(RuntimeConfigExchange.ACTION_PUBLISH));
            assertEquals(Context.RECEIVER_EXPORTED, registration.flags);
        }
    }

    @Test public void acceptedSnapshotsAndBridgeKeysAreWrittenBeforeTheCallback() {
        RuntimeConfigExchange.publish(module);
        RecordingContext systemUi = new RecordingContext(module, "com.android.systemui");
        RecordingContext launcher = new RecordingContext(module, "com.miui.home");
        int[] callbacks = {0};

        assertTrue(RuntimeConfigExchange.receiveHostConfig(systemUi, "com.android.systemui",
                module.broadcasts.get(0), () -> {
                    assertNotNull(RuntimeConfigCache.read(systemUi));
                    assertNotNull(DesktopActionBridgeToken.cachedToken(systemUi));
                    callbacks[0]++;
                }));
        assertTrue(RuntimeConfigExchange.receiveHostConfig(launcher, "com.miui.home",
                module.broadcasts.get(1), () -> {
                    assertNotNull(DesktopConfigStore.read(launcher));
                    assertNotNull(DesktopActionBridgeToken.cachedToken(launcher));
                    callbacks[0]++;
                }));
        assertEquals(2, callbacks[0]);
        assertFalse(RuntimeConfigExchange.receiveHostConfig(launcher, "com.miui.home",
                module.broadcasts.get(0), () -> callbacks[0]++));
        assertEquals(2, callbacks[0]);
    }

    @Test public void settingsReceiversAreDynamicAndReportsRequireSystemUiPermission()
            throws Exception {
        AutoCloseable registered = RuntimeConfigExchange.registerSettings(module);

        assertEquals(2, module.registrations.size());
        assertTrue(module.registrations.get(0).filter.hasAction(RuntimeConfigExchange.ACTION_REQUEST));
        assertTrue(module.registrations.get(0).permission == null);
        assertTrue(module.registrations.get(1).filter.hasAction(RuntimeConfigExchange.ACTION_REPORT));
        assertEquals(RuntimeConfigExchange.SYSTEM_UI_PERMISSION,
                module.registrations.get(1).permission);
        registered.close();
        registered.close();
        assertEquals(2, module.unregistered.size());
    }

    @Test public void reportsFromTheSameRuntimeSessionPreserveAllFeatureStates() {
        long revision = FusionConfigStore.read(module).revision;
        assertTrue(RuntimeConfigExchange.receiveSettingsReport(module,
                report("session-one", revision, "status_bar", "applied")));
        assertTrue(RuntimeConfigExchange.receiveSettingsReport(module,
                report("session-one", revision, "control_center", "waiting:mount")));

        Bundle features = FusionActivationStatus.read(module)
                .getBundle(FusionActivationStatus.KEY_FEATURES);
        assertEquals("applied", features.getString("status_bar"));
        assertEquals("waiting:mount", features.getString("control_center"));
        assertFalse(RuntimeConfigExchange.receiveSettingsReport(module,
                report("invalid-session", revision, "status_bar", "unknown")));
        assertEquals(revision, FusionActivationStatus.read(module)
                .getLong(FusionActivationStatus.KEY_READ_REVISION));
        assertFalse(RuntimeConfigExchange.receiveSettingsReport(module,
                report("session-one", revision + 1L, "status_bar", "applied")));
        assertTrue(RuntimeConfigExchange.receiveSettingsReport(module,
                report("session-two", revision, "status_bar", "waiting:mount")));
        assertFalse(FusionActivationStatus.read(module).getBundle(FusionActivationStatus.KEY_FEATURES)
                .containsKey("control_center"));
    }

    private static Intent report(String session, long revision, String feature, String state) {
        Bundle values = new Bundle();
        values.putString(FusionActivationStatus.KEY_RUNTIME_SESSION, session);
        values.putLong(FusionConfig.KEY_REVISION, revision);
        Bundle features = new Bundle();
        features.putString(feature, state);
        values.putBundle(FusionActivationStatus.KEY_FEATURES, features);
        return new Intent(RuntimeConfigExchange.ACTION_REPORT).setPackage(BuildConfig.APPLICATION_ID)
                .putExtras(values);
    }

    private record Registration(BroadcastReceiver receiver, IntentFilter filter,
            String permission, int flags) { }

    private static final class RecordingContext extends ContextWrapper {
        private final String packageName;
        final List<Intent> broadcasts = new ArrayList<>();
        final List<Registration> registrations = new ArrayList<>();
        final List<BroadcastReceiver> unregistered = new ArrayList<>();

        RecordingContext(Context base, String packageName) {
            super(base);
            this.packageName = packageName;
        }

        @Override public String getPackageName() { return packageName; }
        @Override public Context getApplicationContext() { return this; }
        @Override public void sendBroadcast(Intent intent) { broadcasts.add(new Intent(intent)); }
        @Override public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter,
                String permission, Handler scheduler, int flags) {
            registrations.add(new Registration(receiver, filter, permission, flags));
            return null;
        }
        @Override public void unregisterReceiver(BroadcastReceiver receiver) {
            unregistered.add(receiver);
        }
    }
}

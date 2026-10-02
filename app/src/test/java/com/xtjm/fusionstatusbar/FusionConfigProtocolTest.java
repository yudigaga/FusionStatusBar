package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FusionConfigProtocolTest {
    @Test public void failedAtomicWriteKeepsBaselineAndRevisionUntilRetry() {
        MemoryFile file = new MemoryFile();
        FusionConfigStore.WriteResult first = FusionConfigStore.write(file,
                FusionConfig.defaults().withIconScale(110), FusionConfig.defaults());
        assertTrue(first.success);
        assertEquals(1L, first.revision);
        file.fail = true;
        FusionConfigStore.WriteResult failed = FusionConfigStore.write(file,
                FusionConfig.defaults().withIconScale(130), FusionConfig.defaults());
        assertFalse(failed.success);
        assertEquals(1L, failed.revision);
        assertEquals(110, FusionConfigStore.read(file, FusionConfig.defaults()).iconScale);
        file.fail = false;
        FusionConfigStore.WriteResult retry = FusionConfigStore.write(file,
                FusionConfig.defaults().withIconScale(130), FusionConfig.defaults());
        assertTrue(retry.success);
        assertEquals(2L, retry.revision);
        assertEquals(130, FusionConfigStore.read(file, FusionConfig.defaults()).iconScale);
    }

    @Test public void legacySchemaMigratesAndMalformedFieldsDoNotDiscardValidFields() throws Exception {
        FusionConfig config = FusionConfigStore.decode("{\"config_schema_version\":1,"
                + "\"battery_scale\":125,\"double_row\":\"wrong\",\"stroke_scale\":120,"
                + "\"control_center_columns\":\"wrong\",\"clock_pattern\":null}");
        assertEquals(125, config.iconScale);
        assertEquals(120, config.strokeScale);
        assertFalse(config.doubleRow);
        assertEquals(4, config.controlCenter.columns);
        assertEquals(0L, config.revision);
        assertEquals(FusionConfig.CONFIG_SCHEMA_VERSION,
                config.toBundle().getInt(FusionConfig.KEY_SCHEMA_VERSION));
    }

    @Test public void newerSchemaCannotBeOverwrittenByOlderModule() {
        MemoryFile file = new MemoryFile();
        file.bytes = "{\"config_schema_version\":999,\"icon_scale\":140}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] before = file.bytes.clone();
        assertFalse(FusionConfigStore.write(file, FusionConfig.defaults(), FusionConfig.defaults()).success);
        assertArrayEquals(before, file.bytes);
    }

    @Test public void legacyTelemetryKeysRemainAvailableToExistingMigration() throws Exception {
        FusionConfig config = FusionConfigStore.decode("{\"telemetry_enabled_0\":true,"
                + "\"telemetry_position_0\":1,\"telemetry_enabled_2\":true,"
                + "\"telemetry_position_2\":2}");
        assertTrue(config.telemetry.enabled(TelemetryConfig.TEMPERATURES));
        assertTrue(config.telemetry.enabled(TelemetryConfig.POWER_CURRENT));
    }

    @Test public void deviceProtectedSnapshotCanBeReadWhileUserLocked() {
        Context app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app.getSystemService(UserManager.class)).setUserUnlocked(false);
        assertFalse(FusionConfigStore.availableBeforeUnlock(app));
        FusionConfigStore.WriteResult result = FusionConfigStore.write(app,
                FusionConfig.defaults().withStrokeScale(135));
        assertTrue(result.success);
        assertTrue(FusionConfigStore.availableBeforeUnlock(app));
        assertEquals(135, FusionConfigStore.read(app).strokeScale);
        assertEquals(result.revision, FusionConfigStore.read(app).revision);
    }

    @Test public void malformedLegacyPreferencesAreReadWithoutClassCastCrash() {
        Context app = RuntimeEnvironment.getApplication();
        app.createDeviceProtectedStorageContext().getSharedPreferences("fusion_statusbar", 0)
                .edit().putString(FusionConfig.KEY_ICON_SCALE, "bad")
                .putInt(FusionConfig.KEY_STROKE_SCALE, 130)
                .putString(ControlCenterConfig.KEY_ENABLED, "bad").commit();
        FusionConfig result = FusionConfigStore.read(app);
        assertEquals(100, result.iconScale);
        assertEquals(130, result.strokeScale);
        assertFalse(result.controlCenter.enabled);
    }

    @Test public void continuousWritesCoalesceAndOnlyNewestCallbackIsDelivered() {
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        MemoryStorage storage = new MemoryStorage();
        FusionConfigRepository repository = new FusionConfigRepository(storage, worker, main);
        FusionConfigRepository.Session session = repository.newSession();
        List<Long> callbacks = new ArrayList<>();
        session.write(FusionConfig.defaults().withIconScale(105), result -> callbacks.add(result.revision));
        session.write(FusionConfig.defaults().withIconScale(110), result -> callbacks.add(result.revision));
        session.write(FusionConfig.defaults().withIconScale(120), result -> callbacks.add(result.revision));
        assertEquals(1, worker.tasks.size());
        worker.drain();
        assertEquals(1, storage.writes);
        assertEquals(120, storage.value.iconScale);
        session.write(FusionConfig.defaults().withIconScale(135), result -> callbacks.add(result.revision));
        main.drain();
        assertTrue(callbacks.isEmpty());
        worker.drain();
        main.drain();
        assertEquals(java.util.Collections.singletonList(2L), callbacks);
        assertEquals(135, storage.value.iconScale);
    }

    @Test public void destructionCancelsCallbackButDoesNotCancelSubmittedPersistence() {
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        MemoryStorage storage = new MemoryStorage();
        FusionConfigRepository repository = new FusionConfigRepository(storage, worker, main);
        FusionConfigRepository.Session session = repository.newSession();
        session.write(FusionConfig.defaults().withIconScale(140), result -> fail("closed callback"));
        session.close();
        worker.drain();
        main.drain();
        assertEquals(140, storage.value.iconScale);
        FusionConfigRepository.Session recreated = repository.newSession();
        List<Integer> loaded = new ArrayList<>();
        recreated.read(value -> loaded.add(value.iconScale));
        worker.drain();
        main.drain();
        assertEquals(java.util.Collections.singletonList(140), loaded);
    }

    @Test public void startupReadWaitsForConcurrentCommitAndNeverReturnsOldSnapshot() {
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        MemoryStorage storage = new MemoryStorage();
        FusionConfigRepository repository = new FusionConfigRepository(storage, worker, main);
        FusionConfigRepository.Session session = repository.newSession();
        List<Integer> loaded = new ArrayList<>();
        session.read(value -> loaded.add(value.iconScale));
        worker.drain();
        session.write(FusionConfig.defaults().withIconScale(145), result -> { });
        main.drain();
        assertTrue(loaded.isEmpty());
        worker.drain();
        main.drain();
        assertEquals(java.util.Collections.singletonList(145), loaded);
    }

    @Test public void readReceiptDoesNotPretendFeaturesAppliedAndStaleAckIsRejected() {
        Context app = RuntimeEnvironment.getApplication();
        FusionActivationStatus.reportRead(app, 1L);
        Bundle read = FusionActivationStatus.read(app);
        assertEquals(1L, read.getLong(FusionActivationStatus.KEY_READ_REVISION));
        assertEquals(-1L, read.getLong(FusionActivationStatus.KEY_APPLIED_REVISION));
        assertTrue(FusionActivationStatus.recordApplied(app, ack(1L, "control_center", "waiting:mount")));
        assertTrue(FusionActivationStatus.recordApplied(app, ack(1L, "status_bar", "applied")));
        Bundle state = FusionActivationStatus.read(app);
        assertEquals("waiting:mount", state.getBundle(FusionActivationStatus.KEY_FEATURES).getString("control_center"));
        assertEquals("applied", state.getBundle(FusionActivationStatus.KEY_FEATURES).getString("status_bar"));
        FusionActivationStatus.reportRead(app, 2L);
        assertFalse(FusionActivationStatus.recordApplied(app, ack(1L, "control_center", "applied")));
        assertTrue(FusionActivationStatus.recordApplied(app, ack(2L, "control_center", "degraded:missing_host")));
        assertFalse(FusionActivationStatus.read(app).getBundle(FusionActivationStatus.KEY_FEATURES).containsKey("status_bar"));
    }

    @Test public void providerCallerPolicyRejectsUnknownAndRestrictsRequestsToApp() {
        String own = "com.xtjm.fusionstatusbar";
        FusionConfigProvider.enforceCaller(own, own, true);
        FusionConfigProvider.enforceCaller("com.android.systemui", own, false);
        assertThrows(SecurityException.class, () -> FusionConfigProvider.enforceCaller(null, own, false));
        assertThrows(SecurityException.class, () -> FusionConfigProvider.enforceCaller("malicious.app", own, false));
        assertThrows(SecurityException.class, () -> FusionConfigProvider.enforceCaller("com.android.systemui", own, true));
    }

    @Test public void simultaneousReadersFromDifferentSessionsBothCompleteFromOneRead() {
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        MemoryStorage storage = new MemoryStorage();
        FusionConfigRepository repository = new FusionConfigRepository(storage, worker, main);
        List<String> callbacks = new ArrayList<>();
        repository.newSession().read(value -> callbacks.add("first:" + value.iconScale));
        repository.newSession().read(value -> callbacks.add("second:" + value.iconScale));
        assertEquals(1, worker.tasks.size());
        worker.drain();
        assertEquals(1, storage.reads);
        assertEquals(1, main.tasks.size());
        main.drain();
        assertEquals(java.util.Arrays.asList("first:100", "second:100"), callbacks);
    }

    @Test public void replacingAnotherSessionWriteReportsSupersededInsteadOfHanging() {
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        MemoryStorage storage = new MemoryStorage();
        FusionConfigRepository repository = new FusionConfigRepository(storage, worker, main);
        List<FusionConfigStore.WriteResult> first = new ArrayList<>();
        List<FusionConfigStore.WriteResult> second = new ArrayList<>();
        repository.newSession().write(FusionConfig.defaults().withIconScale(110), first::add);
        repository.newSession().write(FusionConfig.defaults().withIconScale(140), second::add);
        worker.drain(); main.drain();
        assertEquals(1, first.size());
        assertFalse(first.get(0).success);
        assertEquals(FusionConfigRepository.SUPERSEDED, first.get(0).error);
        assertEquals(1, second.size());
        assertTrue(second.get(0).success);
        assertEquals(140, storage.value.iconScale);
        assertEquals(1, storage.writes);
    }

    @Test public void olderRunningWriteDoesNotEraseSupersededResultForNewerRequest() {
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        final Runnable[] whileWriting = {() -> { }};
        MemoryStorage storage = new MemoryStorage() {
            @Override public FusionConfigStore.WriteResult write(FusionConfig next) {
                if (writes == 0) whileWriting[0].run();
                return super.write(next);
            }
        };
        FusionConfigRepository repository = new FusionConfigRepository(storage, worker, main);
        FusionConfigRepository.Session first = repository.newSession();
        FusionConfigRepository.Session second = repository.newSession();
        List<String> results = new ArrayList<>();
        whileWriting[0] = () -> {
            first.write(FusionConfig.defaults().withIconScale(125), value -> results.add(value.error));
            second.write(FusionConfig.defaults().withIconScale(135), value -> results.add("second:" + value.success));
        };
        first.write(FusionConfig.defaults().withIconScale(110), value -> fail("old first callback"));
        worker.drain(); main.drain();
        assertEquals(java.util.Arrays.asList(FusionConfigRepository.SUPERSEDED, "second:true"), results);
        assertEquals(135, storage.value.iconScale);
    }

    @Test public void slowMainThreadKeepsSingleDeliveryTaskAndSubscriberCountIsBounded() {
        QueuedExecutor worker = new QueuedExecutor();
        QueuedExecutor main = new QueuedExecutor();
        FusionConfigRepository repository = new FusionConfigRepository(new MemoryStorage(), worker, main);
        FusionConfigRepository.Session session = repository.newSession();
        List<Long> delivered = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            session.write(FusionConfig.defaults(), result -> delivered.add(result.revision));
            worker.drain();
        }
        assertEquals(1, main.tasks.size());
        main.drain();
        assertEquals(java.util.Collections.singletonList(100L), delivered);
        for (int i = 1; i < FusionConfigRepository.MAX_SESSIONS; i++) repository.newSession();
        assertThrows(IllegalStateException.class, repository::newSession);
        session.close();
        assertNotNull(repository.newSession());
    }

    @Test public void newSystemUiInstanceClearsOldFeaturesEvenAtSameRevision() {
        Context app = RuntimeEnvironment.getApplication();
        FusionActivationStatus.reportRead(app, 7L, "old-process-instance");
        assertTrue(FusionActivationStatus.recordApplied(app,
                ack(7L, "control_center", "applied"), "old-process-instance"));
        FusionActivationStatus.reportRead(app, 7L, "new-process-instance");
        Bundle state = FusionActivationStatus.read(app);
        assertEquals(7L, state.getLong(FusionActivationStatus.KEY_READ_REVISION));
        assertEquals(-1L, state.getLong(FusionActivationStatus.KEY_APPLIED_REVISION));
        assertTrue(state.getBundle(FusionActivationStatus.KEY_FEATURES).isEmpty());
        assertFalse(FusionActivationStatus.recordApplied(app,
                ack(7L, "control_center", "applied"), "old-process-instance"));
        assertTrue(FusionActivationStatus.recordApplied(app,
                ack(7L, "control_center", "waiting:mount"), "new-process-instance"));
    }

    @Test public void runtimeIdentityIncludesActualBinderProcessAndNewInstanceToken() {
        Bundle one = new Bundle();
        one.putString(FusionActivationStatus.KEY_RUNTIME_SESSION, "one");
        Bundle two = new Bundle();
        two.putString(FusionActivationStatus.KEY_RUNTIME_SESSION, "two");
        assertNotEquals(FusionActivationStatus.callerSession(1000, 500, one),
                FusionActivationStatus.callerSession(1000, 501, one));
        assertNotEquals(FusionActivationStatus.callerSession(1000, 500, one),
                FusionActivationStatus.callerSession(1000, 500, two));
    }

    private static Bundle ack(long revision, String feature, String state) {
        Bundle values = new Bundle();
        values.putLong(FusionConfig.KEY_REVISION, revision);
        Bundle features = new Bundle();
        features.putString(feature, state);
        values.putBundle(FusionActivationStatus.KEY_FEATURES, features);
        return values;
    }

    private static final class MemoryFile implements FusionConfigStore.SnapshotFile {
        byte[] bytes;
        boolean fail;
        @Override public byte[] read() throws IOException {
            if (bytes == null) throw new FileNotFoundException();
            return bytes.clone();
        }
        @Override public void write(byte[] next) throws IOException {
            if (fail) throw new IOException("injected_disk_failure");
            bytes = next.clone();
        }
    }

    private static class MemoryStorage implements FusionConfigRepository.Storage {
        FusionConfig value = FusionConfig.defaults();
        int writes;
        int reads;
        @Override public FusionConfig read() { reads++; return value; }
        @Override public FusionConfigStore.WriteResult write(FusionConfig next) {
            value = next.withRevision(++writes);
            return new FusionConfigStore.WriteResult(true, value, "");
        }
    }

    private static final class QueuedExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable task) { tasks.add(task); }
        void drain() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }
}

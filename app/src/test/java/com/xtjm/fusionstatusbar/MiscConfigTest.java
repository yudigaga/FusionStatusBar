package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = AndroidAtomicFileShadow.class)
public class MiscConfigTest {
    @Test public void featureDefaultsOffAndSurvivesBundleRoundTrip() {
        assertFalse(MiscConfig.defaults().removeChainStartDialog);
        assertFalse(MiscConfig.fromBundle(null).removeChainStartDialog);
        assertFalse(MiscConfig.fromBundle(new Bundle()).removeChainStartDialog);
        MiscConfig enabled = MiscConfig.defaults().withRemoveChainStartDialog(true);
        assertTrue(MiscConfig.fromBundle(enabled.toBundle()).removeChainStartDialog);
        Bundle malformed = new Bundle();
        malformed.putString(MiscConfig.KEY_REMOVE_CHAIN_START_DIALOG, "true");
        assertFalse(MiscConfig.fromBundle(malformed).removeChainStartDialog);
    }

    @Test public void appAndProviderReadTheSameDeviceProtectedSnapshotBeforeUnlock() {
        Context app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app.getSystemService(UserManager.class)).setUserUnlocked(false);
        assertTrue(MiscConfig.defaults().withRemoveChainStartDialog(true).save(app));
        assertTrue(MiscConfig.read(app).removeChainStartDialog);
        assertTrue(MiscConfigStore.read(app.createDeviceProtectedStorageContext())
                .removeChainStartDialog);
        assertTrue(MiscConfig.defaults().save(app));
        assertFalse(MiscConfigStore.read(app).removeChainStartDialog);
    }

    @Test public void failedPersistencePreservesCommittedValue() {
        MemoryFile file = new MemoryFile();
        assertTrue(MiscConfigStore.write(file,
                MiscConfig.defaults().withRemoveChainStartDialog(true)));
        byte[] committed = file.bytes.clone();
        file.failWrite = true;
        assertFalse(MiscConfigStore.write(file, MiscConfig.defaults()));
        assertArrayEquals(committed, file.bytes);
        assertTrue(MiscConfigStore.read(file).removeChainStartDialog);
    }

    @Test public void malformedAndFutureConfigurationsKeepTheFeatureOff() {
        MemoryFile file = new MemoryFile();
        file.bytes = "{\"remove_chain_start_dialog\":\"true\"}"
                .getBytes(StandardCharsets.UTF_8);
        assertFalse(MiscConfigStore.read(file).removeChainStartDialog);
        file.bytes = "{\"misc_schema_version\":999,\"remove_chain_start_dialog\":true}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] future = file.bytes.clone();
        assertFalse(MiscConfigStore.read(file).removeChainStartDialog);
        assertFalse(MiscConfigStore.write(file, MiscConfig.defaults()));
        assertArrayEquals(future, file.bytes);
        file.bytes = new byte[4097];
        assertFalse(MiscConfigStore.read(file).removeChainStartDialog);
    }

    @Test public void providerAllowsOnlyOwnerWritesAndSystemFrameworkReads() {
        String own = BuildConfig.APPLICATION_ID;
        FusionConfigProvider.enforceMiscReadCaller(own, own);
        FusionConfigProvider.enforceMiscReadCaller("android", own);
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceMiscReadCaller("com.miui.securitycenter", own));
        FusionConfigProvider.enforceCaller(own, own, true);
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceMiscReadCaller(null, own));
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceCaller("android", own, true));
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceMiscReadCaller("com.android.systemui", own));
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceMiscReadCaller("malicious.app", own));
        assertThrows(SecurityException.class,
                () -> FusionConfigProvider.enforceCaller("com.miui.securitycenter", own, true));
    }

    private static final class MemoryFile implements FusionConfigStore.SnapshotFile {
        byte[] bytes;
        boolean failWrite;
        @Override public byte[] read() throws IOException {
            if (bytes == null) throw new FileNotFoundException();
            return bytes.clone();
        }
        @Override public void write(byte[] next) throws IOException {
            if (failWrite) throw new IOException("injected_disk_failure");
            bytes = next.clone();
        }
    }
}

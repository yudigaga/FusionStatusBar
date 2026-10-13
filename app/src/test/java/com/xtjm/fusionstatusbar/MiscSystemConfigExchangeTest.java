package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = AndroidAtomicFileShadow.class)
public class MiscSystemConfigExchangeTest {
    private FusionConfigStore.SnapshotFile originalCache;
    private final MemoryFile snapshot = new MemoryFile();

    @Before public void useMemoryCache() throws Exception {
        Field field = MiscSystemConfigExchange.class.getDeclaredField("cache");
        field.setAccessible(true);
        originalCache = (FusionConfigStore.SnapshotFile) field.get(null);
        field.set(null, snapshot);
    }

    @After public void reset() throws Exception {
        Field field = MiscSystemConfigExchange.class.getDeclaredField("config");
        field.setAccessible(true);
        field.set(null, MiscConfig.defaults());
        Field cache = MiscSystemConfigExchange.class.getDeclaredField("cache");
        cache.setAccessible(true);
        cache.set(null, originalCache);
    }

    @Test public void completeUpdatePersistsAndDisablingReplacesTheSystemSnapshot() {
        Context system = systemContext();
        assertTrue(MiscSystemConfigExchange.receive(system, update(true)));
        assertTrue(MiscSystemConfigExchange.current().removeChainStartDialog);
        assertTrue(MiscConfigStore.read(snapshot).removeChainStartDialog);
        assertTrue(MiscSystemConfigExchange.receive(system, update(false)));
        assertFalse(MiscSystemConfigExchange.current().removeChainStartDialog);
        assertFalse(MiscConfigStore.read(snapshot).removeChainStartDialog);
    }

    @Test public void malformedFutureAndWrongDestinationUpdatesPreserveTheSnapshot() {
        Context system = systemContext();
        assertTrue(MiscSystemConfigExchange.receive(system, update(true)));
        assertFalse(MiscSystemConfigExchange.receive(system, update(false).setPackage("other.application")));
        assertFalse(MiscSystemConfigExchange.receive(system, update(false).setAction("other.action")));
        Intent malformed = new Intent(MiscSystemConfigExchange.ACTION_PUBLISH).setPackage("android");
        assertFalse(MiscSystemConfigExchange.receive(system, malformed));
        assertFalse(MiscSystemConfigExchange.receive(system,
                update(false).putExtra(MiscConfig.KEY_SCHEMA, 999)));
        assertFalse(MiscSystemConfigExchange.receive(system,
                update(false).putExtra(MiscConfig.KEY_REMOVE_CHAIN_START_DIALOG, "false")));
        assertFalse(MiscSystemConfigExchange.receive(RuntimeEnvironment.getApplication(), update(false)));
        assertTrue(MiscSystemConfigExchange.current().removeChainStartDialog);
    }

    @Test public void publicationTargetsOnlyFrameworkAndRequiresTheModuleIdentity() {
        int[] sends = {0};
        Context module = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public String getPackageName() { return BuildConfig.APPLICATION_ID; }
            @Override public void sendBroadcast(Intent intent) {
                assertEquals("android", intent.getPackage());
                assertEquals(MiscSystemConfigExchange.ACTION_PUBLISH, intent.getAction());
                assertTrue(MiscConfig.fromBundle(intent.getExtras()).removeChainStartDialog);
                sends[0]++;
            }
        };
        MiscSystemConfigExchange.publish(module, MiscConfig.defaults().withRemoveChainStartDialog(true));
        MiscSystemConfigExchange.publish(systemContext(), MiscConfig.defaults());
        assertEquals(1, sends[0]);
    }

    private static Context systemContext() {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public String getPackageName() { return "android"; }
        };
    }

    private static Intent update(boolean enabled) {
        return new Intent(MiscSystemConfigExchange.ACTION_PUBLISH).setPackage("android")
                .putExtras(MiscConfig.defaults().withRemoveChainStartDialog(enabled).toBundle());
    }

    private static class MemoryFile implements FusionConfigStore.SnapshotFile {
        byte[] bytes;
        @Override public byte[] read() throws IOException {
            if (bytes == null) throw new FileNotFoundException();
            return bytes.clone();
        }
        @Override public void write(byte[] data) { bytes = data.clone(); }
    }
}

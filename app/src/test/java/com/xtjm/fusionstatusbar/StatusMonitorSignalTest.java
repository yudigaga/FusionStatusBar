package com.xtjm.fusionstatusbar;

import android.telephony.SubscriptionManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class StatusMonitorSignalTest {
    private static final String[] FIELDS = {"subscriptionOrder", "SUBSCRIPTION_LEVELS",
            "wifiConnected", "wifiLevel", "renderedWifiLevel", "mobileLevel", "showWifi",
            "networkLabel", "signalLevel", "signalLevel2", "dirty"};
    private final Map<String, Object> original = new HashMap<>();

    @Before public void saveState() throws Exception {
        synchronized (lock()) {
            for (String name : FIELDS) original.put(name, copy(get(name)));
        }
    }

    @After public void restoreState() throws Exception {
        synchronized (lock()) {
            for (String name : FIELDS) set(name, original.get(name));
        }
    }

    @Test public void connectedWifiDoesNotReplaceEitherSimSignalWithWifiStrength() throws Exception {
        synchronized (lock()) {
            set("subscriptionOrder", new ArrayList<>(List.of(101, 202)));
            set("SUBSCRIPTION_LEVELS", new HashMap<>(Map.of(101, 1, 202, 3)));
            set("wifiConnected", true);
            set("wifiLevel", 4);
            set("renderedWifiLevel", 0);
            set("mobileLevel", 0);
            set("dirty", false);
            assign();
            assertTrue("A Wi-Fi RSSI change publishes a snapshot", (Boolean) get("dirty"));
        }

        StatusMonitor.Snapshot snapshot = StatusMonitor.snapshot();
        assertTrue(snapshot.showWifi);
        assertEquals(4, snapshot.wifiLevel);
        assertEquals("Primary SIM signal remains cellular", 1, snapshot.signalLevel);
        assertEquals("Secondary SIM signal remains cellular", 3, snapshot.signalLevel2);

        synchronized (lock()) {
            set("dirty", false);
            set("wifiLevel", 1);
            assign();
            assertTrue("Wi-Fi-only strength changes still publish", (Boolean) get("dirty"));
        }
        snapshot = StatusMonitor.snapshot();
        assertEquals(1, snapshot.wifiLevel);
        assertEquals(1, snapshot.signalLevel);
        assertEquals(3, snapshot.signalLevel2);
    }

    @Test public void wifiStrengthIsNotUsedAsTheMobileFallbackWithoutSubscriptionIds() throws Exception {
        synchronized (lock()) {
            set("subscriptionOrder", new ArrayList<>(List.of(SubscriptionManager.INVALID_SUBSCRIPTION_ID)));
            set("SUBSCRIPTION_LEVELS", new HashMap<Integer, Integer>());
            set("wifiConnected", true);
            set("wifiLevel", 4);
            set("renderedWifiLevel", 0);
            set("mobileLevel", 2);
            assign();
        }

        StatusMonitor.Snapshot snapshot = StatusMonitor.snapshot();
        assertTrue(snapshot.showWifi);
        assertEquals(4, snapshot.wifiLevel);
        assertEquals("Fallback remains the mobile signal, not Wi-Fi RSSI", 2, snapshot.signalLevel);
        assertEquals(0, snapshot.signalLevel2);
    }

    @Test public void miuiLevelTakesPriorityOverGenericAndroidLevel() {
        assertEquals(3, StatusMonitor.mobileLevelFromSignalStrength(new MiuiSignal(3, 1)));
        assertEquals(0, StatusMonitor.mobileLevelFromSignalStrength(new GenericSignal(1)));
    }

    public static final class MiuiSignal {
        private final int miuiLevel;
        private final int genericLevel;

        MiuiSignal(int miuiLevel, int genericLevel) {
            this.miuiLevel = miuiLevel;
            this.genericLevel = genericLevel;
        }

        public int getMiuiLevel() { return miuiLevel; }
        public int getLevel() { return genericLevel; }
    }

    public static final class GenericSignal {
        private final int level;

        GenericSignal(int level) { this.level = level; }
        public int getLevel() { return level; }
    }

    private static Object lock() throws Exception { return get("LOCK"); }

    private static Object get(String name) throws Exception {
        Field field = StatusMonitor.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void set(String name, Object value) throws Exception {
        Field field = StatusMonitor.class.getDeclaredField(name);
        field.setAccessible(true);
        if (Modifier.isFinal(field.getModifiers()) && field.get(null) instanceof Map<?, ?> target
                && value instanceof Map<?, ?> source) {
            @SuppressWarnings("unchecked") Map<Object, Object> mutable = (Map<Object, Object>) target;
            mutable.clear();
            mutable.putAll(source);
            return;
        }
        field.set(null, value);
    }

    private static void assign() throws Exception {
        Method method = StatusMonitor.class.getDeclaredMethod("assignLocked");
        method.setAccessible(true);
        method.invoke(null);
    }

    private static Object copy(Object value) {
        if (value instanceof List<?> list) return new ArrayList<>(list);
        if (value instanceof Map<?, ?> map) return new HashMap<>(map);
        return value;
    }
}

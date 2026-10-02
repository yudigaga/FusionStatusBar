package com.xtjm.fusionstatusbar;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/** Process-wide battery, transport and signal state shared by every fused icon. */
public final class StatusMonitor {
    public interface Listener {
        void onStatus();
    }

    public static final class Snapshot {
        public final int batteryPercent;
        public final boolean showWifi;
        public final String networkLabel;
        public final int signalLevel;
        public final int signalLevel2;
        public final boolean charging;

        Snapshot(int batteryPercent, boolean showWifi, String networkLabel, int signalLevel) {
            this(batteryPercent, showWifi, networkLabel, signalLevel, 0, false);
        }

        Snapshot(int batteryPercent, boolean showWifi, String networkLabel,
                int signalLevel, int signalLevel2) {
            this(batteryPercent, showWifi, networkLabel, signalLevel, signalLevel2, false);
        }

        Snapshot(int batteryPercent, boolean showWifi, String networkLabel,
                int signalLevel, int signalLevel2, boolean charging) {
            this.batteryPercent = batteryPercent;
            this.showWifi = showWifi;
            this.networkLabel = networkLabel == null ? "" : networkLabel;
            this.signalLevel = signalLevel;
            this.signalLevel2 = signalLevel2;
            this.charging = charging;
        }
    }

    private static final String TAG = "FusionStatusBar";
    private static final Object LOCK = new Object();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Map<Network, Integer> WIFI_LEVELS = new HashMap<>();
    private static final Map<Network, Boolean> MOBILE_NETWORKS = new HashMap<>();
    private static final Map<Integer, TelephonyManager> TELEPHONY_MANAGERS = new HashMap<>();
    private static final Map<Integer, PhoneCallback> PHONE_CALLBACKS = new HashMap<>();
    private static final Map<Integer, Integer> SUBSCRIPTION_LEVELS = new HashMap<>();

    private static Handler mainHandler;
    private static Executor mainExecutor;
    private static Context appContext;
    private static WifiManager wifiManager;
    private static List<Integer> subscriptionOrder = new ArrayList<>();
    private static int defaultDataSubscription = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private static boolean started;
    private static boolean transportsRegistered;
    private static boolean batteryRegistered;
    private static boolean dirty;
    private static int batteryPercent = -1;
    private static boolean charging;
    private static boolean showWifi;
    private static String networkLabel = "";
    private static int signalLevel;
    private static boolean wifiConnected;
    private static boolean mobileConnected;
    private static int wifiLevel;
    private static int mobileLevel;
    private static int signalLevel2;
    private static String mobileLabel = "";

    private StatusMonitor() {
    }

    public static void start(Context context) {
        if (context == null) {
            return;
        }
        Context resolved = context.getApplicationContext();
        if (resolved == null) {
            resolved = context;
        }
        final Context app = resolved;
        boolean registerNow = false;
        synchronized (LOCK) {
            if (mainHandler == null) {
                mainHandler = new Handler(Looper.getMainLooper());
                mainExecutor = command -> {
                    if (command != null && mainHandler != null) {
                        mainHandler.post(command);
                    }
                };
            }
            if (!started) {
                started = true;
                appContext = app;
                registerNow = true;
            }
        }
        if (!registerNow) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            register(app);
        } else {
            mainHandler.post(() -> register(app));
        }
    }

    public static void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        synchronized (LOCK) {
            if (!LISTENERS.contains(listener)) {
                LISTENERS.add(listener);
            }
        }
    }

    public static void removeListener(Listener listener) {
        if (listener == null) {
            return;
        }
        synchronized (LOCK) {
            LISTENERS.remove(listener);
        }
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(batteryPercent, showWifi, networkLabel, signalLevel,
                    signalLevel2, charging);
        }
    }

    private static void register(Context context) {
        registerBattery(context);
        registerTransports(context);
        registerTelephony(context);
    }

    @SuppressLint("MissingPermission")
    private static void registerBattery(Context context) {
        synchronized (LOCK) {
            if (batteryRegistered) {
                return;
            }
            batteryRegistered = true;
        }
        try {
            Intent sticky = context.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context receiverContext, Intent intent) {
                            applyBattery(intent);
                        }
                    },
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    null,
                    mainHandler,
                    Context.RECEIVER_NOT_EXPORTED);
            applyBattery(sticky);
        } catch (Throwable error) {
            Log.e(TAG, "battery monitor failed", error);
        }
    }

    @SuppressLint("MissingPermission")
    private static void registerTransports(Context context) {
        synchronized (LOCK) {
            if (transportsRegistered) {
                return;
            }
            transportsRegistered = true;
        }
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        wifiManager = context.getSystemService(WifiManager.class);
        if (connectivity == null) {
            Log.e(TAG, "connectivity service missing");
            return;
        }
        try {
            NetworkRequest wifi = new NetworkRequest.Builder()
                    .clearCapabilities()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            connectivity.registerNetworkCallback(wifi, new TransportCallback(true), mainHandler);
        } catch (Throwable error) {
            Log.e(TAG, "wifi monitor failed", error);
        }
        try {
            NetworkRequest mobile = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivity.registerNetworkCallback(mobile, new TransportCallback(false), mainHandler);
        } catch (Throwable error) {
            Log.e(TAG, "mobile monitor failed", error);
        }
    }

    @SuppressLint("MissingPermission")
    private static void registerTelephony(Context context) {
        TelephonyManager base = context.getSystemService(TelephonyManager.class);
        if (base == null) {
            Log.w(TAG, "telephony service missing");
        return;
        }
        final SubscriptionManager subscriptions = context.getSystemService(SubscriptionManager.class);
        if (subscriptions != null) {
            try {
                subscriptions.addOnSubscriptionsChangedListener(
                        mainExecutor, new SubscriptionManager.OnSubscriptionsChangedListener() {
                            @Override
                            public void onSubscriptionsChanged() {
                                bindTelephony(base, subscriptions);
                            }
                        });
            } catch (SecurityException error) {
                Log.w(TAG, "subscription listener denied", error);
            } catch (Throwable error) {
                Log.w(TAG, "subscription listener failed", error);
            }
        }
        bindTelephony(base, subscriptions);
    }

    @SuppressLint("MissingPermission")
    private static void bindTelephony(TelephonyManager base, SubscriptionManager subscriptions) {
        if (base == null) {
            return;
        }
        int defaultSubscription = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        try {
            defaultSubscription = SubscriptionManager.getDefaultDataSubscriptionId();
        } catch (SecurityException error) {
            Log.w(TAG, "default data subscription unavailable", error);
        } catch (Throwable error) {
            Log.w(TAG, "default data subscription failed", error);
        }

        List<Integer> targets = readSubscriptionOrder(subscriptions, defaultSubscription);
        if (defaultSubscription == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && !targets.isEmpty()
                && targets.get(0) != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            defaultSubscription = targets.get(0);
        }

        synchronized (LOCK) {
            defaultDataSubscription = defaultSubscription;
            subscriptionOrder = new ArrayList<>(targets);
            for (Integer subscription : new ArrayList<>(PHONE_CALLBACKS.keySet())) {
                if (targets.contains(subscription)) {
                    continue;
                }
                PhoneCallback callback = PHONE_CALLBACKS.remove(subscription);
                TelephonyManager telephony = TELEPHONY_MANAGERS.remove(subscription);
                SUBSCRIPTION_LEVELS.remove(subscription);
                if (callback != null) {
                    callback.active = false;
                }
                if (telephony != null && callback != null) {
                    try {
                        telephony.unregisterTelephonyCallback(callback);
                    } catch (Throwable error) {
                        Log.w(TAG, "telephony callback unregister failed", error);
                    }
                }
            }
            for (Integer subscription : targets) {
                SUBSCRIPTION_LEVELS.putIfAbsent(subscription, 0);
            }
            for (Integer subscription : new ArrayList<>(SUBSCRIPTION_LEVELS.keySet())) {
                if (!targets.contains(subscription)) {
                    SUBSCRIPTION_LEVELS.remove(subscription);
                }
            }
            assignLocked();
        }
        publish();

        for (Integer subscription : targets) {
            ensureTelephony(base, subscription);
        }
    }

    @SuppressLint("MissingPermission")
    private static List<Integer> readSubscriptionOrder(
            SubscriptionManager subscriptions, int fallback) {
        List<Integer> result = new ArrayList<>();
        if (subscriptions != null) {
            try {
                List<SubscriptionInfo> active = subscriptions.getActiveSubscriptionInfoList();
                if (active != null) {
                    List<SubscriptionInfo> ordered = new ArrayList<>(active);
                    ordered.removeIf(info -> info == null);
                    ordered.sort((left, right) -> {
                        int leftSlot = normalizedSlot(left.getSimSlotIndex());
                        int rightSlot = normalizedSlot(right.getSimSlotIndex());
                        if (leftSlot != rightSlot) {
                            return Integer.compare(leftSlot, rightSlot);
                        }
                        return Integer.compare(
                                left.getSubscriptionId(), right.getSubscriptionId());
                    });
                    for (SubscriptionInfo info : ordered) {
                        if (info == null) {
                            continue;
                        }
                        int id = info.getSubscriptionId();
                        if (!result.contains(id)) {
                            result.add(id);
                        }
                    }
                }
            } catch (SecurityException error) {
                Log.w(TAG, "active subscriptions unavailable", error);
            } catch (Throwable error) {
                Log.w(TAG, "active subscriptions failed", error);
            }
        }
        if (result.isEmpty()) {
            result.add(fallback);
        }
        return result;
    }

    private static int normalizedSlot(int slot) {
        return slot < 0 ? Integer.MAX_VALUE : slot;
    }

    @SuppressLint("MissingPermission")
    private static void ensureTelephony(TelephonyManager base, int subscription) {
        TelephonyManager next = base;
        if (subscription != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            try {
                next = base.createForSubscriptionId(subscription);
            } catch (Throwable error) {
                Log.w(TAG, "subscription telephony manager failed", error);
            }
        }
        if (next == null) {
            return;
        }

        PhoneCallback callback;
        boolean register;
        synchronized (LOCK) {
            callback = PHONE_CALLBACKS.get(subscription);
            TelephonyManager current = TELEPHONY_MANAGERS.get(subscription);
            if (callback != null && callback.active && current != null) {
                next = current;
                register = false;
            } else {
                if (callback != null) {
                    callback.active = false;
                }
                callback = new PhoneCallback(subscription);
                PHONE_CALLBACKS.put(subscription, callback);
                TELEPHONY_MANAGERS.put(subscription, next);
                register = true;
            }
        }
        if (register) {
            try {
                next.registerTelephonyCallback(mainExecutor, callback);
            } catch (SecurityException error) {
                callback.active = false;
                Log.w(TAG, "telephony callback denied", error);
            } catch (Throwable error) {
                callback.active = false;
                Log.e(TAG, "telephony callback failed", error);
            }
        }
        if (callback.active) {
            try {
                primeTelephony(next, subscription);
            } catch (Throwable error) {
                Log.w(TAG, "telephony snapshot failed", error);
            }
        }
        if (register && !callback.active) {
            synchronized (LOCK) {
                if (PHONE_CALLBACKS.get(subscription) == callback) {
                    PHONE_CALLBACKS.remove(subscription);
                    TELEPHONY_MANAGERS.remove(subscription);
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private static void primeTelephony(TelephonyManager telephony, int subscription) {
        try {
            SignalStrength strength = telephony.getSignalStrength();
            if (strength != null) {
                setMobileLevel(subscription, strength.getLevel());
            }
            setMobileLabel(subscription, NetworkLabel.fromNetworkType(telephony.getDataNetworkType()));
        } catch (SecurityException error) {
            Log.w(TAG, "telephony snapshot denied", error);
        } catch (Throwable error) {
            Log.w(TAG, "telephony snapshot failed", error);
        }
    }

    private static void applyBattery(Intent intent) {
        if (intent == null) {
            return;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) {
            return;
        }
        int percent = Math.round(level * 100f / scale);
        if (percent < 0) {
            percent = 0;
        } else if (percent > 100) {
            percent = 100;
        }
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING;
        synchronized (LOCK) {
            if (batteryPercent == percent && charging == isCharging) {
                return;
            }
            batteryPercent = percent;
            charging = isCharging;
            dirty = true;
        }
        publish();
    }

    private static void setWifiNetwork(Network network, boolean connected, int level) {
        synchronized (LOCK) {
            if (connected) {
                if (level >= 0) {
                    WIFI_LEVELS.put(network, clamp(level));
                } else {
                    WIFI_LEVELS.putIfAbsent(network, 0);
                }
            } else {
                WIFI_LEVELS.remove(network);
            }
            wifiConnected = !WIFI_LEVELS.isEmpty();
            wifiLevel = maxLevel(WIFI_LEVELS);
            assignLocked();
        }
        publish();
    }

    private static void setMobileNetwork(Network network, boolean connected) {
        synchronized (LOCK) {
            if (connected) {
                MOBILE_NETWORKS.put(network, Boolean.TRUE);
            } else {
                MOBILE_NETWORKS.remove(network);
            }
            mobileConnected = !MOBILE_NETWORKS.isEmpty();
            assignLocked();
        }
        publish();
    }

    private static void setMobileLevel(int subscription, int level) {
        synchronized (LOCK) {
            if (!subscriptionOrder.contains(subscription)) {
                return;
            }
            if (subscription == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                mobileLevel = clamp(level);
            } else {
                SUBSCRIPTION_LEVELS.put(subscription, clamp(level));
            }
            assignLocked();
        }
        publish();
    }

    private static void setMobileLabel(int subscription, String label) {
        synchronized (LOCK) {
            if (subscription != defaultDataSubscription) {
                return;
            }
            mobileLabel = label == null ? "" : label;
            assignLocked();
        }
        publish();
    }

    private static void assignLocked() {
        boolean nextWifi;
        String nextLabel;
        if (wifiConnected) {
            nextWifi = true;
            nextLabel = "";
        } else if (mobileConnected) {
            nextWifi = false;
            nextLabel = mobileLabel == null ? "" : mobileLabel;
        } else {
            nextWifi = false;
            nextLabel = "";
        }

        int nextSignalLevel = signalLevelAtLocked(0);
        int nextSignalLevel2 = signalLevelAtLocked(1);
        boolean hasRealSubscription = false;
        for (Integer subscription : subscriptionOrder) {
            if (subscription != null
                    && subscription != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                hasRealSubscription = true;
                break;
            }
        }
        if (!hasRealSubscription) {
            nextSignalLevel = wifiConnected ? clamp(wifiLevel) : clamp(mobileLevel);
            nextSignalLevel2 = 0;
        }

        if (nextWifi == showWifi && nextLabel.equals(networkLabel)
                && nextSignalLevel == signalLevel && nextSignalLevel2 == signalLevel2) {
            return;
        }
        showWifi = nextWifi;
        networkLabel = nextLabel;
        signalLevel = nextSignalLevel;
        signalLevel2 = nextSignalLevel2;
        dirty = true;
    }

    private static int signalLevelAtLocked(int row) {
        if (row < 0 || row >= subscriptionOrder.size()) {
            return 0;
        }
        Integer subscription = subscriptionOrder.get(row);
        Integer level = SUBSCRIPTION_LEVELS.get(subscription);
        return level == null ? 0 : clamp(level);
    }

    private static void publish() {
        final Listener[] listeners;
        synchronized (LOCK) {
            if (!dirty) {
                return;
            }
            dirty = false;
            listeners = LISTENERS.toArray(new Listener[0]);
        }
        if (listeners.length == 0 || mainHandler == null) {
            return;
        }
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                try {
                    listener.onStatus();
                } catch (Throwable error) {
                    Log.e(TAG, "status listener failed", error);
                }
            }
        });
    }

    private static int readWifiLevel(NetworkCapabilities capabilities) {
        if (capabilities == null) {
            return 0;
        }
        try {
            if (capabilities.getTransportInfo() instanceof WifiInfo info) {
                int rssi = info.getRssi();
                if (rssi > -127) {
                    return levelFromRssi(rssi);
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "wifi rssi unavailable", error);
        }
        int strength = capabilities.getSignalStrength();
        if (strength != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED && strength < 0) {
            return levelFromRssi(strength);
        }
        return -1;
    }

    private static int levelFromRssi(int rssi) {
        try {
            if (wifiManager != null) {
                return clamp(wifiManager.calculateSignalLevel(rssi));
            }
        } catch (Throwable error) {
            Log.w(TAG, "wifi signal level failed", error);
        }
        try {
            return clamp(WifiManager.calculateSignalLevel(rssi, 5));
        } catch (Throwable error) {
            Log.w(TAG, "wifi signal level fallback failed", error);
        }
        if (rssi <= -88) {
            return 0;
        }
        if (rssi <= -77) {
            return 1;
        }
        if (rssi <= -66) {
            return 2;
        }
        if (rssi <= -55) {
            return 3;
        }
        return 4;
    }

    private static int maxLevel(Map<Network, Integer> levels) {
        int max = 0;
        for (Integer level : levels.values()) {
            if (level != null && level > max) {
                max = level;
            }
        }
        return clamp(max);
    }

    private static int clamp(int level) {
        if (level < 0) {
            return 0;
        }
        return Math.min(level, 4);
    }

    private static final class TransportCallback extends ConnectivityManager.NetworkCallback {
        private final boolean wifi;

        TransportCallback(boolean wifi) {
            this.wifi = wifi;
        }

        @Override
        public void onAvailable(Network network) {
            if (wifi) {
                setWifiNetwork(network, true, -1);
            } else {
                setMobileNetwork(network, true);
            }
        }

        @Override
        public void onLost(Network network) {
            if (wifi) {
                setWifiNetwork(network, false, -1);
            } else {
                setMobileNetwork(network, false);
            }
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
            if (wifi) {
                setWifiNetwork(network, true, readWifiLevel(capabilities));
            } else {
                setMobileNetwork(network, true);
            }
        }
    }

    private static final class PhoneCallback extends TelephonyCallback
            implements TelephonyCallback.SignalStrengthsListener, TelephonyCallback.DisplayInfoListener {
        private final int subscription;
        private volatile boolean active = true;

        PhoneCallback(int subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            if (active && signalStrength != null) {
                setMobileLevel(subscription, signalStrength.getLevel());
            }
        }

        @Override
        public void onDisplayInfoChanged(TelephonyDisplayInfo displayInfo) {
            if (!active || displayInfo == null) {
                return;
            }
            setMobileLabel(subscription, NetworkLabel.fromDisplayInfo(
                    displayInfo.getOverrideNetworkType(), displayInfo.getNetworkType()));
        }
    }
}

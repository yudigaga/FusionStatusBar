package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HardwarePropertiesManager;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Samples optional status readouts on one worker thread, never in a view/layout hook. */
final class TelemetrySampler {
    private static final File BATTERY_UEVENT = new File("/sys/class/power_supply/battery/uevent");
    private static final File THERMAL_ROOT = new File("/sys/class/thermal");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ScheduledExecutorService WORKER =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "fusion-telemetry");
                thread.setDaemon(true);
                return thread;
            });
    private static final AtomicBoolean BATTERY_ERROR_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean GPU_ERROR_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean GPU_PATH_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean SAMPLE_ERROR_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean SCREEN_RECEIVER_ERROR_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean HARDWARE_GPU_ERROR_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean BATTERY_SOURCE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean NETWORK_SOURCE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean VALUES_LOGGED = new AtomicBoolean();

    private static volatile TelemetryConfig config = TelemetryConfig.defaults();
    private static volatile TelemetryReadings latest = TelemetryReadings.EMPTY;
    private static volatile int generation;
    private static volatile boolean interactive = true;
    private static long lastInteractiveCheck;
    private static Context appContext;
    private static BroadcastReceiver screenReceiver;
    private static ScheduledFuture<?> polling;
    private static long lastTrafficAt = -1;
    private static long lastTx = -1;
    private static long lastRx = -1;
    private static int trafficGeneration = -1;
    private static int lastCounterSource = -1;
    private static boolean interfaceMethodsChecked;
    private static Method interfaceTxMethod;
    private static Method interfaceRxMethod;
    private static File gpuTempFile;
    private static long lastGpuScan;
    private static long nextHardwareGpuAttempt;

    private TelemetrySampler() {
    }

    static void configure(Context context, TelemetryConfig next) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> configure(context, next));
            return;
        }
        if (context == null || next == null) return;
        if (appContext == null) {
            Context application = context.getApplicationContext();
            appContext = application != null ? application : context;
        }
        long now = SystemClock.uptimeMillis();
        if (config != next || now - lastInteractiveCheck >= 1_000L) {
            lastInteractiveCheck = now;
            updateInteractive(queryInteractive());
        }
        if (config == next && (polling != null) == (next.anyEnabled() && interactive)) return;
        config = next;
        generation++;
        if (!next.anyEnabled()) {
            if (polling != null) polling.cancel(false);
            polling = null;
            unregisterScreenReceiver();
            appContext = null;
            lastInteractiveCheck = 0;
            latest = TelemetryReadings.EMPTY;
            DualRowStatusBarLayout.updateTelemetry(latest);
            return;
        }
        registerScreenReceiver();
        DualRowStatusBarLayout.updateTelemetry(latest);
        reconcilePolling();
    }

    static boolean isPolling() {
        return polling != null && !polling.isCancelled();
    }

    private static void reconcilePolling() {
        if (!config.anyEnabled() || !interactive) {
            if (polling != null) polling.cancel(false);
            polling = null;
        } else if (polling == null) {
            polling = WORKER.scheduleWithFixedDelay(TelemetrySampler::sample,
                    0, 2, TimeUnit.SECONDS);
        }
    }

    static TelemetryReadings latest() {
        return latest;
    }

    static boolean isInteractive() {
        return interactive;
    }

    private static boolean queryInteractive() {
        try {
            PowerManager power = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            return power == null || power.isInteractive();
        } catch (RuntimeException error) {
            return interactive;
        }
    }

    private static void updateInteractive(boolean active) {
        if (interactive != active) {
            interactive = active;
            generation++;
            reconcilePolling();
            DualRowStatusBarLayout.updateTelemetry(latest);
        }
    }

    private static void registerScreenReceiver() {
        if (screenReceiver != null) return;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    if (!interactive) generation++;
                    updateInteractive(false);
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    if (interactive) generation++;
                    updateInteractive(true);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        try {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            screenReceiver = receiver;
        } catch (RuntimeException error) {
            if (SCREEN_RECEIVER_ERROR_LOGGED.compareAndSet(false, true)) {
                Log.w("FusionStatusBar", "screen state receiver unavailable", error);
            }
        }
    }

    private static void unregisterScreenReceiver() {
        if (screenReceiver == null) return;
        try {
            appContext.unregisterReceiver(screenReceiver);
        } catch (RuntimeException ignored) {
            // The process owns this receiver; a missing registration is harmless.
        }
        screenReceiver = null;
    }

    private static void sample() {
        try {
            int sampleGeneration = generation;
            TelemetryConfig settings = config;
            if (!settings.anyEnabled() || appContext == null) return;
            if (trafficGeneration != sampleGeneration) {
                lastTrafficAt = lastTx = lastRx = -1;
                lastCounterSource = -1;
                trafficGeneration = sampleGeneration;
            }
            PowerManager power = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (power != null && !power.isInteractive()) {
                lastTrafficAt = -1;
                if (interactive) MAIN.post(() -> {
                    if (sampleGeneration == generation) updateInteractive(false);
                });
                return;
            }
            if (!interactive) MAIN.post(() -> {
                if (sampleGeneration == generation) updateInteractive(true);
            });

            Properties battery = new Properties();
            boolean currentInMilliamps = settings.currentInMilliamps;
            if (settings.needsBattery()) {
                battery = readBatteryUevent();
                currentInMilliamps = fillBatteryFallback(battery, currentInMilliamps);
            }
            Double gpuCelsius = settings.needsGpu()
                    ? readGpuTemperature() : null;

            long upload = 0;
            long download = 0;
            boolean speedValid = false;
            if (settings.enabled(TelemetryConfig.NET_SPEED)) {
                long now = SystemClock.elapsedRealtime();
                long[] traffic = readTrafficCounters();
                long tx = traffic[0];
                long rx = traffic[1];
                if (lastCounterSource != (int) traffic[2]) {
                    lastTrafficAt = -1;
                    lastCounterSource = (int) traffic[2];
                }
                if (tx >= 0 && rx >= 0 && lastTx >= 0 && lastRx >= 0 && lastTrafficAt >= 0) {
                    long elapsed = now - lastTrafficAt;
                    upload = TelemetryReadings.speed(tx, lastTx, elapsed);
                    download = TelemetryReadings.speed(rx, lastRx, elapsed);
                    speedValid = elapsed > 0 && tx >= lastTx && rx >= lastRx;
                }
                lastTx = tx;
                lastRx = rx;
                lastTrafficAt = now;
            } else {
                lastTrafficAt = -1;
                lastCounterSource = -1;
            }
            TelemetryReadings readings = TelemetryReadings.fromBattery(battery,
                    currentInMilliamps, settings.currentPositive,
                    gpuCelsius, upload, download, speedValid);
            if ((!settings.enabled(TelemetryConfig.NET_SPEED) || speedValid)
                    && VALUES_LOGGED.compareAndSet(false, true)) {
                Log.i("FusionStatusBar", "telemetry values temperatures="
                        + readings.textFor(TelemetryConfig.TEMPERATURES, settings, java.util.Locale.ROOT)
                        .replace('\n', '/')
                        + " powerCurrent=" + readings.textFor(
                        TelemetryConfig.POWER_CURRENT, settings, java.util.Locale.ROOT)
                        .replace('\n', '/')
                        + " speed=" + readings.textFor(
                        TelemetryConfig.NET_SPEED, settings, java.util.Locale.ROOT)
                        .replace('\n', '/'));
            }
            MAIN.post(() -> {
                if (sampleGeneration != generation || !config.anyEnabled() || !interactive) return;
                if (readings.sameValues(latest)) return;
                latest = readings;
                DualRowStatusBarLayout.updateTelemetry(readings);
            });
        } catch (Throwable error) {
            if (SAMPLE_ERROR_LOGGED.compareAndSet(false, true)) {
                Log.w("FusionStatusBar", "telemetry sample failed", error);
            }
        }
    }

    private static Properties readBatteryUevent() {
        Properties values = new Properties();
        try (FileInputStream input = new FileInputStream(BATTERY_UEVENT)) {
            values.load(input);
        } catch (IOException | SecurityException error) {
            if (BATTERY_ERROR_LOGGED.compareAndSet(false, true)) {
                Log.w("FusionStatusBar", "battery uevent unavailable", error);
            }
        }
        return values;
    }

    private static long[] readTrafficCounters() {
        if (!interfaceMethodsChecked) {
            interfaceMethodsChecked = true;
            try {
                interfaceTxMethod = TrafficStats.class.getDeclaredMethod("getTxBytes", String.class);
                interfaceRxMethod = TrafficStats.class.getDeclaredMethod("getRxBytes", String.class);
                interfaceTxMethod.setAccessible(true);
                interfaceRxMethod.setAccessible(true);
            } catch (Throwable ignored) {
                interfaceTxMethod = null;
                interfaceRxMethod = null;
            }
        }
        if (interfaceTxMethod != null && interfaceRxMethod != null) {
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                long tx = 0;
                long rx = 0;
                int counted = 0;
                while (interfaces != null && interfaces.hasMoreElements()) {
                    NetworkInterface network = interfaces.nextElement();
                    if (!network.isUp() || network.isVirtual() || network.isLoopback()
                            || network.isPointToPoint()) {
                        continue;
                    }
                    String name = network.getName();
                    if (name == null || name.isEmpty()) continue;
                    long interfaceTx = ((Number) interfaceTxMethod.invoke(null, name)).longValue();
                    long interfaceRx = ((Number) interfaceRxMethod.invoke(null, name)).longValue();
                    if (interfaceTx >= 0 && interfaceRx >= 0) {
                        tx += interfaceTx;
                        rx += interfaceRx;
                        counted++;
                    }
                }
                if (counted > 0) {
                    if (NETWORK_SOURCE_LOGGED.compareAndSet(false, true)) {
                        Log.i("FusionStatusBar", "network speed source=physical interfaces");
                    }
                    return new long[] {tx, rx, 1};
                }
            } catch (Throwable ignored) {
                interfaceTxMethod = null;
                interfaceRxMethod = null;
            }
        }
        if (NETWORK_SOURCE_LOGGED.compareAndSet(false, true)) {
            Log.i("FusionStatusBar", "network speed source=total traffic fallback");
        }
        return new long[] {TrafficStats.getTotalTxBytes(), TrafficStats.getTotalRxBytes(), 0};
    }

    private static boolean fillBatteryFallback(Properties values, boolean currentInMilliamps) {
        try {
            BatteryManager manager = (BatteryManager) appContext.getSystemService(
                    Context.BATTERY_SERVICE);
            if (manager != null) {
                int current = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (current != Integer.MIN_VALUE) {
                    values.setProperty("FUSION_API_CURRENT_UA", Integer.toString(current));
                    if (values.getProperty("POWER_SUPPLY_CURRENT_NOW") == null) {
                        values.setProperty("POWER_SUPPLY_CURRENT_NOW",
                                Long.toString(-(long) current));
                        currentInMilliamps = false;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A missing current leaves the readout unavailable.
        }
        if (values.getProperty("POWER_SUPPLY_TEMP") == null
                || values.getProperty("POWER_SUPPLY_VOLTAGE_NOW") == null
                || values.getProperty("POWER_SUPPLY_STATUS") == null) {
            try {
                Intent battery = appContext.registerReceiver(null,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (battery != null) {
                    if (values.getProperty("POWER_SUPPLY_TEMP") == null) {
                        int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                        if (temp >= 0) values.setProperty("POWER_SUPPLY_TEMP", Integer.toString(temp));
                    }
                    if (values.getProperty("POWER_SUPPLY_VOLTAGE_NOW") == null) {
                        int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                        if (voltage > 0) values.setProperty("POWER_SUPPLY_VOLTAGE_NOW",
                                Long.toString(voltage * 1000L));
                    }
                    if (values.getProperty("POWER_SUPPLY_STATUS") == null) {
                        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                        values.setProperty("POWER_SUPPLY_STATUS",
                                status == BatteryManager.BATTERY_STATUS_CHARGING ? "Charging"
                                        : status == BatteryManager.BATTERY_STATUS_DISCHARGING
                                                ? "Discharging" : "Unknown");
                    }
                }
            } catch (RuntimeException ignored) {
                // The sysfs values already read remain valid.
            }
        }
        if (BATTERY_SOURCE_LOGGED.compareAndSet(false, true)) {
            Log.i("FusionStatusBar", "battery source temp="
                    + values.getProperty("POWER_SUPPLY_TEMP", "missing")
                    + " current=" + values.getProperty("POWER_SUPPLY_CURRENT_NOW", "missing")
                    + " apiCurrent=" + values.getProperty("FUSION_API_CURRENT_UA", "missing")
                    + " voltage=" + values.getProperty("POWER_SUPPLY_VOLTAGE_NOW", "missing")
                    + " forceMa=" + currentInMilliamps);
        }
        return currentInMilliamps;
    }

    private static Double readGpuTemperature() {
        long now = SystemClock.elapsedRealtime();
        Double hardware = readHardwareGpuTemperature(now);
        if (hardware != null) return hardware;
        if (gpuTempFile == null && (lastGpuScan == 0 || now - lastGpuScan >= 60_000L)) {
            gpuTempFile = findGpuTemperatureFile();
            lastGpuScan = now;
        }
        if (gpuTempFile == null) return null;
        try {
            Double value = GpuThermalSensor.celsius(firstLine(gpuTempFile));
            if (value == null && GPU_ERROR_LOGGED.compareAndSet(false, true)) {
                Log.w("FusionStatusBar", "GPU thermal value invalid: " + gpuTempFile);
            }
            return value;
        } catch (IOException | SecurityException error) {
            gpuTempFile = null;
            if (GPU_ERROR_LOGGED.compareAndSet(false, true)) {
                Log.w("FusionStatusBar", "GPU temperature unreadable", error);
            }
            return null;
        }
    }

    private static Double readHardwareGpuTemperature(long now) {
        if (now < nextHardwareGpuAttempt) return null;
        try {
            HardwarePropertiesManager manager = (HardwarePropertiesManager)
                    appContext.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE);
            if (manager == null) {
                nextHardwareGpuAttempt = now + 60_000L;
                return null;
            }
            Double value = GpuThermalSensor.hottest(manager.getDeviceTemperatures(
                    HardwarePropertiesManager.DEVICE_TEMPERATURE_GPU,
                    HardwarePropertiesManager.TEMPERATURE_CURRENT));
            if (value == null) nextHardwareGpuAttempt = now + 60_000L;
            return value;
        } catch (SecurityException error) {
            nextHardwareGpuAttempt = Long.MAX_VALUE;
            if (HARDWARE_GPU_ERROR_LOGGED.compareAndSet(false, true)) {
                Log.w("FusionStatusBar", "hardware GPU temperature denied", error);
            }
            return null;
        } catch (RuntimeException error) {
            nextHardwareGpuAttempt = now + 60_000L;
            if (HARDWARE_GPU_ERROR_LOGGED.compareAndSet(false, true)) {
                Log.w("FusionStatusBar", "hardware GPU temperature unavailable", error);
            }
            return null;
        }
    }

    private static File findGpuTemperatureFile() {
        File[] zones;
        try {
            zones = THERMAL_ROOT.listFiles(file ->
                    file.isDirectory() && file.getName().startsWith("thermal_zone"));
        } catch (SecurityException error) {
            if (GPU_ERROR_LOGGED.compareAndSet(false, true)) {
                Log.w("FusionStatusBar", "GPU thermal directory unreadable", error);
            }
            return null;
        }
        if (zones == null) {
            if (GPU_ERROR_LOGGED.compareAndSet(false, true)) {
                Log.w("FusionStatusBar", "GPU thermal zones unavailable");
            }
            return null;
        }
        for (File zone : zones) {
            try {
                String type = firstLine(new File(zone, "type"));
                if (GpuThermalSensor.isGpuType(type)) {
                    File temperature = new File(zone, "temp");
                    if (temperature.canRead()) {
                        if (GPU_PATH_LOGGED.compareAndSet(false, true)) {
                            Log.i("FusionStatusBar", "GPU thermal zone " + zone.getName()
                                    + " type=" + type.trim());
                        }
                        return temperature;
                    }
                }
            } catch (IOException | SecurityException ignored) {
                // Unreadable zones are not GPU evidence.
            }
        }
        if (GPU_ERROR_LOGGED.compareAndSet(false, true)) {
            Log.w("FusionStatusBar", "no readable GPU thermal zone found");
        }
        return null;
    }

    private static String firstLine(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String value = reader.readLine();
            if (value == null) throw new IOException("empty sensor file");
            return value;
        }
    }
}

package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 35}, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class StatusMonitorBatteryTest {
    @Before
    public void resetBatteryState() throws Exception {
        setField("batteryPercent", -1);
        setField("charging", false);
        setField("fastCharging", false);
        setField("powerSave", false);
        setField("powerSaveRegistered", false);
        setField("dirty", false);
        setField("mainHandler", new Handler(Looper.getMainLooper()));
    }

    @Test
    public void oldSnapshotConstructorsKeepDefaultFlags() {
        StatusMonitor.Snapshot[] snapshots = {
                new StatusMonitor.Snapshot(64, true, "", 4),
                new StatusMonitor.Snapshot(64, true, "", 4, 3),
                new StatusMonitor.Snapshot(64, true, "", 4, 3, true),
                new StatusMonitor.Snapshot(64, true, "", 4, 3, 2, true)
        };
        for (StatusMonitor.Snapshot snapshot : snapshots) {
            assertFalse(snapshot.fastCharging);
            assertFalse(snapshot.powerSave);
        }
        assertTrue(snapshots[2].charging);
        assertEquals(2, snapshots[3].wifiLevel);
    }

    @Test
    public void completeSnapshotKeepsSaverAndRequiresChargingForFastFlag() {
        StatusMonitor.Snapshot snapshot = new StatusMonitor.Snapshot(
                24, true, "", 4, 3, false, true, true);
        assertFalse(snapshot.fastCharging);
        assertTrue(snapshot.powerSave);
    }

    @Test
    public void completeSnapshotPreservesIndependentWifiAndBatteryFlags() {
        StatusMonitor.Snapshot snapshot = new StatusMonitor.Snapshot(
                24, true, "", 4, 3, 2, true, true, true);
        assertEquals(4, snapshot.signalLevel);
        assertEquals(3, snapshot.signalLevel2);
        assertEquals(2, snapshot.wifiLevel);
        assertTrue(snapshot.charging);
        assertTrue(snapshot.fastCharging);
        assertTrue(snapshot.powerSave);
    }

    @Test
    public void chargingAloneAndOrdinaryUsbCapabilityStayRegular() throws Exception {
        applyBattery(battery(BatteryManager.BATTERY_STATUS_CHARGING));
        assertTrue(StatusMonitor.snapshot().charging);
        assertFalse(StatusMonitor.snapshot().fastCharging);

        applyBattery(withCapability(battery(BatteryManager.BATTERY_STATUS_CHARGING),
                1_500_000, 5_000_000));
        assertFalse(StatusMonitor.snapshot().fastCharging);
    }

    @Test
    public void supportedPowerSetsFastAndRegularUpdateClearsItAtSameBatteryLevel()
            throws Exception {
        applyBattery(withCapability(battery(BatteryManager.BATTERY_STATUS_CHARGING),
                2_000_000, 9_000_000));
        assertTrue(StatusMonitor.snapshot().fastCharging);

        applyBattery(withCapability(battery(BatteryManager.BATTERY_STATUS_CHARGING),
                1_000_000, 5_000_000));
        assertTrue(StatusMonitor.snapshot().charging);
        assertFalse(StatusMonitor.snapshot().fastCharging);
    }

    @Test
    public void missingCapabilityDoesNotGuessFastOrRetainOldFastFlag() throws Exception {
        applyBattery(withCapability(battery(BatteryManager.BATTERY_STATUS_CHARGING),
                5_000_000, 20_000_000));
        assertTrue(StatusMonitor.snapshot().fastCharging);

        applyBattery(battery(BatteryManager.BATTERY_STATUS_CHARGING)
                .putExtra("max_charging_current", 5_000_000));
        assertFalse(StatusMonitor.snapshot().fastCharging);

        applyBattery(battery(BatteryManager.BATTERY_STATUS_CHARGING)
                .putExtra("max_charging_voltage", 20_000_000));
        assertFalse(StatusMonitor.snapshot().fastCharging);
    }

    @Test
    public void unpluggedUpdateClearsStaleChargingEvenWithoutValidPercentage() throws Exception {
        applyBattery(withCapability(battery(BatteryManager.BATTERY_STATUS_CHARGING),
                2_000_000, 9_000_000));
        applyBattery(withCapability(new Intent(Intent.ACTION_BATTERY_CHANGED)
                        .putExtra(BatteryManager.EXTRA_STATUS,
                                BatteryManager.BATTERY_STATUS_CHARGING)
                        .putExtra(BatteryManager.EXTRA_PLUGGED, 0),
                2_000_000, 9_000_000));
        assertEquals(64, StatusMonitor.snapshot().batteryPercent);
        assertFalse(StatusMonitor.snapshot().charging);
        assertFalse(StatusMonitor.snapshot().fastCharging);
    }

    @Test
    public void powerSavePrimesCurrentValueAndFollowsBroadcasts() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        PowerManager power = context.getSystemService(PowerManager.class);
        shadowOf(power).setIsPowerSaveMode(true);
        Method register = StatusMonitor.class.getDeclaredMethod("registerPowerSave", Context.class);
        register.setAccessible(true);
        register.invoke(null, context);
        assertTrue(StatusMonitor.snapshot().powerSave);

        shadowOf(power).setIsPowerSaveMode(false);
        context.sendBroadcast(new Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(StatusMonitor.snapshot().powerSave);

        shadowOf(power).setIsPowerSaveMode(true);
        context.sendBroadcast(new Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(StatusMonitor.snapshot().powerSave);
    }

    private static Intent battery(int status) {
        return new Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 64)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, status)
                .putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC);
    }

    private static Intent withCapability(Intent intent, int current, int voltage) {
        return intent.putExtra("max_charging_current", current)
                .putExtra("max_charging_voltage", voltage);
    }

    private static void applyBattery(Intent intent) throws Exception {
        Method apply = StatusMonitor.class.getDeclaredMethod("applyBattery", Intent.class);
        apply.setAccessible(true);
        apply.invoke(null, intent);
    }

    private static void setField(String name, Object value) throws Exception {
        Field field = StatusMonitor.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}

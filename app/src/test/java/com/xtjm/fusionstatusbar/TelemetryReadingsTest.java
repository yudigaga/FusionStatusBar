package com.xtjm.fusionstatusbar;

import org.junit.Test;

import java.util.Locale;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TelemetryReadingsTest {
    @Test
    public void batteryUeventUsesDocumentedUnitsAndChargingDirection() {
        Properties values = new Properties();
        values.setProperty("POWER_SUPPLY_TEMP", "356");
        values.setProperty("POWER_SUPPLY_CURRENT_NOW", "900000");
        values.setProperty("POWER_SUPPLY_VOLTAGE_NOW", "4000000");
        values.setProperty("POWER_SUPPLY_STATUS", "Discharging");
        TelemetryReadings readings = TelemetryReadings.fromBattery(
                values, false, false, 42.3d, 2048, 1024, true);
        TelemetryConfig config = TelemetryConfig.defaults();
        assertEquals("35.6℃\n42.3℃", readings.textFor(TelemetryConfig.TEMPERATURES,
                config, Locale.US));
        assertEquals("3.60W\n-900mA", readings.textFor(
                TelemetryConfig.POWER_CURRENT, config, Locale.US));
        assertEquals("↑2.0KB/s\n↓1.0KB/s", readings.textFor(
                TelemetryConfig.NET_SPEED, config, Locale.US));
    }

    @Test
    public void milliampQuirkAndMissingSensorRemainExplicit() {
        Properties values = new Properties();
        values.setProperty("POWER_SUPPLY_CURRENT_NOW", "1200");
        values.setProperty("POWER_SUPPLY_VOLTAGE_NOW", "4000");
        values.setProperty("POWER_SUPPLY_STATUS", "Charging");
        TelemetryReadings readings = TelemetryReadings.fromBattery(
                values, true, true, null, 0, 0, false);
        TelemetryConfig config = TelemetryConfig.defaults();
        assertEquals("4.80W\n1.20A", readings.textFor(
                TelemetryConfig.POWER_CURRENT, config, Locale.US));
        assertEquals("--\n--", readings.textFor(
                TelemetryConfig.TEMPERATURES, config, Locale.US));
        assertEquals("↑--\n↓--", readings.textFor(TelemetryConfig.NET_SPEED, config, Locale.US));
        assertNull(readings.batteryCelsius);
    }

    @Test
    public void batteryManagerReferenceCorrectsWrongCurrentUnitSetting() {
        Properties microamps = new Properties();
        microamps.setProperty("POWER_SUPPLY_CURRENT_NOW", "900000");
        microamps.setProperty("FUSION_API_CURRENT_UA", "900000");
        microamps.setProperty("POWER_SUPPLY_VOLTAGE_NOW", "4000000");
        TelemetryReadings readings = TelemetryReadings.fromBattery(
                microamps, true, false, null, 0, 0, false);
        assertEquals("3.60W\n-900mA", readings.textFor(
                TelemetryConfig.POWER_CURRENT, TelemetryConfig.defaults(), Locale.US));

        Properties milliamps = new Properties();
        milliamps.setProperty("POWER_SUPPLY_CURRENT_NOW", "900");
        milliamps.setProperty("FUSION_API_CURRENT_UA", "900000");
        milliamps.setProperty("POWER_SUPPLY_VOLTAGE_NOW", "4000000");
        TelemetryReadings corrected = TelemetryReadings.fromBattery(
                milliamps, false, true, null, 0, 0, false);
        assertEquals("3.60W\n900mA", corrected.textFor(
                TelemetryConfig.POWER_CURRENT, TelemetryConfig.defaults(), Locale.US));
    }

    @Test
    public void integralBatteryTemperatureDoesNotShowUnneededDecimal() {
        Properties values = new Properties();
        values.setProperty("POWER_SUPPLY_TEMP", "350");
        TelemetryReadings readings = TelemetryReadings.fromBattery(
                values, false, false, null, 0, 0, false);
        assertEquals("35℃\n--", readings.textFor(
                TelemetryConfig.TEMPERATURES, TelemetryConfig.defaults(), Locale.US));
    }

    @Test
    public void smallMatchingApiValueDoesNotOverrideManualMilliampMode() {
        Properties values = new Properties();
        values.setProperty("POWER_SUPPLY_CURRENT_NOW", "900");
        values.setProperty("FUSION_API_CURRENT_UA", "900");
        values.setProperty("POWER_SUPPLY_VOLTAGE_NOW", "4000000");
        TelemetryReadings readings = TelemetryReadings.fromBattery(
                values, true, false, null, 0, 0, false);
        assertEquals("3.60W\n-900mA", readings.textFor(
                TelemetryConfig.POWER_CURRENT, TelemetryConfig.defaults(), Locale.US));
    }

    @Test
    public void speedHandlesCounterResetAndElapsedTime() {
        assertEquals(2000, TelemetryReadings.speed(5000, 1000, 2000));
        assertEquals(0, TelemetryReadings.speed(1000, 5000, 2000));
        assertEquals(0, TelemetryReadings.speed(5000, -1, 2000));
        assertEquals(0, TelemetryReadings.speed(5000, 1000, 0));
    }

    @Test
    public void unchangedTelemetrySnapshotDoesNotNeedAnotherUiRefresh() {
        assertEquals(true, TelemetryReadings.SAMPLE.sameValues(
                new TelemetryReadings(35.6d, 42.3d, 3.6d, -900d, 2048, 1024, true)));
        assertEquals(false, TelemetryReadings.SAMPLE.sameValues(
                new TelemetryReadings(35.7d, 42.3d, 3.6d, -900d, 2048, 1024, true)));
    }
}

package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GpuThermalSensorTest {
    @Test
    public void onlyGpuNamedZonesAreAccepted() {
        assertTrue(GpuThermalSensor.isGpuType("gpu-therm"));
        assertTrue(GpuThermalSensor.isGpuType("GPUSS-0"));
        assertFalse(GpuThermalSensor.isGpuType("cpu_big"));
        assertFalse(GpuThermalSensor.isGpuType("battery"));
    }

    @Test
    public void commonThermalScalesConvergeToCelsius() {
        assertEquals(42d, GpuThermalSensor.celsius("42000"), 0.001d);
        assertEquals(42d, GpuThermalSensor.celsius("420"), 0.001d);
        assertEquals(42.5d, GpuThermalSensor.celsius("42.5"), 0.001d);
        assertNull(GpuThermalSensor.celsius("not a number"));
        assertNull(GpuThermalSensor.celsius("500000"));
        assertEquals(47d, GpuThermalSensor.hottest(new float[] {42f, 47f, Float.NaN}), 0.001d);
        assertNull(GpuThermalSensor.hottest(new float[] {Float.NaN, 300f}));
    }
}

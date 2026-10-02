package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TelemetryConfigTest {
    @Test
    public void defaultsDoNotStartPolling() {
        TelemetryConfig config = TelemetryConfig.defaults();
        assertFalse(config.anyEnabled());
        assertFalse(config.needsBattery());
        assertFalse(config.needsGpu());
        assertTrue(config.dualSpeed);
        assertEquals(10, config.textSizeSp);
        assertEquals(1, config.position(TelemetryConfig.TEMPERATURES));
        assertEquals(3, config.position(TelemetryConfig.POWER_CURRENT));
    }

    @Test
    public void groupedReadoutsCanMoveIndependently() {
        TelemetryConfig config = TelemetryConfig.defaults()
                .withEnabled(TelemetryConfig.TEMPERATURES, true)
                .withEnabled(TelemetryConfig.NET_SPEED, true)
                .withPosition(TelemetryConfig.NET_SPEED, 1)
                .withTextSize(20);
        assertTrue(config.enabled(TelemetryConfig.TEMPERATURES));
        assertTrue(config.enabled(TelemetryConfig.NET_SPEED));
        assertFalse(config.enabled(TelemetryConfig.POWER_CURRENT));
        assertEquals(1, config.position(TelemetryConfig.NET_SPEED));
        assertEquals(TelemetryConfig.SIDE_LEFT, TelemetryConfig.side(config.position(2)));
        assertEquals(1, TelemetryConfig.row(config.position(2)));
        assertEquals(14, config.textSizeSp);
        assertTrue(config.needsBattery());
        assertTrue(config.needsGpu());
        assertEquals(10f, config.textSizeFor(TelemetryConfig.TEMPERATURES, 48, true), 0.01f);
        assertEquals(6f, config.textSizeFor(TelemetryConfig.TEMPERATURES, 32, true), 0.01f);
    }

    @Test
    public void oldFiveMetricsMigrateToThreePairsWithoutMovingSelectedPosition() {
        TelemetryConfig config = TelemetryConfig.fromLegacy(
                new boolean[] {true, true, true, true, true},
                new int[] {2, 2, 2, 2, 2});
        assertTrue(config.enabled(TelemetryConfig.TEMPERATURES));
        assertTrue(config.enabled(TelemetryConfig.POWER_CURRENT));
        assertTrue(config.enabled(TelemetryConfig.NET_SPEED));
        assertEquals(2, config.position(TelemetryConfig.TEMPERATURES));
        assertEquals(2, config.position(TelemetryConfig.POWER_CURRENT));
        assertEquals(2, config.position(TelemetryConfig.NET_SPEED));
    }

    @Test
    public void groupedSideMigrationKeepsOldRowChoice() {
        assertEquals(1, TelemetryConfig.migratedPosition(TelemetryConfig.SIDE_LEFT, 3));
        assertEquals(2, TelemetryConfig.migratedPosition(TelemetryConfig.SIDE_RIGHT, 0));
    }

    @Test
    public void oneOldTemperatureStillEnablesTheWholeTemperaturePair() {
        TelemetryConfig config = TelemetryConfig.fromLegacy(
                new boolean[] {false, true, false, false, false},
                new int[] {1, 3, 2, 0, 3});
        assertTrue(config.enabled(TelemetryConfig.TEMPERATURES));
        assertEquals(3, config.position(TelemetryConfig.TEMPERATURES));
        assertFalse(config.enabled(TelemetryConfig.POWER_CURRENT));
        assertFalse(config.enabled(TelemetryConfig.NET_SPEED));
    }

    @Test
    public void fusionConfigCopiesTelemetry() {
        TelemetryConfig telemetry = TelemetryConfig.defaults()
                .withEnabled(TelemetryConfig.TEMPERATURES, true)
                .withDualSpeed(false);
        FusionConfig config = FusionConfig.defaults().withTelemetry(telemetry)
                .withIconScale(120).withDoubleRow(true).withShowWeather(true);
        assertTrue(config.telemetry.enabled(TelemetryConfig.TEMPERATURES));
        assertFalse(config.telemetry.dualSpeed);
        assertTrue(config.telemetry.withCurrentPositive(true).currentPositive);
    }

    @Test
    public void eachReadoutKeepsIndependentPresentationSettings() {
        TelemetryConfig config = TelemetryConfig.defaults()
                .withGroupTextSize(TelemetryConfig.NET_SPEED, 14)
                .withAlignment(TelemetryConfig.NET_SPEED, TelemetryConfig.ALIGN_RIGHT)
                .withFixedWidth(TelemetryConfig.NET_SPEED, 120)
                .withLeftMargin(TelemetryConfig.NET_SPEED, 8)
                .withRightMargin(TelemetryConfig.NET_SPEED, 10)
                .withVerticalOffset(TelemetryConfig.NET_SPEED, -12)
                .withBold(TelemetryConfig.NET_SPEED, true)
                .withLineSpacing(TelemetryConfig.NET_SPEED, 6);
        assertEquals(14, config.textSize(TelemetryConfig.NET_SPEED));
        assertEquals(10, config.textSize(TelemetryConfig.TEMPERATURES));
        assertEquals(TelemetryConfig.ALIGN_RIGHT,
                config.alignment(TelemetryConfig.NET_SPEED));
        assertEquals(120, config.fixedWidth(TelemetryConfig.NET_SPEED));
        assertEquals(8, config.leftMargin(TelemetryConfig.NET_SPEED));
        assertEquals(10, config.rightMargin(TelemetryConfig.NET_SPEED));
        assertEquals(-12, config.verticalOffset(TelemetryConfig.NET_SPEED));
        assertTrue(config.bold(TelemetryConfig.NET_SPEED));
        assertEquals(6, config.lineSpacing(TelemetryConfig.NET_SPEED));
    }

    @Test
    public void defaultReadoutAlignmentFollowsItsSelectedSide() {
        assertEquals(TelemetryConfig.ALIGN_LEFT,
                TelemetryConfig.resolvedAlignment(TelemetryConfig.ALIGN_DEFAULT,
                        TelemetryConfig.SIDE_LEFT));
        assertEquals(TelemetryConfig.ALIGN_RIGHT,
                TelemetryConfig.resolvedAlignment(TelemetryConfig.ALIGN_DEFAULT,
                        TelemetryConfig.SIDE_RIGHT));
        assertEquals(TelemetryConfig.ALIGN_CENTER,
                TelemetryConfig.resolvedAlignment(TelemetryConfig.ALIGN_CENTER,
                        TelemetryConfig.SIDE_RIGHT));
        assertEquals(TelemetryConfig.ALIGN_LEFT,
                TelemetryConfig.resolvedAlignment(TelemetryConfig.ALIGN_LEFT,
                        TelemetryConfig.SIDE_RIGHT));
    }
}

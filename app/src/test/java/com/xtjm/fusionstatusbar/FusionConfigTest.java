package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FusionConfigTest {
    @Test
    public void clampsOverallSizeAndWeightToSliderRange() {
        FusionConfig config = FusionConfig.defaults()
                .withIconScale(1)
                .withStrokeScale(151);
        assertEquals(50, config.iconScale);
        assertEquals(150, config.strokeScale);
    }

    @Test
    public void copyMethodsKeepLayoutOptions() {
        FusionConfig original = FusionConfig.defaults()
                .withIconScale(80)
                .withStrokeScale(120)
                .withStatusBarHeight(72)
                .withElementPosition(1, FusionConfig.SIDE_RIGHT, 1)
                .withDoubleRow(true)
                .withDoubleRowSide(FusionConfig.SIDE_LEFT)
                .withSpanRows(true)
                .withSpanOffsetX(-12)
                .withSpanOffsetY(6);
        FusionConfig copy = original.withIconScale(110).withStrokeScale(65);
        assertEquals(110, copy.iconScale);
        assertEquals(65, copy.strokeScale);
        assertTrue(copy.doubleRow);
        assertEquals(FusionConfig.SIDE_LEFT, copy.doubleRowSide);
        assertTrue(copy.spanRows);
        assertEquals(72, copy.statusBarHeight);
        assertEquals(FusionConfig.SIDE_RIGHT, copy.notificationSide);
        assertEquals(1, copy.notificationRow);
        assertEquals(-12, copy.spanOffsetX);
        assertEquals(6, copy.spanOffsetY);
    }

    @Test
    public void overallSizeAndWeightAreIndependent() {
        FusionConfig config = FusionConfig.defaults()
                .withIconScale(70)
                .withStrokeScale(135);
        assertEquals(70, config.iconScale);
        assertEquals(135, config.strokeScale);
        assertEquals(135, config.withIconScale(100).strokeScale);
        assertEquals(70, config.withStrokeScale(100).iconScale);
    }

    @Test
    public void elementPositionsAndHeightAreIndependent() {
        FusionConfig config = FusionConfig.defaults()
                .withStatusBarHeight(72)
                .withElementPosition(0, FusionConfig.SIDE_LEFT, 0)
                .withElementPosition(1, FusionConfig.SIDE_RIGHT, 1)
                .withElementPosition(2, FusionConfig.SIDE_LEFT, 1);
        assertEquals(72, config.statusBarHeight);
        assertEquals(FusionConfig.SIDE_LEFT, config.clockSide);
        assertEquals(0, config.clockRow);
        assertEquals(FusionConfig.SIDE_RIGHT, config.notificationSide);
        assertEquals(1, config.notificationRow);
        assertEquals(FusionConfig.SIDE_LEFT, config.systemSide);
        assertEquals(1, config.systemRow);
    }

    @Test
    public void defaultsUseSafeLayoutOptions() {
        FusionConfig config = FusionConfig.defaults();
        assertEquals(100, config.iconScale);
        assertEquals(100, config.strokeScale);
        assertEquals(FusionConfig.SIDE_RIGHT, config.doubleRowSide);
        assertFalse(config.doubleRow);
        assertFalse(config.spanRows);
        assertEquals(0, config.spanOffsetX);
        assertEquals(0, config.spanOffsetY);
        assertFalse(config.customClock);
        assertFalse(config.showWeather);
        assertEquals("HH:mm", config.clockPattern);
    }

    @Test
    public void clockSettingsSurviveOtherConfigChanges() {
        FusionConfig config = FusionConfig.defaults()
                .withCustomClock(true)
                .withClockPattern("MM/dd HH:mm tq")
                .withShowWeather(true)
                .withSpanOffsetX(8)
                .withIconScale(125)
                .withDoubleRow(true);
        assertTrue(config.customClock);
        assertTrue(config.showWeather);
        assertEquals("MM/dd HH:mm tq", config.clockPattern);
        assertEquals(8, config.spanOffsetX);
    }

    @Test
    public void spanOffsetsClampWithoutChangingOtherSettings() {
        FusionConfig config = FusionConfig.defaults()
                .withSpanOffsetX(-50)
                .withSpanOffsetY(50);
        assertEquals(-24, config.spanOffsetX);
        assertEquals(24, config.spanOffsetY);
        FusionConfig changed = config.withStatusBarHeight(72)
                .withElementPosition(2, FusionConfig.SIDE_LEFT, 1)
                .withWifiIcon(false);
        assertEquals(-24, changed.spanOffsetX);
        assertEquals(24, changed.spanOffsetY);
    }

    @Test
    public void doubleRowSideDoesNotMoveSystemGroup() {
        FusionConfig config = FusionConfig.defaults()
                .withSystemPosition(FusionConfig.SIDE_LEFT, 1)
                .withDoubleRowSide(FusionConfig.SIDE_RIGHT);
        assertEquals(FusionConfig.SIDE_RIGHT, config.doubleRowSide);
        assertEquals(FusionConfig.SIDE_LEFT, config.systemSide);
        assertEquals(1, config.systemRow);
    }

    @Test
    public void systemGroupPositionDoesNotMoveDoubleRowSide() {
        FusionConfig config = FusionConfig.defaults()
                .withDoubleRowSide(FusionConfig.SIDE_LEFT)
                .withSystemPosition(FusionConfig.SIDE_RIGHT, 0);
        assertEquals(FusionConfig.SIDE_LEFT, config.doubleRowSide);
        assertEquals(FusionConfig.SIDE_RIGHT, config.systemSide);
        assertEquals(0, config.systemRow);
    }

    @Test
    public void elementSystemPositionDoesNotMoveDoubleRowSide() {
        FusionConfig config = FusionConfig.defaults()
                .withDoubleRowSide(FusionConfig.SIDE_RIGHT)
                .withElementPosition(2, FusionConfig.SIDE_LEFT, 1);
        assertEquals(FusionConfig.SIDE_RIGHT, config.doubleRowSide);
        assertEquals(FusionConfig.SIDE_LEFT, config.systemSide);
        assertEquals(1, config.systemRow);
    }

    @Test
    public void clampsStatusBarHeightToSupportedRange() {
        assertEquals(32, FusionConfig.clampStatusBarHeight(1));
        assertEquals(72, FusionConfig.clampStatusBarHeight(72));
        assertEquals(96, FusionConfig.clampStatusBarHeight(120));
    }

    @Test
    public void fusionAndNativeSystemPositionsAreIndependent() {
        FusionConfig config = FusionConfig.defaults()
                .withFusionPosition(FusionConfig.SIDE_RIGHT, 0)
                .withSystemPosition(FusionConfig.SIDE_LEFT, 1)
                .withSystemIconScale(70)
                .withNotificationIconScale(130);
        assertEquals(FusionConfig.SIDE_RIGHT, config.fusionSide);
        assertEquals(0, config.fusionRow);
        assertEquals(FusionConfig.SIDE_LEFT, config.systemSide);
        assertEquals(1, config.systemRow);
        assertEquals(70, config.systemIconScale);
        assertEquals(130, config.notificationIconScale);
        FusionConfig changed = config.withIconScale(120).withStatusBarHeight(72);
        assertEquals(FusionConfig.SIDE_RIGHT, changed.fusionSide);
        assertEquals(70, changed.systemIconScale);
        assertEquals(130, changed.notificationIconScale);
    }

    @Test
    public void configurationSchemaHasAnExplicitCurrentVersion() {
        assertEquals(4, FusionConfig.CONFIG_SCHEMA_VERSION);
    }

    @Test
    public void notificationTimeStaysIndependentOfStatusBarClock() {
        NotificationClockConfig notification = NotificationClockConfig.defaults()
                .withEnabled(true).withPattern("HH:mm E").withSize(58).withOffsetY(-8);
        FusionConfig changed = FusionConfig.defaults()
                .withNotificationClock(notification)
                .withClockPattern("yyyy-MM-dd")
                .withCustomClock(true)
                .withIconScale(120)
                .withTelemetry(TelemetryConfig.defaults());
        assertEquals("yyyy-MM-dd", changed.clockPattern);
        assertEquals("HH:mm E", changed.notificationClock.pattern);
        assertTrue(changed.notificationClock.enabled);
        assertEquals(58, changed.notificationClock.sizeSp);
        assertEquals(-8, changed.notificationClock.offsetYDp);
        assertFalse(changed.withNotificationClock(notification.withEnabled(false))
                .notificationClock.enabled);
        assertTrue(changed.customClock);
    }

    @Test
    public void notificationTimeClampsSizeAndPosition() {
        NotificationClockConfig clock = NotificationClockConfig.defaults()
                .withSize(300).withOffsetY(-90);
        assertEquals(180, clock.sizeSp);
        assertEquals(-24, clock.offsetYDp);
        assertEquals(64, clock.withOffsetY(200).offsetYDp);
        assertEquals(0, clock.withSize(0).sizeSp);
    }

    @Test
    public void notificationDateDoesNotOverrideTimeOrStatusBarSettings() {
        NotificationClockConfig separate = NotificationClockConfig.defaults()
                .withEnabled(true).withPattern("HH:mm:ss").withSize(102)
                .withDateEnabled(true).withDatePattern("yyyy/MM/dd E")
                .withDateSize(22).withDateOffsetY(7);
        FusionConfig config = FusionConfig.defaults()
                .withCustomClock(true).withClockPattern("yy/MM/dd HH:mm")
                .withNotificationClock(separate)
                .withIconScale(110).withNotificationIconScale(80);
        assertEquals("yy/MM/dd HH:mm", config.clockPattern);
        assertEquals("HH:mm:ss", config.notificationClock.pattern);
        assertEquals(102, config.notificationClock.sizeSp);
        assertTrue(config.notificationClock.dateEnabled);
        assertEquals("yyyy/MM/dd E", config.notificationClock.datePattern);
        assertEquals(22, config.notificationClock.dateSizeSp);
        assertEquals(7, config.notificationClock.dateOffsetYDp);
        assertTrue(config.withNotificationClock(
                separate.withDateEnabled(false)).notificationClock.enabled);
        assertEquals(0, NotificationClockConfig.defaults().dateSizeSp);
    }

    @Test
    public void notificationDateSizeAndPositionClampIndependently() {
        NotificationClockConfig config = NotificationClockConfig.defaults()
                .withSize(70).withDateSize(100).withDateOffsetY(-100);
        assertEquals(70, config.sizeSp);
        assertEquals(72, config.dateSizeSp);
        assertEquals(-24, config.dateOffsetYDp);
        assertEquals(64, config.withDateOffsetY(200).dateOffsetYDp);
        assertEquals(10, config.withDateSize(1).dateSizeSp);
    }

    @Test
    public void notificationOffsetEasesFromCollapsedToExpanded() {
        assertEquals(0f, NotificationClockConfig.expansionOffsetDp(64, 0f), 0.001f);
        assertEquals(32f, NotificationClockConfig.expansionOffsetDp(64, 0.5f), 0.001f);
        assertEquals(64f, NotificationClockConfig.expansionOffsetDp(64, 1f), 0.001f);
        assertEquals(-24f, NotificationClockConfig.expansionOffsetDp(-24, 2f), 0.001f);
    }

    @Test
    public void notificationListOffsetIsIndependentAndClamped() {
        NotificationClockConfig config = NotificationClockConfig.defaults()
                .withEnabled(true).withSize(130).withDateEnabled(true)
                .withListOffsetY(45);
        assertEquals(45, config.listOffsetYDp);
        assertEquals(45, config.withSize(160).withDateSize(45).listOffsetYDp);
        assertEquals(45, config.withPattern("HH:mm:ss").withDatePattern("MM/dd E")
                .withOffsetY(20).withDateOffsetY(12).listOffsetYDp);
        assertEquals(128, config.withListOffsetY(500).listOffsetYDp);
        assertEquals(0, config.withListOffsetY(-10).listOffsetYDp);
    }

    @Test
    public void notificationTimeAndDateReserveIndependentRows() {
        NotificationClockConfig settings = NotificationClockConfig.defaults()
                .withEnabled(true).withSize(120).withDateEnabled(true).withDateSize(24)
                .withOffsetY(48);
        assertEquals(0, settings.dateRowSpacingDp(0.25f));
        assertEquals(56, settings.dateRowSpacingDp(1f));
        assertEquals(8, settings.withDateOffsetY(64).dateRowSpacingDp(1f));
        assertEquals(0, NotificationClockConfig.defaults().dateRowSpacingDp(1f));
    }

    @Test
    public void mergedNotificationPatternUsesOneFormatAndKeepsListOffset() {
        NotificationClockConfig settings = NotificationClockConfig.defaults()
                .withEnabled(true).withPattern("HH:mm\nMM/dd E").withSize(72)
                .withListOffsetY(36);
        assertEquals("HH:mm\nMM/dd E", settings.combinedPattern());
        assertEquals(36, settings.listOffsetYDp);
        assertEquals(36, settings.withOffsetY(20).listOffsetYDp);
    }

    @Test
    public void notificationTimeAndDateCenteringAreIndependent() {
        NotificationClockConfig config = NotificationClockConfig.defaults()
                .withTimeCentered(true).withDateCentered(false);
        assertTrue(config.timeCentered);
        assertFalse(config.dateCentered);
        config = config.withDateCentered(true).withTimeCentered(false);
        assertFalse(config.timeCentered);
        assertTrue(config.dateCentered);
    }

    @Test
    public void notificationSettingsVisibilitySurvivesOtherChanges() {
        NotificationClockConfig config = NotificationClockConfig.defaults()
                .withHideSettings(true).withSize(80).withDateCentered(true);
        assertTrue(config.hideSettings);
        assertTrue(config.withPattern("HH:mm\nMM/dd E").hideSettings);
        assertTrue(config.withListOffsetY(30).hideSettings);
        assertFalse(config.withHideSettings(false).hideSettings);
    }

    @Test
    public void controlCenterCompositionClampsAndSurvivesStatusChanges() {
        ControlCenterConfig control = ControlCenterConfig.defaults()
                .withEnabled(true).withColumns(9).withTileScale(200)
                .withCornerRadius(-2).withSpacing(30)
                .withHidden("wifi,bt,wifi");
        assertTrue(control.enabled);
        assertEquals(6, control.columns);
        assertEquals(130, control.tileScale);
        assertEquals(0, control.cornerRadius);
        assertEquals(24, control.spacing);
        assertTrue(control.isHidden("wifi"));
        assertTrue(FusionConfig.defaults().withControlCenter(control)
                .withIconScale(120).controlCenter.enabled);
    }

    @Test
    public void controlCenterLegacySpecsMapToHyperOsSpecs() {
        ControlCenterConfig control = ControlCenterConfig.defaults()
                .withOrder("wifi,screenrecord,silent,dnd,battery,location")
                .withHidden("screenrecord");

        assertEquals("wifi,custom(com.miui.screenrecorder/.service.quickservice),mute,"
                + "quietmode,batterysaver,gps", control.order);
        assertTrue(control.isHidden("custom(com.miui.screenrecorder/.service.QuickService)"));
    }

    @Test
    public void controlCenterClearRemovesOverridesWithoutDisablingFeature() {
        ControlCenterConfig edited = ControlCenterConfig.defaults()
                .withEnabled(true)
                .withOrder("wifi,cell,bt")
                .withHidden("bt")
                .withColumns(5)
                .withTileScale(120)
                .withCornerRadius(20)
                .withSpacing(8)
                .withTileShape("wifi", 2, 2);

        ControlCenterConfig cleared = edited.clearCustomization();
        assertTrue(cleared.enabled);
        assertEquals("", cleared.order);
        assertEquals("", cleared.hidden);
        assertEquals("", cleared.layout);
        assertEquals(4, cleared.columns);
        assertEquals(100, cleared.tileScale);
        assertEquals(0, cleared.cornerRadius);
        assertEquals(0, cleared.spacing);
        assertFalse(cleared.hasTileOrderOverride());
        assertFalse(cleared.hasVisualOverride());
        assertFalse(cleared.withEnabled(false).withEnabled(true).hasTileOrderOverride());
    }
}

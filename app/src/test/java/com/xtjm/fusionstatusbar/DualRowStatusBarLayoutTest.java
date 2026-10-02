package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DualRowStatusBarLayoutTest {
    @Test
    public void spanningBatteryStartsAtTopWhileOtherSystemIconsKeepTheirRow() {
        assertEquals(0, DualRowStatusBarLayout.systemChildRow(true, true, 1));
        assertEquals(1, DualRowStatusBarLayout.systemChildRow(true, false, 1));
        assertEquals(1, DualRowStatusBarLayout.systemChildRow(false, true, 1));
    }

    @Test
    public void telemetryOnlyUsesSecondRowWhenDoubleRowEnabled() {
        assertEquals(1, DualRowStatusBarLayout.telemetryRow(true, 3));
        assertEquals(0, DualRowStatusBarLayout.telemetryRow(false, 3));
    }

    @Test
    public void telemetrySharesOnlySpaceLeftByNativeStatusElements() {
        assertArrayEquals(new int[] {124, 96},
                StatusBarWidthAllocator.allocate(220, new int[] {180, 140}));
    }

    @Test
    public void telemetryKeepsPreferredWidthsWhenTheSlotHasRoom() {
        assertArrayEquals(new int[] {76, 58, 72},
                StatusBarWidthAllocator.allocate(240, new int[] {76, 58, 72}));
    }

    @Test
    public void telemetryCollapsesBeforeItCanPushNativeStatusElementsAway() {
        assertArrayEquals(new int[] {0, 0},
                StatusBarWidthAllocator.allocate(0, new int[] {180, 140}));
    }

    @Test
    public void spanningFusionIconReservesTheOppositeRowToo() {
        assertEquals(0, DualRowStatusBarLayout.spanningInsetForRow(true, 0, 0, 64));
        assertEquals(64, DualRowStatusBarLayout.spanningInsetForRow(true, 0, 1, 64));
        assertEquals(0, DualRowStatusBarLayout.spanningInsetForRow(false, 0, 1, 64));
    }

    @Test
    public void fusionWidthWinsWhenItsParentReportsZeroWidth() {
        assertEquals(64, DualRowStatusBarLayout.priorityChildWidth(0, 64));
        assertEquals(88, DualRowStatusBarLayout.priorityChildWidth(88, 64));
    }

    @Test
    public void fusionOffsetIsAddedAfterChoosingTheRealOrFallbackWidth() {
        assertEquals(74, DualRowStatusBarLayout.priorityReservationWidth(0, 64, 10));
        assertEquals(98, DualRowStatusBarLayout.priorityReservationWidth(88, 64, 10));
    }

    @Test
    public void oppositeRowReservesOnlyFusionIconNotParentWithNativeIcons() {
        int measuredNativeContainer = 196;
        int iconWidth = 72;
        int reservation = DualRowStatusBarLayout.oppositeRowFusionWidth(
                iconWidth, 64, 8);
        assertEquals(80, reservation);
        assertEquals(72, DualRowStatusBarLayout.oppositeRowFusionWidth(0, 64, 8));
        assertTrue(reservation < measuredNativeContainer);
    }

    @Test
    public void translatedFusionIconGetsPhysicalSpaceOnItsInwardSide() {
        assertEquals(0, DualRowStatusBarLayout.fusionInwardMarginLeft(
                FusionConfig.SIDE_LEFT, 10));
        assertEquals(10, DualRowStatusBarLayout.fusionInwardMarginRight(
                FusionConfig.SIDE_LEFT, 10));
        assertEquals(10, DualRowStatusBarLayout.fusionInwardMarginLeft(
                FusionConfig.SIDE_RIGHT, 10));
        assertEquals(0, DualRowStatusBarLayout.fusionInwardMarginRight(
                FusionConfig.SIDE_RIGHT, 10));
    }

    @Test
    public void disabledTranslationClearsBothPhysicalMargins() {
        assertEquals(0, DualRowStatusBarLayout.fusionInwardMarginLeft(
                FusionConfig.SIDE_RIGHT, 0));
        assertEquals(0, DualRowStatusBarLayout.fusionInwardMarginRight(
                FusionConfig.SIDE_LEFT, 0));
    }

    @Test
    public void telemetryOnlySharesSpaceRemainingAfterFusionAndNativeIcons() {
        int available = DualRowStatusBarLayout.telemetryAvailableWidth(220, 40, 64);
        assertEquals(116, available);
        assertArrayEquals(new int[] {65, 51},
                StatusBarWidthAllocator.allocate(available, new int[] {76, 60}));
    }

    @Test
    public void reusableWidthBuffersKeepTheSameAllocationResult() {
        int[] result = new int[2];
        StatusBarWidthAllocator.allocateInto(220, new int[] {180, 140}, 2,
                result, new long[2]);
        assertArrayEquals(new int[] {124, 96}, result);
    }

    @Test
    public void oppositeRowReadoutsMoveTowardRingWithoutCrossingItsVisibleEdge() {
        assertEquals(44, DualRowStatusBarLayout.readoutShiftTowardFusion(1200, 1144, 12, 90));
        assertEquals(24, DualRowStatusBarLayout.readoutShiftTowardFusion(1200, 1144, 12, 24));
        assertEquals(0, DualRowStatusBarLayout.readoutShiftTowardFusion(1200, 1190, 12, 90));
        assertEquals(0, DualRowStatusBarLayout.readoutShiftTowardFusion(1200, 1210, 12, 90));
    }
}

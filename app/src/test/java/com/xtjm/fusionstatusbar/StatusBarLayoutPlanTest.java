package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StatusBarLayoutPlanTest {
    @Test
    public void singleRowNormalizesEveryElementToTheOnlyVisibleRow() {
        FusionConfig config = FusionConfig.defaults()
                .withElementPosition(0, FusionConfig.SIDE_RIGHT, 1)
                .withElementPosition(1, FusionConfig.SIDE_RIGHT, 1)
                .withElementPosition(2, FusionConfig.SIDE_LEFT, 1)
                .withFusionPosition(FusionConfig.SIDE_LEFT, 1)
                .withSpanRows(true);

        StatusBarLayoutPlan plan = StatusBarLayoutPlan.from(config);

        assertFalse(plan.doubleRow);
        assertFalse(plan.spanFusion);
        assertEquals(0, plan.clockRow);
        assertEquals(0, plan.notificationRow);
        assertEquals(0, plan.systemRow);
        assertEquals(0, plan.fusionRow);
        assertEquals(0, plan.telemetryRow(3));
        assertEquals(0, plan.systemChildRow(true));
    }

    @Test
    public void spanningFusionReservesBothRowsButStartsInTheTopRow() {
        FusionConfig config = FusionConfig.defaults()
                .withDoubleRow(true)
                .withSpanRows(true)
                .withElementPosition(0, FusionConfig.SIDE_RIGHT, 1)
                .withElementPosition(1, FusionConfig.SIDE_LEFT, 0)
                .withElementPosition(2, FusionConfig.SIDE_RIGHT, 1)
                .withFusionPosition(FusionConfig.SIDE_RIGHT, 1);

        StatusBarLayoutPlan plan = StatusBarLayoutPlan.from(config);

        assertTrue(plan.doubleRow);
        assertTrue(plan.spanFusion);
        assertEquals(0, plan.systemChildRow(true));
        assertEquals(1, plan.systemChildRow(false));
        assertTrue(plan.fusionUsesRow(0));
        assertTrue(plan.fusionUsesRow(1));
        assertTrue(plan.nativeElementAt(FusionConfig.SIDE_RIGHT, 1));
        assertFalse(plan.nativeElementAt(FusionConfig.SIDE_LEFT, 1));
    }

    @Test
    public void planEqualityOnlyDependsOnEffectivePlacement() {
        FusionConfig first = FusionConfig.defaults().withIconScale(60);
        FusionConfig second = first.withIconScale(140);

        assertEquals(StatusBarLayoutPlan.from(first), StatusBarLayoutPlan.from(second));
    }
}

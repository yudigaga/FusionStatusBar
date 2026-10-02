package com.xtjm.fusionstatusbar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ControlCenterGridGeometryTest {
    @Test
    public void spanAndContentHeightIncludeInternalGaps() {
        assertEquals(218, ControlCenterGridGeometry.spanPx(70, 4, 3));
        assertEquals(166, ControlCenterGridGeometry.contentHeightPx(2, 81, 4));
        assertEquals(0, ControlCenterGridGeometry.contentHeightPx(0, 81, 4));
    }

    @Test
    public void columnUnitUsesAvailableWidthAndGap() {
        assertEquals(97, ControlCenterGridGeometry.columnUnitPx(400, 4, 4));
        assertEquals(1, ControlCenterGridGeometry.columnUnitPx(2, 4, 4));
    }

    @Test
    public void spanningItemEndsAtTheSameRowBoundaryAsStackedItems() {
        int row = 313;
        int gap = 12;
        for (int rows = 1; rows <= ControlCenterLayoutPlan.MAX_HEIGHT; rows++) {
            int height = ControlCenterGridGeometry.spanPx(row, gap, rows);
            assertEquals(rows * (row + gap), height + gap);
            assertEquals(height, ControlCenterGridGeometry.contentHeightPx(rows, row, gap));
        }
    }
}

package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FusionIconGeometryTest {
    @Test
    public void signalColumnsAreSymmetricAroundTheRingCenter() {
        float left = FusionIconGeometry.signalX(0);
        float right = FusionIconGeometry.signalX(3);
        float innerLeft = FusionIconGeometry.signalX(1);
        float innerRight = FusionIconGeometry.signalX(2);

        assertEquals(FusionIconGeometry.RING_CENTER_X, (left + right) / 2f, 0.001f);
        assertEquals(FusionIconGeometry.RING_CENTER_X,
                (innerLeft + innerRight) / 2f, 0.001f);
    }

    @Test
    public void signalRowsKeepAControlledGapRelativeToRingStroke() {
        float inner = FusionIconGeometry.innerSignalRowY(100);
        float outer = FusionIconGeometry.outerSignalRowY(100);

        assertEquals(24f, outer - inner, 0.001f);
        assertEquals(36f,
                FusionIconGeometry.outerSignalRowY(150)
                        - FusionIconGeometry.innerSignalRowY(150), 0.001f);
    }

    @Test
    public void innerAndOuterSignalDotsStayOnOppositeRingEdges() {
        float innerRow = FusionIconGeometry.innerSignalRowY(100);
        float outerRow = FusionIconGeometry.outerSignalRowY(100);
        float innerBoundary = FusionIconGeometry.RING_CENTER_Y
                + FusionIconGeometry.RING_RADIUS
                - FusionIconGeometry.RING_STROKE_WIDTH / 2f;
        float outerBoundary = FusionIconGeometry.RING_CENTER_Y
                + FusionIconGeometry.RING_RADIUS
                + FusionIconGeometry.RING_STROKE_WIDTH / 2f;

        assertTrue(FusionIconGeometry.signalY(innerRow, 1) <= innerBoundary);
        assertTrue(FusionIconGeometry.signalY(outerRow, 2) >= outerBoundary);
    }
}

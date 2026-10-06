package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FusionIconGeometryTest {
    @Test public void defaultContentTransformPreservesTheOriginalCoordinates() {
        FusionIconGeometry.ContentLayout layout = FusionIconGeometry.contentLayout(
                FusionContentConfig.defaults(), 100);
        for (int part = 0; part < 3; part++) {
            assertEquals(FusionIconGeometry.contentSourceY(part, 100), layout.centerY(part), 0.001f);
        }
    }

    @Test public void enlargedContentInAllOrdersStaysInsideTheFixedDesignSpace() {
        for (int battery = 0; battery < 3; battery++) {
            for (int network = 0; network < 3; network++) {
                if (battery == network) continue;
                FusionContentConfig config = FusionContentConfig.defaults()
                        .withPosition(FusionContentConfig.BATTERY, battery)
                        .withPosition(FusionContentConfig.NETWORK, network);
                for (int size : new int[] {50, 100, 150}) {
                    config = config.withScale(0, size).withScale(1, size).withScale(2, size);
                    FusionIconGeometry.ContentLayout layout = FusionIconGeometry.contentLayout(config, 150);
                    for (int part = 0; part < 3; part++) {
                        assertTrue(layout.top(part) >= 0f);
                        assertTrue(layout.bottom(part) < FusionIconGeometry.DESIGN_H);
                    }
                }
            }
        }
    }

    @Test public void growingOneContentPartCannotChangeOtherPartsSizesOrAnchors() {
        FusionContentConfig initial = FusionContentConfig.defaults();
        FusionIconGeometry.ContentLayout before = FusionIconGeometry.contentLayout(initial, 100);
        for (int part = 0; part < 3; part++) {
            FusionIconGeometry.ContentLayout changed = FusionIconGeometry.contentLayout(
                    initial.withScale(part, 150), 100);
            for (int other = 0; other < 3; other++) {
                if (part == other) continue;
                assertEquals(before.centerY(other), changed.centerY(other), 0.001f);
                assertEquals(before.top(other), changed.top(other), 0.001f);
                assertEquals(before.bottom(other), changed.bottom(other), 0.001f);
            }
        }
    }

    @Test public void onlyTheTopSignalBlockFlipsAndOverallSizeCannotChangeItsLayout() {
        FusionConfig config = FusionConfig.defaults()
                .withFusionContentPosition(FusionContentConfig.SIGNAL, FusionContentConfig.TOP);
        assertTrue(FusionIconGeometry.flipContent(FusionContentConfig.SIGNAL, config.fusionContent));
        org.junit.Assert.assertFalse(FusionIconGeometry.flipContent(FusionContentConfig.NETWORK,
                config.fusionContent));
        FusionIconGeometry.ContentLayout before = FusionIconGeometry.contentLayout(config.fusionContent, 100);
        FusionIconGeometry.ContentLayout after = FusionIconGeometry.contentLayout(
                config.withIconScale(50).fusionContent, 100);
        for (int part = 0; part < 3; part++) assertEquals(before.centerY(part), after.centerY(part), 0.001f);
        org.junit.Assert.assertFalse(FusionIconGeometry.flipContent(FusionContentConfig.SIGNAL,
                config.withFusionContentPosition(FusionContentConfig.SIGNAL, FusionContentConfig.MIDDLE)
                        .fusionContent));
    }

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

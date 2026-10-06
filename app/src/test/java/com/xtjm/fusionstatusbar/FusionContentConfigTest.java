package com.xtjm.fusionstatusbar;

import org.junit.Test;
import static org.junit.Assert.*;

public class FusionContentConfigTest {
    @Test public void defaultsRetainTheOriginalThreeContentPositionsAndSizes() {
        FusionContentConfig config = FusionConfig.defaults().fusionContent;
        assertEquals(0, config.batteryPosition);
        assertEquals(1, config.networkPosition);
        assertEquals(2, config.signalPosition);
        assertEquals(100, config.batteryScale);
        assertEquals(100, config.networkScale);
        assertEquals(100, config.signalScale);
    }

    @Test public void movingOnePartSwapsTheOtherPartRatherThanOverlappingIt() {
        FusionContentConfig config = FusionContentConfig.defaults()
                .withPosition(FusionContentConfig.NETWORK, FusionContentConfig.TOP);
        assertEquals(FusionContentConfig.TOP, config.networkPosition);
        assertEquals(FusionContentConfig.MIDDLE, config.batteryPosition);
        assertEquals(FusionContentConfig.BOTTOM, config.signalPosition);
        config = config.withPosition(FusionContentConfig.SIGNAL, FusionContentConfig.TOP);
        assertEquals(FusionContentConfig.TOP, config.signalPosition);
        assertEquals(FusionContentConfig.BOTTOM, config.networkPosition);
        assertEquals(FusionContentConfig.MIDDLE, config.batteryPosition);
    }

    @Test public void allSixPermutationsAreReachableAndKeepThreeDistinctSlots() {
        for (int battery = 0; battery < 3; battery++) {
            for (int network = 0; network < 3; network++) {
                if (battery == network) continue;
                FusionContentConfig config = FusionContentConfig.defaults()
                        .withPosition(FusionContentConfig.BATTERY, battery)
                        .withPosition(FusionContentConfig.NETWORK, network);
                assertEquals(battery, config.batteryPosition);
                assertEquals(network, config.networkPosition);
                assertEquals(3 - battery - network, config.signalPosition);
            }
        }
    }

    @Test public void independentSizesAndPositionsSurviveOverallSizeAndOtherEdits() {
        FusionConfig source = FusionConfig.defaults()
                .withFusionContentPosition(FusionContentConfig.SIGNAL, FusionContentConfig.TOP)
                .withFusionContentScale(FusionContentConfig.BATTERY, 75)
                .withFusionContentScale(FusionContentConfig.NETWORK, 120)
                .withFusionContentScale(FusionContentConfig.SIGNAL, 90);
        FusionConfig changed = source.withIconScale(140).withStatusBarHeight(72)
                .withDoubleRow(true).withSpanRows(true).withStrokeScale(115)
                .withStatusClockSizeSp(21).withControlCenter(ControlCenterConfig.defaults())
                .withNotificationClock(NotificationClockConfig.defaults()).withRevision(37);
        assertTrue(source.fusionContent.sameAs(changed.fusionContent));
        assertEquals(140, changed.iconScale);
        assertEquals(21, changed.statusClockSizeSp);
        assertEquals(37, changed.revision);
        FusionConfig next = changed.withFusionContentScale(FusionContentConfig.BATTERY, 1000);
        assertEquals(150, next.fusionContent.batteryScale);
        assertEquals(120, next.fusionContent.networkScale);
        assertEquals(90, next.fusionContent.signalScale);
        assertEquals(140, next.iconScale);
        assertEquals(37, next.revision);
        assertEquals(50, next.withFusionContentScale(FusionContentConfig.NETWORK, -1)
                .fusionContent.networkScale);
    }
}

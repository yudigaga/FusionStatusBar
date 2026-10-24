package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FusionIconPlacementTest {
    @Test
    public void controlCenterScaleDoesNotReadOrChangeTheStatusBarIconScale() {
        assertEquals(50, FusionIconPlacement.iconScalePercent(50, false));
        assertEquals(200, FusionIconPlacement.iconScalePercent(200, false));
        assertEquals(FusionIconPlacement.CONTROL_CENTER_ICON_SCALE,
                FusionIconPlacement.iconScalePercent(50, true));
        assertEquals(FusionIconPlacement.CONTROL_CENTER_ICON_SCALE,
                FusionIconPlacement.iconScalePercent(200, true));
    }

    @Test
    public void sizeMatchesSpanPreviewAndSystemUi() {
        assertEquals(32, FusionIconPlacement.iconHeight(48, 100));
        assertEquals(48, FusionIconPlacement.iconHeight(48, 150));
        assertEquals(16, FusionIconPlacement.iconHeight(48, 50));
    }
}

package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FusionIconPlacementTest {
    @Test
    public void sizeMatchesSpanPreviewAndSystemUi() {
        assertEquals(32, FusionIconPlacement.iconHeight(48, 100));
        assertEquals(48, FusionIconPlacement.iconHeight(48, 150));
        assertEquals(16, FusionIconPlacement.iconHeight(48, 50));
    }
}

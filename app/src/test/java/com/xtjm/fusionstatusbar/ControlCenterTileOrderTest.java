package com.xtjm.fusionstatusbar;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ControlCenterTileOrderTest {
    @Test
    public void displayOrderFiltersHiddenTilesAndKeepsUnknownTiles() {
        ControlCenterConfig settings = ControlCenterConfig.defaults()
                .withOrder("bt,wifi,unknown")
                .withHidden("cell");
        List<String> result = ControlCenterTileOrder.forDisplay(
                Arrays.asList("cell", "wifi", "other", "bt"), settings, value -> value);

        assertEquals(Arrays.asList("bt", "wifi", "other"), result);
    }

}

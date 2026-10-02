package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ControlCenterTileLayoutTest {
    @Test
    public void shapeValuesAreStoredAndReadBack() {
        String layout = ControlCenterTileLayout.set("", "wifi", 2, 1, 4);
        layout = ControlCenterTileLayout.set(layout, "cell", 2, 2, 4);

        assertEquals(2, ControlCenterTileLayout.width(layout, "wifi", 4));
        assertEquals(1, ControlCenterTileLayout.height(layout, "wifi"));
        assertEquals(2, ControlCenterTileLayout.width(layout, "cell", 4));
        assertEquals(2, ControlCenterTileLayout.height(layout, "cell"));
        assertTrue(ControlCenterTileLayout.hasShape(layout, "wifi"));
        assertFalse(ControlCenterTileLayout.hasShape(layout, "bt"));
    }

    @Test
    public void defaultShapeIsRemovedFromPersistence() {
        String layout = ControlCenterTileLayout.set("wifi=2x1", "wifi", 1, 1, 4);
        assertEquals("", layout);
        assertEquals(1, ControlCenterTileLayout.width(layout, "wifi", 4));
        assertEquals(1, ControlCenterTileLayout.height(layout, "wifi"));
        assertFalse(ControlCenterTileLayout.hasShape(layout, "wifi"));
    }

    @Test
    public void shapeValuesClampToGridBounds() {
        String layout = ControlCenterTileLayout.set("", "wifi", 9, 9, 4);
        assertEquals(4, ControlCenterTileLayout.width(layout, "wifi", 4));
        assertEquals(4, ControlCenterTileLayout.height(layout, "wifi"));
        assertEquals(3, ControlCenterTileLayout.width(layout, "wifi", 3));
    }

    @Test
    public void controlCenterConfigPreservesTileLayout() {
        ControlCenterConfig config = ControlCenterConfig.defaults()
                .withTileShape("wifi", 3, 1)
                .withTileShape("cell", 2, 2)
                .withColumns(4);

        assertEquals(3, config.tileWidth("wifi"));
        assertEquals(2, config.tileWidth("cell"));
        assertEquals(2, config.tileHeight("cell"));
        assertEquals(config.layout,
                config.withTileScale(120).withCornerRadius(20).layout);
    }

    @Test
    public void componentOneByOneIsAnExplicitOverride() {
        ControlCenterConfig settings = ControlCenterConfig.defaults()
                .withTileShape(ControlCenterComponentSpec.MEDIA, 1, 1);

        assertTrue(ControlCenterTileLayout.hasShape(settings.layout,
                ControlCenterComponentSpec.MEDIA));
        assertEquals(1, settings.tileWidth(ControlCenterComponentSpec.MEDIA));
        assertFalse(ControlCenterTileLayout.hasShape(
                settings.withoutTileShape(ControlCenterComponentSpec.MEDIA).layout,
                ControlCenterComponentSpec.MEDIA));
    }

    @Test
    public void componentPlacementPersistsIndependentlyFromOrder() {
        ControlCenterConfig settings = ControlCenterConfig.defaults()
                .withComponentOrder(ControlCenterComponentSpec.MEDIA + ","
                        + ControlCenterComponentSpec.VOLUME)
                .withComponentSide(ControlCenterComponentSpec.MEDIA, "left");

        assertEquals("left", settings.componentSide(ControlCenterComponentSpec.MEDIA));
        assertEquals("", settings.componentSide(ControlCenterComponentSpec.VOLUME));
        assertEquals(ControlCenterComponentSpec.MEDIA + ","
                        + ControlCenterComponentSpec.VOLUME,
                settings.componentOrder);
    }
}

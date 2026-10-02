package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.*;

public class ControlCenterConfigAliasTest {
    @Test public void hidingShortApplicationSpecAlsoHidesFullyQualifiedService() {
        ControlCenterConfig config = ControlCenterConfig.defaults()
                .withHidden("custom(com.example.tiles/.QuickTile)");

        assertTrue(config.isHidden("custom(com.example.tiles/com.example.tiles.QuickTile)"));
    }

    @Test public void equivalentHiddenNamesCollapseWithoutHidingAnotherService() {
        ControlCenterConfig config = ControlCenterConfig.defaults().withHidden(
                "custom(com.example.tiles/.QuickTile),custom(com.example.tiles/com.example.tiles.QUICKTILE)");
        assertEquals("custom(com.example.tiles/.quicktile)", config.hidden);
        assertTrue(config.isHidden("CUSTOM(COM.EXAMPLE.TILES/COM.EXAMPLE.TILES.QUICKTILE)"));
        assertFalse(config.isHidden("custom(com.example.tiles/.OtherTile)"));
        assertFalse(config.withHidden("").isHidden("custom(com.example.tiles/com.example.tiles.QuickTile)"));
    }

    @Test public void unrelatedComponentClassNamespacesRemainDistinct() {
        assertEquals("custom(com.example.tiles/com.other.shared.tile)",
                ControlCenterConfig.canonicalSpec("custom(com.example.tiles/com.other.shared.Tile)"));
        assertEquals("custom(com.example.tiles/.nested.tile)",
                ControlCenterConfig.canonicalSpec("custom(com.example.tiles/com.example.tiles.nested.Tile)"));
    }
}

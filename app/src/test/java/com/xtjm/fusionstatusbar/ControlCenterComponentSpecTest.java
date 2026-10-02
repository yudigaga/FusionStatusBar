package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ControlCenterComponentSpecTest {
    @Test
    public void mapsNativeComponentsWithoutConfusingTiles() {
        assertEquals(ControlCenterComponentSpec.MEDIA,
                ControlCenterComponentSpec.fromViewName(
                        "miui.systemui.controlcenter.panel.main.media.MediaPlayerPanel"));
        assertEquals(ControlCenterComponentSpec.BRIGHTNESS,
                ControlCenterComponentSpec.fromControllerName(
                        "miui.systemui.controlcenter.panel.main.brightness.BrightnessSliderController"));
        assertEquals(ControlCenterComponentSpec.MEDIA,
                ControlCenterComponentSpec.fromType(63342));
        assertEquals(ControlCenterComponentSpec.BRIGHTNESS,
                ControlCenterComponentSpec.fromType(274442));
        assertEquals(ControlCenterComponentSpec.VOLUME,
                ControlCenterComponentSpec.fromType(865269));
        assertEquals(ControlCenterComponentSpec.DEVICE_CENTER,
                ControlCenterComponentSpec.fromType(338423));
        assertEquals(ControlCenterComponentSpec.DEVICE_CONTROLS,
                ControlCenterComponentSpec.fromType(2668765));
        assertEquals(ControlCenterComponentSpec.QS_CARD,
                ControlCenterComponentSpec.fromType(22273));
        assertEquals(2, ControlCenterComponentSpec.defaultRows(
                ControlCenterComponentSpec.MEDIA));
        assertEquals(2, ControlCenterComponentSpec.defaultRows(
                ControlCenterComponentSpec.VOLUME));
        assertEquals(1, ControlCenterComponentSpec.defaultRows(
                ControlCenterComponentSpec.DEVICE_CENTER));
        assertFalse(ControlCenterComponentSpec.isSpecial("bt"));
        assertTrue(ControlCenterComponentSpec.isSpecial(ControlCenterComponentSpec.MEDIA));
    }

    @Test
    public void componentOrderOnlySwapsComponentSlots() {
        ArrayList<String> items = new ArrayList<>(Arrays.asList(
                "qs-cards", ControlCenterComponentSpec.MEDIA,
                ControlCenterComponentSpec.BRIGHTNESS,
                ControlCenterComponentSpec.VOLUME, "qs-tiles"));
        ControlCenterComponentSpec.reorder(items,
                "control:volume,control:media,control:brightness", value -> value);

        assertEquals(Arrays.asList("qs-cards", ControlCenterComponentSpec.VOLUME,
                ControlCenterComponentSpec.MEDIA, ControlCenterComponentSpec.BRIGHTNESS,
                "qs-tiles"), items);
    }
}

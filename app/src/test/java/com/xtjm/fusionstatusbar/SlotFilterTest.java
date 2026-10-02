package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SlotFilterTest {
    @Test
    public void hidesSignalTransports() {
        assertTrue(SlotFilter.shouldHide("wifi"));
        assertTrue(SlotFilter.shouldHide("WiFi"));
        assertTrue(SlotFilter.shouldHide("demo_wifi"));
        assertTrue(SlotFilter.shouldHide("slave_wifi"));
        assertTrue(SlotFilter.shouldHide("mobile"));
        assertTrue(SlotFilter.shouldHide("mobile_data"));
        assertTrue(SlotFilter.shouldHide("cell"));
        assertTrue(SlotFilter.shouldHide("cell_data"));
        assertTrue(SlotFilter.shouldHide("ethernet"));
        assertTrue(SlotFilter.shouldHide("Ethernet"));
    }

    @Test
    public void keepsExclusionsAndUnrelatedSlots() {
        assertFalse(SlotFilter.shouldHide("vowifi"));
        assertFalse(SlotFilter.shouldHide("vowifi0"));
        assertFalse(SlotFilter.shouldHide("vowifi1"));
        assertFalse(SlotFilter.shouldHide("vowifi2"));
        assertFalse(SlotFilter.shouldHide("VoWiFi"));
        assertFalse(SlotFilter.shouldHide("wifi_calling"));
        assertFalse(SlotFilter.shouldHide("hotspot"));
        assertFalse(SlotFilter.shouldHide("airplane"));
        assertFalse(SlotFilter.shouldHide("vpn"));
        assertFalse(SlotFilter.shouldHide("volte"));
        assertFalse(SlotFilter.shouldHide("hd"));
        assertFalse(SlotFilter.shouldHide("ethernet_calling"));
        assertFalse(SlotFilter.shouldHide(null));
        assertFalse(SlotFilter.shouldHide(""));
        assertFalse(SlotFilter.shouldHide("bluetooth"));
    }

    @Test
    public void wifiCallWithoutCallingWordIsHidden() {
        assertTrue(SlotFilter.shouldHide("wifi_call"));
    }

    @Test
    public void listedHyperOsSystemIconsRemainVisible() {
        String[] slots = {
                "location", "privacy", "vpn", "hotspot", "mute", "zen",
                "alarm_clock", "roaming", "headset", "phone", "tablet",
                "pc", "tv", "car", "speaker", "glasses", "camera"
        };
        for (String slot : slots) {
            assertFalse(slot, SlotFilter.shouldHide(slot));
        }
    }

    @Test
    public void bluetoothBatterySlotIsExplicitlyKept() {
        assertTrue(SlotFilter.shouldForceShow("bluetooth_handsfree_battery"));
        assertFalse(SlotFilter.shouldHide("bluetooth_handsfree_battery"));
        assertFalse(SlotFilter.shouldHide("bluetooth_battery"));
    }
}

package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SystemIconVisibilityPolicyTest {
    @Test
    public void defaultSelectionsMatchTheReferenceAndPreserveUnknownSlots() {
        String stock = "alarm_clock,phone,pad,pc,tv,car,sound_box,glasses,camera,custom_slot";
        assertEquals("alarm_clock,custom_slot,privacy",
                SystemIconVisibilityPolicy.apply(stock,
                FusionConfig.DEFAULT_SYSTEM_ICON_MASK));
    }

    @Test
    public void connectedDeviceAndAggregateChoicesExpandToTheirNativeSlots() {
        int mask = FusionConfig.SYSTEM_ICON_ALL
                & ~FusionConfig.ICON_ALARM
                & ~FusionConfig.ICON_PRIVACY
                & ~FusionConfig.ICON_PHONE
                & ~FusionConfig.ICON_HEADSET
                & ~FusionConfig.ICON_SPEAKER
                & ~FusionConfig.ICON_ROAMING
                & ~FusionConfig.ICON_PRIVACY;
        String result = SystemIconVisibilityPolicy.apply("alarm_clock,phone,custom_slot", mask);
        assertEquals("alarm_clock,phone,custom_slot,privacy,roaming,headset,wireless_headset,"
                + "sound_box,sound_box_screen,stereo,sound_box_group", result);

        String visible = SystemIconVisibilityPolicy.apply(result,
                FusionConfig.SYSTEM_ICON_ALL);
        assertEquals("custom_slot", visible);
        assertTrue((mask & FusionConfig.ICON_VOLUME) != 0);
    }
}

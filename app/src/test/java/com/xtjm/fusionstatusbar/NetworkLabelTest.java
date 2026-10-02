package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NetworkLabelTest {
    @Test
    public void lteOverridesAre4gPlus() {
        assertEquals("4G+", NetworkLabel.fromDisplayInfo(1, 13));
        assertEquals("4G+", NetworkLabel.fromDisplayInfo(2, 20));
    }

    @Test
    public void nrOverridesAre5g() {
        assertEquals("5G", NetworkLabel.fromDisplayInfo(3, 13));
        assertEquals("5G", NetworkLabel.fromDisplayInfo(4, 13));
        assertEquals("5G", NetworkLabel.fromDisplayInfo(5, 13));
    }

    @Test
    public void unknownOverrideUsesNetworkType() {
        assertEquals("4G", NetworkLabel.fromDisplayInfo(0, 13));
        assertEquals("4G", NetworkLabel.fromDisplayInfo(9, 13));
        assertEquals("", NetworkLabel.fromDisplayInfo(0, 0));
    }

    @Test
    public void namedNetworkTypes() {
        assertEquals("5G", NetworkLabel.fromNetworkType(20));
        assertEquals("4G+", NetworkLabel.fromNetworkType(19));
        assertEquals("4G", NetworkLabel.fromNetworkType(13));
        assertEquals("3G", NetworkLabel.fromNetworkType(3));
        assertEquals("3G", NetworkLabel.fromNetworkType(10));
        assertEquals("3G", NetworkLabel.fromNetworkType(15));
        assertEquals("3G", NetworkLabel.fromNetworkType(5));
        assertEquals("3G", NetworkLabel.fromNetworkType(6));
        assertEquals("3G", NetworkLabel.fromNetworkType(12));
        assertEquals("3G", NetworkLabel.fromNetworkType(14));
        assertEquals("3G", NetworkLabel.fromNetworkType(17));
        assertEquals("3G", NetworkLabel.fromNetworkType(8));
        assertEquals("3G", NetworkLabel.fromNetworkType(9));
        assertEquals("2G", NetworkLabel.fromNetworkType(1));
        assertEquals("2G", NetworkLabel.fromNetworkType(2));
        assertEquals("2G", NetworkLabel.fromNetworkType(4));
        assertEquals("2G", NetworkLabel.fromNetworkType(7));
        assertEquals("2G", NetworkLabel.fromNetworkType(11));
        assertEquals("2G", NetworkLabel.fromNetworkType(16));
        assertEquals("", NetworkLabel.fromNetworkType(18));
        assertEquals("", NetworkLabel.fromNetworkType(-1));
    }
}

package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FusionActivationStatusTest {
    @Test
    public void tracksCurrentBootOnly() {
        assertFalse(FusionActivationStatus.isSameBoot(0, 1_000_000));
        assertTrue(FusionActivationStatus.isSameBoot(1_000_000, 1_015_000));
        assertFalse(FusionActivationStatus.isSameBoot(1_000_000, 1_300_000));
    }
}

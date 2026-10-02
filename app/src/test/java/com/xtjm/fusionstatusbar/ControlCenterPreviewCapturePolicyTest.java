package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ControlCenterPreviewCapturePolicyTest {
    @Test
    public void fullSizeCaptureWithoutTilesCannotBePublished() {
        assertFalse(ControlCenterPreviewCapturePolicy.canPublish(true, 1440, 3200, 0));
    }

    @Test
    public void visibleCaptureWithTilesCanBePublished() {
        assertTrue(ControlCenterPreviewCapturePolicy.canPublish(true, 1440, 3200, 12));
    }

    @Test
    public void hiddenOrZeroSizeCaptureCannotBePublished() {
        assertFalse(ControlCenterPreviewCapturePolicy.canPublish(false, 1440, 3200, 12));
        assertFalse(ControlCenterPreviewCapturePolicy.canPublish(true, 0, 3200, 12));
    }
}

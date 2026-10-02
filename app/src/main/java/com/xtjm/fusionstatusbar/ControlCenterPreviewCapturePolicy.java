package com.xtjm.fusionstatusbar;

final class ControlCenterPreviewCapturePolicy {
    private ControlCenterPreviewCapturePolicy() {
    }

    static boolean canPublish(boolean visible, int width, int height, int tileCount) {
        return visible && width > 0 && height > 0 && tileCount > 0;
    }
}

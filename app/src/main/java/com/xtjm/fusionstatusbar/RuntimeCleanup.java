package com.xtjm.fusionstatusbar;

import android.util.Log;

/** Cleanup steps are independent because an OEM callback can throw at any point. */
final class RuntimeCleanup {
    interface Step { void run() throws Throwable; }
    private RuntimeCleanup() { }

    static void run(String label, Step step) {
        try {
            step.run();
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "Runtime cleanup failed: " + label, error);
        }
    }
}

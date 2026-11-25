package com.xtjm.fusionstatusbar;


/** Cleanup steps are independent because an OEM callback can throw at any point. */
final class RuntimeCleanup {
    interface Step { void run() throws Throwable; }
    private RuntimeCleanup() { }

    static void run(String label, Step step) {
        try {
            step.run();
        } catch (Throwable error) {
            ModuleLog.w("FusionStatusBar", "Runtime cleanup failed: " + label, error);
        }
    }
}

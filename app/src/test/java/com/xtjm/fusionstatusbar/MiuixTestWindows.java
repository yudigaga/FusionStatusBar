package com.xtjm.fusionstatusbar;

import android.app.Dialog;
import android.view.View;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

final class MiuixTestWindows {
    private MiuixTestWindows() { }

    static Dialog latest() {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        do {
            ShadowLooper.idleMainLooper(32, java.util.concurrent.TimeUnit.MILLISECONDS);
            Dialog dialog = ShadowDialog.getLatestDialog();
            if (dialog != null && dialog.isShowing()) {
                View view = dialog.getWindow().getDecorView();
                if (view.getWidth() > 0 && view.getHeight() > 0) return dialog;
            }
            try { Thread.sleep(5); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Miuix window did not become visible");
    }
}

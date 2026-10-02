package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/** Captures a settled view on main; provider state and generation-specific PNG IO stay on one worker. */
final class ControlCenterPreviewCapture {
    record Layout(String serialized, int tileCount, String catalog) { }
    private final Handler main;
    private final Supplier<View> root;
    private final Function<View, Layout> serialize;
    private final RuntimeWorkQueue work = new RuntimeWorkQueue("fusion-preview", 2);
    private final AtomicBoolean posted = new AtomicBoolean();
    private boolean writing;
    private long generation;
    private int retries;
    private String lastLayout = "";
    private WeakReference<View> lastRoot = new WeakReference<>(null);
    private int stableSamples;

    ControlCenterPreviewCapture(Handler main, Supplier<View> root, Function<View, Layout> serialize) {
        this.main = main;
        this.root = root;
        this.serialize = serialize;
    }

    void request(Context context) {
        if (context == null || !posted.compareAndSet(false, true)) return;
        main.postDelayed(() -> work.execute("state", () -> {
            Bundle state = FusionConfig.controlCenterPreviewState(context);
            main.post(() -> {
                posted.set(false);
                try { capture(context, state); }
                catch (Throwable error) {
                    Log.w("FusionStatusBar", "Control center preview capture failed", error);
                }
            });
        }), 120L);
    }

    private void capture(Context context, Bundle state) {
        if (state == null) return;
        long requested = state.getLong(FusionConfig.KEY_PREVIEW_REQUEST, 0);
        if (requested != generation) {
            generation = requested;
            retries = 0;
            stableSamples = 0;
            lastLayout = "";
            lastRoot.clear();
        }
        long ready = state.getLong(FusionConfig.KEY_PREVIEW_READY, 0);
        boolean alreadyReady = requested > 0 && requested == ready
                && !state.getString(FusionConfig.KEY_PREVIEW_URI, "").isEmpty();
        if (requested <= 0 || requested < ready || (requested == ready && !alreadyReady)) return;
        View target = root.get();
        if (target == null || !target.isShown() || target.getWidth() <= 0 || target.getHeight() <= 0) {
            retry(context, "panel not visible");
            return;
        }
        Layout layout = serialize.apply(target);
        String publishedLayout = state.getString(FusionConfig.KEY_PREVIEW_LAYOUT, "");
        String publishedCatalog = state.getString(FusionConfig.KEY_PREVIEW_CATALOG, "");
        if (alreadyReady && layout.serialized.equals(publishedLayout)
                && layout.catalog.equals(publishedCatalog)) return;
        if (!ControlCenterPreviewCapturePolicy.canPublish(target.isShown(), target.getWidth(),
                target.getHeight(), layout.tileCount)) {
            retry(context, "no visible tiles");
            return;
        }
        if (!alreadyReady && layout.catalog.isEmpty() && retries < 12) {
            retry(context, "tile catalog pending");
            return;
        }
        String captureKey = layout.serialized + "\\n" + layout.catalog;
        stableSamples = target == lastRoot.get() && captureKey.equals(lastLayout)
                ? stableSamples + 1 : 1;
        lastRoot = new WeakReference<>(target);
        lastLayout = captureKey;
        if (stableSamples < 2 || writing) {
            retry(context, writing ? "capture in progress" : "layout settling");
            return;
        }
        if (alreadyReady) {
            if (!layout.serialized.equals(publishedLayout)) {
                retry(context, "layout changed after ready");
                return;
            }
            if (!work.execute("catalog", () -> {
                FusionConfig.refreshControlCenterPreviewCatalog(context, requested, layout.catalog);
                main.post(() -> retry(context, "catalog refreshed"));
            })) retry(context, "catalog refresh queued");
            return;
        }
        int width = target.getWidth(), height = target.getHeight();
        long pixels = (long) width * height;
        if (pixels > 5_000_000L) {
            double scale = Math.sqrt(5_000_000d / pixels);
            width = Math.max(1, (int) Math.round(width * scale));
            height = Math.max(1, (int) Math.round(height * scale));
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        writing = true;
        try {
            ControlCenterWindowCapture.request(target, bitmap, main, status -> {
                try {
                    Layout fresh = serialize.apply(target);
                    boolean valid = status == PixelCopy.SUCCESS && requested == generation
                            && target.isAttachedToWindow() && target.isShown() && target == root.get()
                            && captureKey.equals(fresh.serialized + "\\n" + fresh.catalog);
                    if (!valid) {
                        finish(context, bitmap);
                        return;
                    }
                    if (!work.execute("write", () -> publish(context, requested, bitmap, layout)))
                        finish(context, bitmap);
                } catch (Throwable error) {
                    Log.w("FusionStatusBar", "Preview completion failed", error);
                    finish(context, bitmap);
                }
            });
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "Preview window copy failed", error);
            finish(context, bitmap);
        }
    }

    private void publish(Context context, long requested, Bitmap bitmap, Layout layout) {
        try {
            Bundle state = FusionConfig.controlCenterPreviewState(context);
            if (state == null || state.getLong(FusionConfig.KEY_PREVIEW_REQUEST, 0) != requested) return;
            try (OutputStream output = context.getContentResolver().openOutputStream(
                    FusionConfig.controlCenterPreviewUri(requested), "w")) {
                if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    throw new IllegalStateException("Preview PNG write failed");
            }
            FusionConfig.markControlCenterPreviewReady(context, requested, layout.serialized,
                    layout.catalog);
        } catch (Throwable error) {
            Log.w("FusionStatusBar", "Preview generation write failed", error);
        } finally { main.post(() -> finish(context, bitmap)); }
    }

    private void finish(Context context, Bitmap bitmap) {
        if (!bitmap.isRecycled()) bitmap.recycle();
        writing = false;
        retry(context, "capture completed");
    }

    private void retry(Context context, String reason) {
        if (retries++ >= 120) return;
        if (retries <= 3 || retries % 20 == 0)
            Log.i("FusionStatusBar", "Preview retry=" + retries + " reason=" + reason);
        main.postDelayed(() -> request(context), 500L);
    }
}

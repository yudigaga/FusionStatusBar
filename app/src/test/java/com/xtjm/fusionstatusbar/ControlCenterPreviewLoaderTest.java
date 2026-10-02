package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.os.Bundle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterPreviewLoaderTest {
    private static final class Worker implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void run() { while (!tasks.isEmpty()) tasks.remove().run(); }
    }
    private static final class Capture implements ControlCenterPreviewLoader.Source {
        final Bundle state = new Bundle();
        byte[] png;
        int reads;
        boolean clearDuringDecode;
        Capture() {
            state.putLong(FusionConfig.KEY_PREVIEW_REQUEST, 1);
            state.putLong(FusionConfig.KEY_PREVIEW_READY, 1);
            state.putString(FusionConfig.KEY_PREVIEW_LAYOUT, "v1|360|800|d2lmaQ==,0,0,100,100");
            Bitmap bitmap = Bitmap.createBitmap(720, 1600, Bitmap.Config.ARGB_8888);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            bitmap.recycle();
            png = output.toByteArray();
        }
        public Bundle state() { return new Bundle(state); }
        public InputStream open(long generation) {
            reads++;
            if (clearDuringDecode && reads == 2) {
                state.putLong(FusionConfig.KEY_PREVIEW_REQUEST, 2);
                state.putLong(FusionConfig.KEY_PREVIEW_READY, 2);
                state.putString(FusionConfig.KEY_PREVIEW_LAYOUT, "");
            }
            return new ByteArrayInputStream(png);
        }
    }
    private static final class Results implements ControlCenterPreviewLoader.Listener {
        int delivered, failures;
        Bitmap bitmap;
        public void loaded(long generation, Bitmap bitmap, String layout) { delivered++; this.bitmap = bitmap; }
        public void failed(long generation, Throwable error) { failures++; }
    }

    @Test public void decodeIsSizedCachedAndReleasedWhenDetached() {
        Worker worker = new Worker(); Capture source = new Capture(); Results result = new Results();
        ControlCenterPreviewLoader loader = new ControlCenterPreviewLoader(source, worker);
        loader.load(1, 360, result);
        assertEquals(0, source.reads);
        worker.run(); ShadowLooper.idleMainLooper();
        assertEquals(1, result.delivered); assertEquals(360, result.bitmap.getWidth());
        Bitmap first = result.bitmap;
        loader.load(1, 360, result); worker.run(); ShadowLooper.idleMainLooper();
        assertEquals(2, source.reads); assertSame(first, result.bitmap);
        loader.close(); assertTrue(first.isRecycled());
    }

    @Test public void clearDuringDecodeCannotRestoreAnOldCapture() {
        Worker worker = new Worker(); Capture source = new Capture(); Results result = new Results();
        source.clearDuringDecode = true;
        ControlCenterPreviewLoader loader = new ControlCenterPreviewLoader(source, worker);
        loader.load(1, 360, result); worker.run(); ShadowLooper.idleMainLooper();
        assertEquals(0, result.delivered); loader.close();
    }

    @Test public void closedScreenNeverReceivesQueuedCallback() {
        Worker worker = new Worker(); Capture source = new Capture(); Results result = new Results();
        ControlCenterPreviewLoader loader = new ControlCenterPreviewLoader(source, worker);
        loader.load(1, 360, result); worker.run(); loader.close(); ShadowLooper.idleMainLooper();
        assertEquals(0, result.delivered);
    }

    @Test public void newerRequestSuppressesOlderDecodedCallback() {
        Worker worker = new Worker(); Capture source = new Capture(); Results result = new Results();
        ControlCenterPreviewLoader loader = new ControlCenterPreviewLoader(source, worker);
        loader.load(1, 360, result); worker.run();
        source.state.putLong(FusionConfig.KEY_PREVIEW_REQUEST, 2);
        loader.load(2, 360, result); worker.run(); ShadowLooper.idleMainLooper();
        assertEquals(0, result.delivered); loader.close();
    }
}

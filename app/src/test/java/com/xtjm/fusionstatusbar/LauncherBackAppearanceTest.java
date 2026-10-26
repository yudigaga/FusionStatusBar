package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LauncherBackAppearanceTest {
    private DesktopConfig config;
    private ArrowView arrow;
    private Bitmap bitmap;
    private Canvas canvas;
    private int nativeCalls;
    private int attached;
    private final List<String> order = new ArrayList<>();
    private final Registry registry = new Registry();

    @Before public void setUp() {
        config = DesktopConfig.defaults().withBackGestureStyle(DesktopConfig.BACK_STYLE_CAPSULE)
                .withBackGestureColors(Color.GREEN, Color.RED);
        arrow = new ArrowView(RuntimeEnvironment.getApplication());
        arrow.layout(0, 0, 300, 220);
        bitmap = Bitmap.createBitmap(300, 220, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        install(ArrowView.class, registry);
    }

    @Test public void nativeStyleDrawsOriginalPixelsAndAttachesRuntimeOnlyOnce() throws Throwable {
        config = DesktopConfig.defaults();
        draw(arrow, () -> { });
        draw(arrow, () -> { });

        assertEquals(Color.MAGENTA, bitmap.getPixel(160, 110));
        assertEquals(2, nativeCalls);
        assertEquals(1, attached);
    }

    @Test public void customStyleKeepsNativeSideEffectsButReplacesPixelsAndRestoresClip()
            throws Throwable {
        Rect before = canvas.getClipBounds();
        int saves = canvas.getSaveCount();
        arrow.mIconNeedDraw = false;
        arrow.mArrowPaint.setAlpha(0);
        draw(arrow, () -> {
            order.add("native-state");
            arrow.mIconNeedDraw = true;
            arrow.mArrowPaint.setAlpha(255);
        });

        assertEquals(1, nativeCalls);
        assertEquals(List.of("native-state"), order);
        assertEquals(before, canvas.getClipBounds());
        assertEquals(saves, canvas.getSaveCount());
        assertEquals(0, count(Color.MAGENTA));
        assertTrue(count(palette().background) > 50);
        assertTrue("updated native arrow alpha must be read after its callback", count(palette().arrow) > 5);
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(200, 110));
    }

    @Test public void missingGeometryContractFallsBackToOriginalDraw() throws Throwable {
        Registry missing = new Registry();
        install(UnknownView.class, missing);
        UnknownView view = new UnknownView(RuntimeEnvironment.getApplication());
        view.layout(0, 0, 300, 220);
        missing.call(view, canvas, () -> nativePixels(() -> { }));

        assertEquals(1, nativeCalls);
        assertEquals(Color.MAGENTA, bitmap.getPixel(160, 110));
    }

    @Test public void invalidGeometryDoesNotSuppressNativePixels() throws Throwable {
        arrow.mBackgroundScale = Float.NaN;
        draw(arrow, () -> { });
        assertEquals(Color.MAGENTA, bitmap.getPixel(160, 110));
        bitmap.eraseColor(Color.TRANSPARENT);
        arrow.mBackgroundScale = .8f;
        arrow.mPosition = 2;
        draw(arrow, () -> { });

        assertEquals(2, nativeCalls);
        assertEquals(Color.MAGENTA, bitmap.getPixel(160, 110));
    }

    @Test public void nativeFailureRestoresCanvasWithoutRetryingTheOriginal() throws Throwable {
        Rect clip = canvas.getClipBounds();
        int saves = canvas.getSaveCount();
        RuntimeException failure = new IllegalStateException("native_draw_failed");
        try {
            draw(arrow, () -> { throw failure; });
            fail("native failure should propagate to protective handling");
        } catch (IllegalStateException expected) {
            assertSame(failure, expected);
        }

        assertEquals(1, nativeCalls);
        assertEquals(clip, canvas.getClipBounds());
        assertEquals(saves, canvas.getSaveCount());
        Paint paint = new Paint();
        paint.setColor(Color.CYAN);
        canvas.drawRect(190, 100, 200, 110, paint);
        assertEquals(Color.CYAN, bitmap.getPixel(195, 105));
    }

    @Test public void rightEdgeMirrorsTheCustomGeometry() throws Throwable {
        draw(arrow, () -> { });
        int[] left = new int[300 * 220];
        bitmap.getPixels(left, 0, 300, 0, 0, 300, 220);
        bitmap.eraseColor(Color.TRANSPARENT);
        arrow.mPosition = 1;
        draw(arrow, () -> { });

        BackGestureRendererTest.assertMirrored("host geometry", left, bitmap);
    }

    @Test public void nativeTriggerVisibilityAndAlphaControlTheArrowPixels() throws Throwable {
        arrow.mTriggerType = Trigger.TRIGGER_TYPE_NONE;
        draw(arrow, () -> { });
        assertEquals(0, count(palette().arrow));
        assertTrue(count(palette().background) > 50);
        bitmap.eraseColor(Color.TRANSPARENT);
        arrow.mTriggerType = Trigger.TRIGGER_TYPE_BACK;
        arrow.mIconNeedDraw = false;
        draw(arrow, () -> { });
        assertEquals(0, count(palette().arrow));
        bitmap.eraseColor(Color.TRANSPARENT);
        arrow.mIconNeedDraw = true;
        arrow.mArrowPaint.setAlpha(0);
        draw(arrow, () -> { });
        assertEquals(0, count(palette().arrow));
    }

    @Test public void styleChangeBackToNativeImmediatelyRestoresOriginalPixels() throws Throwable {
        draw(arrow, () -> { });
        assertEquals(0, count(Color.MAGENTA));
        bitmap.eraseColor(Color.TRANSPARENT);
        config = config.withBackGestureStyle(DesktopConfig.BACK_STYLE_NATIVE);
        draw(arrow, () -> { });

        assertEquals(Color.MAGENTA, bitmap.getPixel(160, 110));
        assertEquals(2, nativeCalls);
        assertEquals(1, attached);
    }

    @Test public void retainedViewAndSameSeedFollowDayNightDayWithoutReattaching() throws Throwable {
        Configuration original = new Configuration(arrow.getResources().getConfiguration());
        Configuration day = new Configuration(original);
        day.uiMode = (day.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        Configuration night = new Configuration(day);
        night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        DesktopConfig seed = config;
        try {
            arrow.getResources().updateConfiguration(day, arrow.getResources().getDisplayMetrics());
            draw(arrow, () -> { });
            int[] daytime = new int[300 * 220];
            bitmap.getPixels(daytime, 0, 300, 0, 0, 300, 220);
            int dayBackground = palette().background;
            assertTrue(count(dayBackground) > 50);
            bitmap.eraseColor(Color.TRANSPARENT);
            arrow.getResources().updateConfiguration(night, arrow.getResources().getDisplayMetrics());
            draw(arrow, () -> { });
            assertNotEquals(dayBackground, palette().background);
            assertTrue(count(palette().background) > 50);
            bitmap.eraseColor(Color.TRANSPARENT);
            arrow.getResources().updateConfiguration(day, arrow.getResources().getDisplayMetrics());
            draw(arrow, () -> { });
            int[] repeated = new int[300 * 220];
            bitmap.getPixels(repeated, 0, 300, 0, 0, 300, 220);
            assertArrayEquals(daytime, repeated);
            assertSame(seed, config);
            assertEquals(1, attached);
        } finally {
            arrow.getResources().updateConfiguration(original, arrow.getResources().getDisplayMetrics());
        }
    }

    @Test public void zeroScaleRunsNativeStateWithoutLeavingCustomPixels() throws Throwable {
        arrow.mBackgroundScale = 0;
        draw(arrow, () -> order.add("reset"));

        assertEquals(List.of("reset"), order);
        assertEquals(1, nativeCalls);
        assertEquals(300 * 220, count(Color.TRANSPARENT));
    }

    @Test public void missingDrawMethodLeavesHostUnhooked() {
        Registry missing = new Registry();
        assertEquals(0, LauncherBackAppearance.install(getClass().getClassLoader(),
                (name, loader) -> NoDrawOverride.class, missing, () -> config, context -> { }));
        assertNull(missing.hook);
    }

    private void install(Class<?> owner, Registry target) {
        assertEquals(1, LauncherBackAppearance.install(getClass().getClassLoader(),
                (name, loader) -> owner, target, () -> config, context -> attached++));
    }

    private void draw(View view, NativeCall sideEffects) throws Throwable {
        registry.call(view, canvas, () -> nativePixels(sideEffects));
    }

    private Object nativePixels(NativeCall sideEffects) throws Throwable {
        nativeCalls++;
        sideEffects.run();
        Paint paint = new Paint();
        paint.setColor(Color.MAGENTA);
        canvas.drawRect(0, 0, 300, 220, paint);
        return null;
    }

    private int count(int color) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (bitmap.getPixel(x, y) == color) count++;
            }
        }
        return count;
    }

    private BackGesturePalette palette() {
        return BackGesturePalette.resolve(config.backGestureBackgroundColor,
                config.backGestureArrowColor, BackGesturePalette.isNight(arrow.getResources()));
    }

    private interface NativeCall { void run() throws Throwable; }
    private interface Original { Object run() throws Throwable; }

    enum Trigger { TRIGGER_TYPE_NONE, TRIGGER_TYPE_BACK }

    public static final class ArrowView extends View {
        private int mPosition;
        private int mBackgroundWidth = 80;
        private float mBackgroundScale = .8f;
        private float mStartX;
        private float mStartY = 100;
        private float mExpectBackHeight = 120;
        private boolean mIconNeedDraw = true;
        private Trigger mTriggerType = Trigger.TRIGGER_TYPE_BACK;
        private Paint mArrowPaint = new Paint();

        public ArrowView(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) { }
    }

    public static final class UnknownView extends View {
        public UnknownView(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) { }
    }

    public static final class NoDrawOverride extends View {
        public NoDrawOverride(Context context) { super(context); }
    }

    private static final class Registry implements LauncherBackAppearance.Registrar {
        XposedInterface.Hooker hook;

        @Override public boolean install(Method method, XposedInterface.Hooker installed) {
            assertEquals("onDraw", method.getName());
            hook = installed;
            return true;
        }

        Object call(View owner, Canvas canvas, Original original) throws Throwable {
            XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {XposedInterface.Chain.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getThisObject" -> owner;
                        case "getArgs" -> Arrays.asList(canvas);
                        case "proceed" -> original.run();
                        default -> null;
                    });
            return hook.intercept(chain);
        }
    }
}

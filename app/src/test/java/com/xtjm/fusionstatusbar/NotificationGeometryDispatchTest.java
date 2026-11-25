package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.NotificationBackgroundView;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationGeometryDispatchTest {
    private static final int UPDATES = 120, SIZE = 160;
    private final List<Fixture> fixtures = new ArrayList<>();

    @After public void restoreFixtures() {
        for (Fixture fixture : fixtures) fixture.close();
        NotificationSurfaceHooks.setReporter(null);
        ControlCenterMaterials.clearForTest();
    }

    @Test public void fullApplyBaselineReportsTheCostOf120Refreshes() throws Throwable {
        Fixture fixture = fixture(false, ControlCenterSurface.HIGHLIGHT_GLASS);
        fixture.resetCounts();
        for (int update = 0; update < UPDATES; update++)
            NotificationSurfaceHooks.applyRow(fixture.row, fixture.current.get());
        fixture.printCounts("full_apply_120");
        assertEquals("A full apply unwraps and remounts the owned background", 240,
                fixture.background.assignments);
        assertEquals("Four captured native requests are replayed by every full apply", 480,
                fixture.background.nativeReplays);
        // Stage A removes notification content traversal independently of geometry dispatch.
        // Print its observed value so the original snapshot and the candidate stay comparable.
    }

    @Test public void allOwnedGeometryCallbacksAvoidBackgroundAssignmentsAndNativeReplay() throws Throwable {
        Fixture fixture = fixture(false, ControlCenterSurface.HIGHLIGHT_GLASS);
        Drawable bounded = fixture.background.getBackground();
        Drawable material = fixture.bounded().content;
        fixture.resetCounts();
        for (int update = 0; update < UPDATES; update++) {
            switch (update % 7) {
                case 0 -> fixture.call(fixture.row, "setActualHeight", 64 + update % 20, false);
                case 1 -> fixture.call(fixture.background, "setActualHeight", 64 + update % 20);
                case 2 -> fixture.call(fixture.background, "setActualWidth", 120 + update % 20);
                case 3 -> fixture.call(fixture.background, "setExpandAnimationRunning", update % 2 == 0);
                case 4 -> fixture.call(fixture.background, "setExpandAnimationSize", 100, 90 + update % 20);
                case 5 -> fixture.call(fixture.background, "setClipTopAmount", update % 8);
                default -> fixture.call(fixture.background, "setClipBottomAmount", update % 10);
            }
        }
        fixture.printCounts("geometry_callbacks_120");
        assertEquals("Every native setter still executes once", UPDATES,
                fixture.row.geometryCalls + fixture.background.geometryCalls);
        assertEquals("Geometry must keep the mounted bounds wrapper", 0, fixture.background.assignments);
        assertEquals("Geometry must not replay unchanged native blur requests", 0,
                fixture.background.nativeReplays);
        assertEquals("Geometry must not visit notification content", 0, fixture.content.childReads);
        assertSame(bounded, fixture.background.getBackground());
        assertSame(material, fixture.bounded().content);
    }

    @Test public void actualHeightRtlAndClipsAffectTheNextDrawWithoutWaitingForAFrame() throws Throwable {
        for (boolean night : new boolean[] {false, true}) {
            Fixture fixture = fixture(night, ControlCenterSurface.HIGHLIGHT_GLASS);
            fixture.call(fixture.row, "setActualHeight", 64, false);
            fixture.call(fixture.background, "setActualWidth", 90);
            fixture.background.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            fixture.call(fixture.background, "setClipTopAmount", 8);
            fixture.call(fixture.background, "setClipBottomAmount", 10);
            Bitmap pixels = fixture.draw();
            try {
                assertTrue(Color.alpha(pixels.getPixel(110, 30)) > 0);
                assertNoPixelsOutside(pixels, new Rect(70, 8, 160, 54));
            } finally { pixels.recycle(); }
            fixture.call(fixture.background, "setClipTopAmount", 54);
            Bitmap hidden = fixture.draw();
            try { assertEquals("Fully clipped notifications leave no reflection", 0, maximumAlpha(hidden)); }
            finally { hidden.recycle(); }
        }
    }

    @Test public void expandAnimationUsesItsCenteredBoundsImmediatelyAndIgnoresCollapsedClips() throws Throwable {
        for (boolean night : new boolean[] {false, true}) {
            Fixture fixture = fixture(night, ControlCenterSurface.HIGHLIGHT_GLASS);
            fixture.call(fixture.background, "setActualHeight", 40);
            fixture.call(fixture.background, "setClipTopAmount", 30);
            fixture.call(fixture.background, "setClipBottomAmount", 30);
            fixture.call(fixture.background, "setExpandAnimationSize", 70, 100);
            fixture.call(fixture.background, "setExpandAnimationRunning", true);
            Bitmap pixels = fixture.draw();
            try {
                assertTrue("The live expand height wins over the fully clipped collapsed height",
                        Color.alpha(pixels.getPixel(80, 70)) > 0);
                assertNoPixelsOutside(pixels, new Rect(45, 0, 115, 100));
            } finally { pixels.recycle(); }
        }
    }

    @Test public void nativeAlphaRemainsLiveAndIsAppliedOnceWithoutAnotherGeometryCallback() throws Throwable {
        for (ControlCenterSurface surface : new ControlCenterSurface[] {
                ControlCenterSurface.HIGHLIGHT_GLASS, ControlCenterSurface.DEFAULT}) {
            Fixture fixture = fixture(false, surface);
            Bitmap full = fixture.draw();
            int peak;
            try { peak = maximumAlpha(full); assertTrue(peak > 0); }
            finally { full.recycle(); }
            Drawable material = fixture.bounded().content;
            int savedAlpha = material.getAlpha();
            Rect savedBounds = new Rect(material.getBounds());
            fixture.original.setAlpha(128);
            Bitmap half = fixture.draw();
            try {
                if (surface == ControlCenterSurface.DEFAULT)
                    assertEquals("Native DEFAULT content must not be faded twice", 128,
                            Color.alpha(half.getPixel(80, 30)));
                else assertTrue("The complete glass reflection follows the live native fade",
                        maximumAlpha(half) <= Math.round(peak * .7f) + 1);
            } finally { half.recycle(); }
            assertEquals(savedAlpha, material.getAlpha());
            assertEquals(savedBounds, material.getBounds());
            assertEquals(128, fixture.original.getAlpha());
            fixture.original.setAlpha(0);
            Bitmap hidden = fixture.draw();
            try { assertEquals(0, maximumAlpha(hidden)); }
            finally { hidden.recycle(); }
        }
    }

    @Test public void changedConfigCannotBeSkippedByAnOtherwiseOwnedGeometryCallback() throws Throwable {
        Fixture fixture = fixture(false, ControlCenterSurface.HIGHLIGHT_GLASS);
        FusionConfig visible = fixture.current.get();
        ControlCenterConfig zero = fixture.current.get().controlCenter.withBlur(100, 0, 100);
        fixture.current.set(fixture.current.get().withControlCenter(zero));
        fixture.call(fixture.background, "setActualHeight", 80);
        Bitmap nativeBody = fixture.draw();
        try { assertEquals("A pending automatic-zero change preserves the native body", 255,
                maximumAlpha(nativeBody)); }
        finally { nativeBody.recycle(); }
        fixture.current.set(fixture.disabled());
        fixture.call(fixture.background, "setActualWidth", 120);
        assertSame("A pending disable must restore the native background", fixture.original,
                fixture.background.getBackground());
        assertEquals("Disabling restores the latest native request rather than a scaled replay", 120,
                fixture.background.getMiBackgroundBlurRadius());
        fixture.current.set(visible);
        fixture.call(fixture.background, "setActualHeight", 96);
        assertTrue("An unowned geometry callback must apply a newly enabled surface",
                fixture.background.getBackground() instanceof NotificationSurfaceHooks.BoundedBackground);
        Bitmap shown = fixture.draw();
        try { assertTrue(maximumAlpha(shown) > 0); }
        finally { shown.recycle(); }
    }

    @Test public void geometryDoesNotOverwriteTheLatestNativeBlurRequestNeededForRestoration() throws Throwable {
        Fixture fixture = fixture(false, ControlCenterSurface.HIGHLIGHT_GLASS);
        Object[] effective = SystemSurfaceStyle.nativeArguments(fixture.background,
                "setMiBackgroundBlurRadius", new Object[] {240});
        fixture.background.setMiBackgroundBlurRadius((Integer) effective[0]);
        fixture.resetCounts();
        fixture.call(fixture.background, "setActualHeight", 96);
        assertEquals("A geometry event leaves the host's live blur request alone", 0,
                fixture.background.nativeReplays);
        fixture.current.set(fixture.disabled());
        NotificationSurfaceHooks.refresh();
        assertEquals("Disabling replays the latest native request", 240,
                fixture.background.getMiBackgroundBlurRadius());
    }

    @Test public void losingBackgroundOwnershipFallsBackAndRestoresTheNewOemDrawable() throws Throwable {
        Fixture fixture = fixture(false, ControlCenterSurface.HIGHLIGHT_GLASS);
        ColorDrawable replacement = new ColorDrawable(Color.GREEN);
        fixture.background.mBackground = replacement;
        fixture.background.setBackground(replacement);
        fixture.resetCounts();
        fixture.call(fixture.background, "setActualHeight", 80);
        assertTrue("An external replacement must be styled through the full path",
                fixture.background.assignments > 0);
        assertTrue(fixture.background.getBackground() instanceof NotificationSurfaceHooks.BoundedBackground);
        fixture.current.set(fixture.disabled());
        NotificationSurfaceHooks.refresh();
        assertSame("Restoration must use the replacement native drawable", replacement,
                fixture.background.getBackground());
    }

    @Test public void headsUpPinnedAndDisappearingTransitionsStillSelectTheCorrectSurface() throws Throwable {
        Fixture fixture = fixture(false, ControlCenterSurface.HIGHLIGHT_GLASS);
        fixture.current.set(fixture.current.get().withControlCenter(fixture.current.get().controlCenter
                .withSystemSurfaces(SystemSurfaceConfig.defaults().withNotifications(false).withHeadsUp(true))));
        NotificationSurfaceHooks.refresh();
        assertSame(fixture.original, fixture.background.getBackground());
        for (String method : new String[] {"setHeadsUp", "setPinned", "setHeadsUpAnimatingAway"}) {
            fixture.call(fixture.row, method, true);
            assertTrue(method + " must activate the heads-up surface",
                    fixture.background.getBackground() instanceof NotificationSurfaceHooks.BoundedBackground);
            fixture.call(fixture.background, "setActualHeight", 80);
            fixture.call(fixture.row, method, false);
            assertSame(method + " must restore a disabled shade row", fixture.original,
                    fixture.background.getBackground());
        }
    }

    @Test public void refreshPreservesUnknownNativeFallbackAndReportsIndependentNotificationSwitches() throws Throwable {
        Fixture fixture = fixture(false, ControlCenterSurface.HIGHLIGHT_GLASS);
        Map<String, RuntimeApplicationPolicy.Result> reports = new HashMap<>();
        NotificationSurfaceHooks.setReporter((settings, result) -> reports.put(result.feature(), result));
        NotificationSurfaceHooks.refresh();
        RuntimeApplicationPolicy.Result shade = reports.get(FusionActivationStatus.FEATURE_NOTIFICATION_SURFACE);
        assertNotNull(shade);
        assertEquals("The final refresh report must retain the native-fallback limitation",
                FusionActivationStatus.DEGRADED, shade.state());
        assertTrue(shade.reason().contains("availability_unknown_native_fallback"));

        fixture.current.set(fixture.current.get().withControlCenter(fixture.current.get().controlCenter
                .withSystemSurfaces(SystemSurfaceConfig.defaults().withNotifications(false).withHeadsUp(true))));
        NotificationSurfaceHooks.refresh();
        assertEquals("The heads-up switch must not mark disabled shade styling as enabled", "native_restored",
                reports.get(FusionActivationStatus.FEATURE_NOTIFICATION_SURFACE).reason());
        fixture.call(fixture.row, "setHeadsUp", true);
        NotificationSurfaceHooks.refresh();
        assertEquals("A mounted heads-up fallback must also retain its degraded report",
                FusionActivationStatus.DEGRADED, reports.get(FusionActivationStatus.FEATURE_HEADS_UP).state());
        assertEquals("native_restored", reports.get(FusionActivationStatus.FEATURE_NOTIFICATION_SURFACE).reason());
    }

    @Test public void tintRadiusAndCustomBackgroundRemainSemanticUpdates() throws Throwable {
        Fixture fixture = fixture(false, ControlCenterSurface.HIGHLIGHT_GLASS);
        for (String method : new String[] {"setTint", "setRadius"}) {
            fixture.resetCounts();
            if (method.equals("setTint")) fixture.call(fixture.background, method, Color.BLUE);
            else fixture.call(fixture.background, method, 12f);
            assertTrue(method + " must retain full material application", fixture.background.assignments > 0);
            assertEquals(method + " replays the four captured native requests", 4,
                    fixture.background.nativeReplays);
        }
        ColorDrawable replacement = new ColorDrawable(Color.BLUE);
        fixture.call(fixture.background, "setCustomBackground", replacement);
        fixture.current.set(fixture.disabled());
        NotificationSurfaceHooks.refresh();
        assertSame(replacement, fixture.background.getBackground());
    }

    @Test public void clearingTheInternalDrawableHandsOnDrawBackToTheHostImmediately() throws Throwable {
        Fixture fixture = fixture(false, ControlCenterSurface.HIGHLIGHT_GLASS);
        fixture.background.mBackground = null;
        Bitmap pixels = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(pixels);
            fixture.background.getBackground().setBounds(0, 0, SIZE, SIZE);
            fixture.background.getBackground().draw(canvas);
            assertEquals("The displaced drawable must not survive behind custom drawing", 0,
                    maximumAlpha(pixels));
            int before = fixture.background.nativeDrawCalls;
            fixture.call(fixture.background, "onDraw", canvas);
            assertEquals(before + 1, fixture.background.nativeDrawCalls);
            assertEquals(Color.BLUE, pixels.getPixel(80, 30));
            fixture.current.set(fixture.disabled());
            NotificationSurfaceHooks.refresh();
            assertNull(fixture.background.getBackground());
        } finally { pixels.recycle(); }
    }

    @Test public void aTransientBoundsFailureFallsBackToFullApplicationImmediately() throws Throwable {
        Fixture fixture = fixture(false, ControlCenterSurface.HIGHLIGHT_GLASS);
        AtomicInteger attempts = new AtomicInteger();
        NotificationSurfaceHooks.install(getClass().getClassLoader(), fixture, fixture.current::get,
                ignored -> { if (attempts.getAndIncrement() == 0) throw new IllegalStateException("Transient start"); });
        fixture.current.set(fixture.current.get().withControlCenter(
                fixture.current.get().controlCenter.withBlur(100, 0, 100)));
        fixture.call(fixture.background, "setActualHeight", 80);
        assertEquals("The bounds failure retries through full application", 2, attempts.get());
        Bitmap pixels = fixture.draw();
        try { assertEquals("The pending automatic-zero change preserves the native body", 255,
                maximumAlpha(pixels)); }
        finally { pixels.recycle(); }
    }

    private Fixture fixture(boolean night, ControlCenterSurface surface) throws Throwable {
        Fixture fixture = new Fixture(night, surface);
        fixtures.add(fixture);
        return fixture;
    }

    private static void assertNoPixelsOutside(Bitmap pixels, Rect allowed) {
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
            if (!allowed.contains(x, y)) assertEquals("Unexpected drawing at " + x + "," + y,
                    0, Color.alpha(pixels.getPixel(x, y)));
    }

    private static int maximumAlpha(Bitmap pixels) {
        int peak = 0;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
            peak = Math.max(peak, Color.alpha(pixels.getPixel(x, y)));
        return peak;
    }

    private static final class Fixture implements NotificationSurfaceHooks.Registrar {
        final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();
        final AtomicReference<FusionConfig> current;
        final CountingBackground background;
        final ExpandableNotificationRow row;
        final CountingContent content;
        final ColorDrawable original = new ColorDrawable(Color.RED);

        Fixture(boolean night, ControlCenterSurface surface) throws Throwable {
            Context app = RuntimeEnvironment.getApplication();
            Configuration mode = new Configuration(app.getResources().getConfiguration());
            mode.uiMode = (mode.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            Context context = app.createConfigurationContext(mode);
            background = new CountingBackground(context);
            background.mBackground = original;
            background.setBackground(original);
            row = new ExpandableNotificationRow(context, background);
            content = new CountingContent(context);
            for (int index = 0; index < 3; index++) {
                TextView label = new TextView(context);
                label.setText("Notification " + index);
                label.setTextColor(Color.GREEN);
                content.addView(label, new FrameLayout.LayoutParams(100, 20));
            }
            row.addView(content, new FrameLayout.LayoutParams(100, 60));
            row.measure(View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY));
            row.layout(0, 0, SIZE, SIZE);
            current = new AtomicReference<>(FusionConfig.defaults().withControlCenter(
                    ControlCenterConfig.defaults().withEnabled(true).withSurface(surface)
                            .withBlur(100, 100, 100).withCornerRadius(12).withGlobalBorder(false, 1)
                            .withSystemSurfaces(SystemSurfaceConfig.defaults()
                                    .withNotifications(true).withHeadsUp(true))));
            assertTrue("The actual install path must capture host callbacks",
                    NotificationSurfaceHooks.install(getClass().getClassLoader(), this, current::get,
                            ignored -> { }) > 0);
            call(row, "onFinishInflate");
            Bitmap warm = draw(); warm.recycle();
            resetCounts();
        }

        @Override public boolean install(String id, Method method, XposedInterface.Hooker hooker) {
            hooks.put(method, hooker);
            return true;
        }

        Object call(Object owner, String name, Object... arguments) throws Throwable {
            Method nativeMethod = hooks.keySet().stream().filter(method -> method.getName().equals(name)
                    && method.getDeclaringClass().isInstance(owner)
                    && method.getParameterCount() == arguments.length
                    && (name.equals("setCustomBackground") && arguments[0] instanceof Drawable
                    ? method.getParameterTypes()[0] == Drawable.class : true)).findFirst().orElseThrow();
            XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {XposedInterface.Chain.class},
                    (proxy, called, args) -> switch (called.getName()) {
                        case "getThisObject" -> owner;
                        case "getArgs" -> Collections.unmodifiableList(Arrays.asList(arguments));
                        case "proceed" -> invoke(nativeMethod, owner, args == null || args.length == 0
                                ? arguments : (Object[]) args[0]);
                        default -> throw new UnsupportedOperationException(called.getName());
                    });
            return hooks.get(nativeMethod).intercept(chain);
        }

        private static Object invoke(Method method, Object owner, Object[] arguments) throws Throwable {
            try { return method.invoke(owner, arguments); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        }

        NotificationSurfaceHooks.BoundedBackground bounded() {
            return (NotificationSurfaceHooks.BoundedBackground) background.getBackground();
        }

        Bitmap draw() {
            Bitmap pixels = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Drawable drawable = background.getBackground();
            drawable.setBounds(0, 0, SIZE, SIZE);
            drawable.draw(new Canvas(pixels));
            return pixels;
        }

        void resetCounts() {
            background.assignments = background.nativeReplays = background.geometryCalls = 0;
            row.geometryCalls = content.childReads = 0;
        }

        void printCounts(String scenario) {
            System.out.println("NOTIFICATION_GEOMETRY_COUNTS {\"scenario\":\"" + scenario
                    + "\",\"updates\":120,\"background_assignments\":" + background.assignments
                    + ",\"native_replays\":" + background.nativeReplays
                    + ",\"content_child_reads\":" + content.childReads
                    + ",\"native_geometry_calls\":" + (row.geometryCalls + background.geometryCalls) + "}");
        }

        FusionConfig disabled() {
            return current.get().withControlCenter(current.get().controlCenter.withSystemSurfaces(
                    current.get().controlCenter.systemSurfaces.withNotifications(false).withHeadsUp(false)));
        }

        void close() { NotificationSurfaceHooks.applyRow(row, disabled()); }
    }

    private static final class CountingBackground extends NotificationBackgroundView {
        int assignments, nativeReplays;
        CountingBackground(Context context) { super(context); }
        @Override public void setBackground(Drawable value) { assignments++; super.setBackground(value); }
        @Override public void setMiViewBlurMode(int value) { nativeReplays++; super.setMiViewBlurMode(value); }
        @Override public void setMiBackgroundBlurMode(int value) { nativeReplays++; super.setMiBackgroundBlurMode(value); }
        @Override public void setMiBackgroundBlurRadius(int value) { nativeReplays++; super.setMiBackgroundBlurRadius(value); }
        @Override public void setMiBackgroundBlurScaleRatio(float value) { nativeReplays++; super.setMiBackgroundBlurScaleRatio(value); }
    }

    private static final class CountingContent extends FrameLayout {
        int childReads;
        CountingContent(Context context) { super(context); }
        @Override public View getChildAt(int index) { childReads++; return super.getChildAt(index); }
    }
}

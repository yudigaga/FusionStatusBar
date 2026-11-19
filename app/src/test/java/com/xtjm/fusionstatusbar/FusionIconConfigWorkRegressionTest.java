package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Counts actual setter/common-entry work; callback counts are not device frame timings. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FusionIconConfigWorkRegressionTest {
    private final Map<Field, Object> previousStatics = new LinkedHashMap<>();

    @After public void restoreOnlyTheCommonEntryFields() throws Exception {
        for (Map.Entry<Field, Object> saved : previousStatics.entrySet()) {
            saved.getKey().set(null, saved.getValue());
        }
    }

    @Test public void repeatedSameConfigIdentityKeepsRealDrawCacheAndAvoidsExtraWork()
            throws Exception {
        CountingFusionIconView icon = icon();
        FusionConfig config = baseConfig();
        Bitmap before = warmCellular(icon, config);
        Object layout = state(icon, "contentLayout");
        icon.resetCounts();

        repeat120(icon, () -> icon.setDisplayConfig(config));

        assertNoRepeatedWork(icon, layout);
        assertTrue("The same configuration and status must retain the rendered pixels",
                before.sameAs(draw(icon)));
    }

    @Test public void ensureFusionIconWithNetOffAvoidsRepeatedConfigWorkFor120Callbacks()
            throws Exception {
        assertCommonEntryWork(false);
    }

    @Test public void ensureFusionIconWithNetOnAvoidsRepeatedConfigWorkFor120Callbacks()
            throws Exception {
        assertCommonEntryWork(true);
    }

    @Test public void anEqualValuedNewConfigObjectStillAppliesAndInvalidatesTheDrawCache()
            throws Exception {
        CountingFusionIconView icon = icon();
        FusionConfig original = baseConfig();
        Bitmap before = warmCellular(icon, original);
        Object oldLayout = state(icon, "contentLayout");
        FusionConfig equalValues = baseConfig();
        assertNotSame(original, equalValues);
        icon.resetCounts();

        icon.setDisplayConfig(equalValues);

        assertAppliedOnce(icon, equalValues, oldLayout);
        assertTrue("Equal values still render the same pixels after actual reapplication",
                before.sameAs(draw(icon)));
        assertFalse((Boolean) state(icon, "networkTextDirty"));
    }

    @Test public void aReboundNewViewStillAppliesTheExistingConfigObject() throws Exception {
        FusionConfig config = baseConfig().withStrokeScale(120);
        CountingFusionIconView oldIcon = icon();
        Bitmap oldPixels = warmCellular(oldIcon, config);
        CountingFusionIconView rebound = icon();
        Object initialLayout = state(rebound, "contentLayout");
        rebound.resetCounts();

        rebound.setDisplayConfig(config);

        assertAppliedOnce(rebound, config, initialLayout);
        assertEquals(700, ((Paint) state(rebound, "text")).getTypeface().getWeight());
        render(rebound, false, "5G");
        assertTrue("The current object must initialize the replacement view fully",
                oldPixels.sameAs(draw(rebound)));
    }

    @Test public void strokeScaleContentOrderAndSpanningChangesContinueToApply()
            throws Exception {
        CountingFusionIconView icon = icon();
        FusionConfig base = baseConfig();
        Bitmap before = warmCellular(icon, base);

        FusionConfig stroke = base.withStrokeScale(130);
        applyAndCheck(icon, stroke);
        assertEquals(750, ((Paint) state(icon, "text")).getTypeface().getWeight());
        assertFalse("A real stroke change must change the raster", before.sameAs(draw(icon)));

        FusionConfig content = stroke.withFusionContentPosition(
                FusionContentConfig.NETWORK, FusionContentConfig.TOP)
                .withFusionContentScale(FusionContentConfig.NETWORK, 125);
        Bitmap beforeOrder = draw(icon);
        applyAndCheck(icon, content);
        FusionIconGeometry.ContentLayout layout =
                (FusionIconGeometry.ContentLayout) state(icon, "contentLayout");
        assertTrue(layout.centerY(FusionContentConfig.NETWORK)
                < layout.centerY(FusionContentConfig.BATTERY));
        assertFalse("Content order and scale still affect pixels", beforeOrder.sameAs(draw(icon)));

        FusionConfig scaled = content.withIconScale(150);
        applyAndCheck(icon, scaled);
        measureInOneHostRow(icon);
        assertEquals("150 percent fits all 24 available pixels", 24, icon.getMeasuredHeight());

        FusionConfig spanning = scaled.withDoubleRow(true).withSpanRows(true).withStatusBarHeight(48);
        applyAndCheck(icon, spanning);
        measureInOneHostRow(icon);
        assertEquals("Spanning remains independent of the host's 24px row", 48,
                icon.getMeasuredHeight());
    }

    @Test public void nullStillAppliesFreshDefaultsOnEveryCall()
            throws Exception {
        CountingFusionIconView icon = icon();
        warmCellular(icon, baseConfig().withStrokeScale(150).withIconScale(150));
        for (int callback = 0; callback < 2; callback++) {
            Object previousConfig = state(icon, "displayConfig");
            Object previousLayout = state(icon, "contentLayout");
            icon.resetCounts();

            icon.setDisplayConfig(null);

            FusionConfig applied = (FusionConfig) state(icon, "displayConfig");
            assertNotNull(applied);
            assertNotSame(previousConfig, applied);
            assertEquals(FusionConfig.defaults().strokeScale, applied.strokeScale);
            assertEquals(FusionConfig.defaults().iconScale, applied.iconScale);
            assertAppliedOnce(icon, applied, previousLayout);
            draw(icon);
            assertFalse((Boolean) state(icon, "networkTextDirty"));
        }
    }

    @Test public void statusChangesStillRenderWifiAndNewNetworkLabelsUnderTheSameConfig()
            throws Exception {
        CountingFusionIconView icon = icon();
        FusionConfig config = baseConfig().withFusionContentPosition(
                FusionContentConfig.NETWORK, FusionContentConfig.TOP);
        Bitmap cellular5g = warmCellular(icon, config);
        FusionIconGeometry.ContentLayout cellularLayout =
                (FusionIconGeometry.ContentLayout) state(icon, "contentLayout");
        icon.resetCounts();
        icon.setDisplayConfig(config);
        assertNoRepeatedWork(icon, cellularLayout);

        render(icon, true, "");
        assertSame(config, state(icon, "displayConfig"));
        FusionIconGeometry.ContentLayout wifiLayout =
                (FusionIconGeometry.ContentLayout) state(icon, "contentLayout");
        assertNotSame(cellularLayout, wifiLayout);
        assertNotEquals(cellularLayout.centerY(FusionContentConfig.NETWORK),
                wifiLayout.centerY(FusionContentConfig.NETWORK), 0f);
        assertFalse("Wi-Fi must replace the cellular label pixels", cellular5g.sameAs(draw(icon)));

        render(icon, false, "LTE");
        assertTrue("A real label change dirties the path cache",
                (Boolean) state(icon, "networkTextDirty"));
        Bitmap cellularLte = draw(icon);
        assertFalse((Boolean) state(icon, "networkTextDirty"));
        assertFalse("LTE must update the actual network glyph pixels", cellular5g.sameAs(cellularLte));
        Object latestLayout = state(icon, "contentLayout");
        icon.resetCounts();
        icon.setDisplayConfig(config);
        assertNoRepeatedWork(icon, latestLayout);
    }

    private void assertCommonEntryWork(boolean netEnabled) throws Exception {
        FusionConfig config = baseConfig().withTelemetry(baseConfig().telemetry
                .withEnabled(TelemetryConfig.NET_SPEED, netEnabled));
        Resources named = new NamedResources(RuntimeEnvironment.getApplication().getResources());
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public Resources getResources() { return named; }
        };
        FrameLayout systemIcons = new FrameLayout(context);
        systemIcons.setId(named.getIdentifier("system_icons", "id", "com.android.systemui"));
        TintBattery battery = new TintBattery(context);
        battery.setId(named.getIdentifier("battery", "id", "com.android.systemui"));
        CountingFusionIconView icon = new CountingFusionIconView(context);
        battery.addView(icon);
        systemIcons.addView(battery);
        assertFalse(icon.isAttachedToWindow());
        replaceStatic("currentConfig", config);
        replaceStatic("currentPlan", StatusBarLayoutPlan.from(config));
        replaceStatic("batteryClass", TintBattery.class);
        Method ensure = SystemUiHooks.class.getDeclaredMethod("ensureFusionIcon", View.class);
        ensure.setAccessible(true);
        icon.resetCounts();
        ensure.invoke(null, battery);
        assertTrue("The real common entry must apply its initially different config",
                icon.requests > 0);
        assertSame(config, state(icon, "displayConfig"));
        // The fixture stays unattached: no provider observer, StatusMonitor or sampler starts.
        warmCellular(icon, config);
        Object layout = state(icon, "contentLayout");
        icon.resetCounts();

        repeat120(icon, () -> ensure.invoke(null, battery));

        assertSame(icon, battery.getChildAt(0));
        assertEquals(1, battery.getChildCount());
        assertNoRepeatedWork(icon, layout);
        assertEquals(netEnabled, ((FusionConfig) state(icon, "displayConfig"))
                .telemetry.enabled(TelemetryConfig.NET_SPEED));
    }

    private static FusionConfig baseConfig() {
        return FusionConfig.defaults().withDoubleRow(false).withStatusBarHeight(40).withIconScale(100);
    }

    private static CountingFusionIconView icon() {
        return new CountingFusionIconView(RuntimeEnvironment.getApplication());
    }

    private static Bitmap warmCellular(CountingFusionIconView icon, FusionConfig config)
            throws Exception {
        icon.setDisplayConfig(config);
        render(icon, false, "5G");
        Bitmap bitmap = draw(icon);
        assertFalse("The real draw must prepare the network path cache",
                (Boolean) state(icon, "networkTextDirty"));
        assertFalse("Real native glyph ink is required",
                ((RectF) state(icon, "networkTextBounds")).isEmpty());
        assertTrue((Float) state(icon, "networkReferenceWidth") > 0f);
        return bitmap;
    }

    private static void render(FusionIconView icon, boolean wifi, String label) throws Exception {
        Method render = FusionIconView.class.getDeclaredMethod("render", StatusMonitor.Snapshot.class);
        render.setAccessible(true);
        render.invoke(icon, new StatusMonitor.Snapshot(80, wifi, label, 2, 3, 4, false));
    }

    private static Bitmap draw(FusionIconView icon) {
        icon.layout(0, 0, 271, 282);
        Bitmap bitmap = Bitmap.createBitmap(271, 282, Bitmap.Config.ARGB_8888);
        icon.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void measureInOneHostRow(FusionIconView icon) {
        icon.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(24, View.MeasureSpec.EXACTLY));
    }

    private static void applyAndCheck(CountingFusionIconView icon, FusionConfig config)
            throws Exception {
        Object oldLayout = state(icon, "contentLayout");
        icon.resetCounts();
        icon.setDisplayConfig(config);
        assertAppliedOnce(icon, config, oldLayout);
    }

    private static void assertAppliedOnce(CountingFusionIconView icon, FusionConfig config,
            Object previousLayout) throws Exception {
        assertSame(config, state(icon, "displayConfig"));
        assertEquals(1, icon.requests);
        assertEquals(1, icon.invalidations);
        assertNotSame(previousLayout, state(icon, "contentLayout"));
        assertTrue((Boolean) state(icon, "networkTextDirty"));
    }

    private static void assertNoRepeatedWork(CountingFusionIconView icon, Object originalLayout)
            throws Exception {
        assertEquals("Stable config must not request another layout", 0, icon.requests);
        assertEquals("Stable config must not invalidate again", 0, icon.invalidations);
        assertSame("Stable config must retain its content layout object", originalLayout,
                state(icon, "contentLayout"));
        assertFalse("Stable config must retain the genuinely prepared network path cache",
                (Boolean) state(icon, "networkTextDirty"));
    }

    private static void repeat120(CountingFusionIconView icon, Callback callback) throws Exception {
        Object previousLayout = state(icon, "contentLayout");
        int layoutReplacements = 0;
        int dirtyCallbacks = 0;
        for (int invocation = 0; invocation < 120; invocation++) {
            callback.run();
            Object currentLayout = state(icon, "contentLayout");
            if (currentLayout != previousLayout) layoutReplacements++;
            previousLayout = currentLayout;
            if ((Boolean) state(icon, "networkTextDirty")) dirtyCallbacks++;
        }
        String counts = "requests=" + icon.requests + " invalidations=" + icon.invalidations
                + " layoutReplacements=" + layoutReplacements + " dirtyCallbacks=" + dirtyCallbacks;
        System.out.println("FUSION_CONFIG_WORK_COUNTS callbacks=120 " + counts);
        assertEquals(counts, 0, layoutReplacements);
        assertEquals(counts, 0, dirtyCallbacks);
    }

    @FunctionalInterface private interface Callback { void run() throws Exception; }

    private static Object state(FusionIconView icon, String name) throws Exception {
        Field field = FusionIconView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(icon);
    }

    private void replaceStatic(String name, Object value) throws Exception {
        Field field = SystemUiHooks.class.getDeclaredField(name);
        field.setAccessible(true);
        previousStatics.putIfAbsent(field, field.get(null));
        field.set(null, value);
    }

    /** Count only actual View methods; the production config setter is never overridden. */
    private static final class CountingFusionIconView extends FusionIconView {
        int requests;
        int invalidations;
        CountingFusionIconView(Context context) { super(context); }
        @Override public void requestLayout() { requests++; super.requestLayout(); }
        @Override public void invalidate() { invalidations++; super.invalidate(); }
        void resetCounts() { requests = invalidations = 0; }
    }

    /** Supply the host tint fields so the common entry need not exercise warning fallback. */
    private static final class TintBattery extends FrameLayout {
        final boolean mUseTint = false;
        final float mDarkIntensity = 0f;
        final int mLightColor = 0xffffffff;
        final int mDarkColor = 0xff000000;
        TintBattery(Context context) { super(context); }
    }

    private static final class NamedResources extends Resources {
        @SuppressWarnings("deprecation") NamedResources(Resources base) {
            super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
        }
        @Override public int getIdentifier(String name, String type, String packageName) {
            if ("id".equals(type) && "com.android.systemui".equals(packageName)) {
                if ("system_icons".equals(name)) return 0x7f0e0103;
                if ("battery".equals(name)) return 0x7f0e0105;
            }
            return super.getIdentifier(name, type, packageName);
        }
    }
}

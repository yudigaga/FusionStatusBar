package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.systemui.statusbar.views.MiuiStatusBatteryContainer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/**
 * Characterizes the real module install/apply path, not a screenshot or an OEM replay.
 * The existing battery identity stub is a LinearLayout; OEM manual icon measurement,
 * native visibility hooks, cutout callbacks and compositor output are outside this fixture.
 * Central free space is recorded separately from readout-frame and rasterized-text bounds.
 * Frozen-layout observations do not establish a requirement to keep equal columns or to uninstall.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NetworkLeadingGapLayoutContractTest {
    private static final int LEFT = 0x7f0e4101;
    private static final int SYSTEM = 0x7f0e4102;
    private static final int BATTERY = 0x7f0e4103;
    private static final int STATUS_ICONS = 0x7f0e4104;
    private static final int CLOCK = 0x7f0e4105;
    private static final int WIDTH = 960;
    private static final int HEIGHT = 80;
    private final Map<Field, Object> savedSamplerFields = new LinkedHashMap<>();
    private Map<Object, Object> layoutStates;
    private Map<Object, Object> savedLayoutStates;
    private ActivityController<Activity> activityController;
    private FrameLayout window;

    @Before public void isolateLayoutRegistryAndSamplingBoundary() throws Exception {
        Field states = field(DualRowStatusBarLayout.class, "STATES");
        @SuppressWarnings("unchecked") Map<Object, Object> current =
                (Map<Object, Object>) states.get(null);
        layoutStates = current;
        savedLayoutStates = new LinkedHashMap<>(current);
        current.clear();
        for (String name : new String[] {"config", "latest", "interactive",
                "lastInteractiveCheck", "appContext", "polling", "generation"}) {
            Field samplerField = field(TelemetrySampler.class, name);
            savedSamplerFields.put(samplerField, samplerField.get(null));
        }
        activityController = Robolectric.buildActivity(Activity.class).setup();
        window = new FrameLayout(activityController.get());
        activityController.get().setContentView(window);
    }

    @After public void restoreLayoutRegistryAndSamplingBoundary() throws Exception {
        try {
            if (window != null) window.removeAllViews();
            if (activityController != null) activityController.pause().stop().destroy();
        } finally {
            for (Map.Entry<Field, Object> saved : savedSamplerFields.entrySet()) {
                saved.getKey().set(null, saved.getValue());
            }
            if (layoutStates != null) {
                layoutStates.clear();
                layoutStates.putAll(savedLayoutStates);
            }
        }
    }

    @Test public void centralFreeSpaceIsReachableWithoutAnyHiddenNativeIconSlot() throws Exception {
        for (boolean originalTwoColumns : new boolean[] {false, true}) {
            for (int direction : directions()) {
                Fixture fixture = new Fixture(originalTwoColumns, direction);
                fixture.apply(enabled(false, TelemetryConfig.SIDE_RIGHT, 0,
                        TelemetryConfig.ALIGN_DEFAULT));
                fixture.measureAndLayout();
                Snapshot snapshot = fixture.capture("first-installed");
                fixture.assertNativeContentVisible();
                assertTrue(snapshot.toString(), snapshot.twoEqualColumns);
                assertTrue("Left text stops before its column ends: " + snapshot,
                        fixture.end(snapshot.leftInk) < fixture.end(snapshot.leftColumn));
                assertTrue("The right readout starts after its column starts: " + snapshot,
                        fixture.start(snapshot.readoutFrame) > fixture.start(snapshot.rightColumn));
                fixture.assertClockToReadoutGap(snapshot);
            }
        }
    }

    @Test public void autoAndFixedAlignmentRecordTextInkSeparatelyFromTheReadoutFrame()
            throws Exception {
        for (int direction : directions()) {
            for (int side : new int[] {TelemetryConfig.SIDE_LEFT, TelemetryConfig.SIDE_RIGHT}) {
                Fixture fixture = new Fixture(false, direction);
                for (int width : new int[] {0, 140}) {
                    Snapshot previousAlignment = null;
                    for (int alignment : new int[] {TelemetryConfig.ALIGN_LEFT,
                            TelemetryConfig.ALIGN_CENTER, TelemetryConfig.ALIGN_RIGHT}) {
                        fixture.apply(enabled(false, side, width, alignment));
                        fixture.measureAndLayout();
                        Snapshot snapshot = fixture.capture("width=" + width + ",align=" + alignment);
                        StatusPairView readout = fixture.readout();
                        assertNotNull(snapshot.toString(), readout);
                        fixture.assertParagraphAlignment(alignment);
                        assertEquals(snapshot.toString(), width == 0 ? readout.naturalWidth() : width,
                                readout.getWidth());
                        assertTrue(snapshot.toString(), snapshot.readoutFrame.contains(snapshot.readoutInk));
                        if (width > 0) {
                            if (previousAlignment != null) {
                                assertFixedParagraphAdvance(previousAlignment, snapshot);
                            }
                            previousAlignment = snapshot;
                        }
                        fixture.assertNativeContentVisible();
                    }
                }
            }
        }
    }

    @Test public void initialApplyPrecedesMeasurementAndTheSecondNormalPassKeepsThePack()
            throws Exception {
        for (boolean originalTwoColumns : new boolean[] {false, true}) {
            for (int direction : directions()) {
                for (int side : new int[] {TelemetryConfig.SIDE_LEFT, TelemetryConfig.SIDE_RIGHT}) {
                    for (boolean doubleRow : new boolean[] {false, true}) {
                        Fixture fixture = new Fixture(originalTwoColumns, direction);
                        FusionConfig config = enabled(doubleRow, side, 0, TelemetryConfig.ALIGN_DEFAULT);
                        assertEquals(0, fixture.root.getMeasuredWidth());
                        fixture.apply(config);
                        fixture.measureAndLayout();
                        Snapshot first = fixture.capture("apply-before-measure,doubleRow=" + doubleRow);
                        fixture.apply(config);
                        fixture.measureAndLayout();
                        Snapshot second = fixture.capture("second-normal-pass,doubleRow=" + doubleRow);
                        assertEquals(first.toString() + " -> " + second,
                                first.readoutFrame, second.readoutFrame);
                        assertEquals(first.toString() + " -> " + second,
                                first.nativeFrame, second.nativeFrame);
                        fixture.assertNativeContentVisible();
                    }
                }
            }
        }
    }

    @Test public void changingOneSpeedDirectionOnlyWritesThatLine() throws Exception {
        Fixture fixture = new Fixture(false, View.LAYOUT_DIRECTION_LTR);
        fixture.apply(enabled(false, TelemetryConfig.SIDE_RIGHT, 140,
                TelemetryConfig.ALIGN_DEFAULT));
        fixture.measureAndLayout();
        StatusPairView readout = fixture.readout();
        TextView upload = (TextView) readout.getChildAt(0);
        TextView download = (TextView) readout.getChildAt(1);
        assertEquals(View.VISIBLE, download.getVisibility());
        TextWrites uploadWrites = new TextWrites(upload);
        TextWrites downloadWrites = new TextWrites(download);

        DualRowStatusBarLayout.updateTelemetry(new TelemetryReadings(
                null, null, null, null, 1, 1024, true));
        assertEquals("\u21911B/s", upload.getText().toString());
        assertEquals(1, uploadWrites.count);
        assertEquals("An unchanged download must not be written", 0, downloadWrites.count);

        DualRowStatusBarLayout.updateTelemetry(new TelemetryReadings(
                null, null, null, null, 1, 2, true));
        assertEquals("\u21932B/s", download.getText().toString());
        assertEquals("\u21911B/s \u21932B/s", readout.getContentDescription().toString());
        assertEquals("An unchanged upload must not be written again", 1, uploadWrites.count);
        assertEquals(1, downloadWrites.count);
    }

    @Test public void measuredFixedWidthSurvivesAStableConfigurationResync() throws Exception {
        Fixture fixture = new Fixture(false, View.LAYOUT_DIRECTION_LTR);
        FusionConfig config = enabled(false, TelemetryConfig.SIDE_RIGHT, 140,
                TelemetryConfig.ALIGN_DEFAULT);
        fixture.apply(config);
        fixture.measureAndLayout();
        StatusPairView readout = fixture.readout();
        assertEquals(140, readout.getLayoutParams().width);
        assertFalse(readout.isLayoutRequested());

        // State sees the newly measured root dimensions on this second apply.
        fixture.apply(config);

        assertSame(readout, fixture.readout());
        assertEquals(140, readout.getLayoutParams().width);
        assertFalse("A stable readout must not request another layout", readout.isLayoutRequested());
    }

    @Test public void onlyTheSlotWithReadoutsMeasuresNativeVisualBounds() throws Exception {
        Fixture fixture = new Fixture(false, View.LAYOUT_DIRECTION_LTR);
        fixture.battery.setClipChildren(false);
        for (int side : new int[] {TelemetryConfig.SIDE_RIGHT, TelemetryConfig.SIDE_LEFT}) {
            fixture.apply(enabled(false, side, 140, TelemetryConfig.ALIGN_DEFAULT));
            fixture.measureAndLayout();
            View nativeSlot = (View) fixture.battery.getParent();
            fixture.firstIcon.transformReads = 0;
            nativeSlot.layout(nativeSlot.getLeft(), nativeSlot.getTop() + 1,
                    nativeSlot.getRight(), nativeSlot.getBottom() + 1);
            if (side == TelemetryConfig.SIDE_RIGHT) {
                assertTrue("A neighboring readout must still reserve native visual bounds",
                        fixture.firstIcon.transformReads > 0);
            } else {
                assertEquals("The native-only slot must skip visual bounds work",
                        0, fixture.firstIcon.transformReads);
            }
        }
    }

    @Test public void autoSpeedGrowsAndShrinksWithoutRequestingTheStatusBarRoot() throws Exception {
        TelemetryReadings slow = new TelemetryReadings(null, null, null, null, 1, 2, true);
        TelemetryReadings fast = new TelemetryReadings(null, null, null, null,
                123L * 1024 * 1024, 246L * 1024 * 1024, true);
        for (int direction : directions()) {
            for (int position : new int[] {0, 2, 3}) {
                Fixture fixture = new Fixture(false, direction);
                FusionConfig config = enabled(true, TelemetryConfig.side(position), 0,
                        TelemetryConfig.ALIGN_DEFAULT);
                fixture.apply(config.withTelemetry(config.telemetry
                        .withPosition(TelemetryConfig.NET_SPEED, position)
                        .withLeftMargin(TelemetryConfig.NET_SPEED, 3)
                        .withRightMargin(TelemetryConfig.NET_SPEED, 5)));
                layoutWindow();
                StatusPairView readout = fixture.readout();
                View slot = (View) readout.getParent();
                RectF nativeFrame = fixture.bounds(fixture.battery);
                TextView upload = (TextView) readout.getChildAt(0);
                int wideWidth = 0;
                for (TelemetryReadings readings : new TelemetryReadings[] {fast, slow, fast}) {
                    String phase = " direction=" + direction + " position=" + position
                            + " B/s=" + readings.txBytesPerSecond;
                    assertFalse("The host root must begin with a completed layout",
                            fixture.root.isLayoutRequested());
                    fixture.root.layoutRequests = 0;

                    DualRowStatusBarLayout.updateTelemetry(readings);

                    assertEquals("A width-changing sample must stay within its status slot" + phase,
                            0, fixture.root.layoutRequests);
                    assertEquals(readings == slow ? "\u21911B/s"
                            : String.format("\u2191%.0fMB/s", 123.0), upload.getText().toString());
                    assertEquals(nativeFrame, fixture.bounds(fixture.battery));
                    assertEquals(readout.naturalWidth(), readout.getWidth());
                    assertFalse(fixture.textInk(upload).isEmpty());
                    if (readings == slow) assertTrue(readout.getWidth() < wideWidth);
                    else wideWidth = readout.getWidth();
                    RectF updatedFrame = fixture.bounds(readout);
                    slot.requestLayout();
                    layoutWindow();
                    assertEquals("A framework traversal must agree with the local result",
                            updatedFrame, fixture.bounds(readout));
                    assertEquals(nativeFrame, fixture.bounds(fixture.battery));
                }
            }
        }
    }

    @Test public void pendingHostLayoutStillUsesTheNormalReadoutAllocation() throws Exception {
        Fixture fixture = new Fixture(false, View.LAYOUT_DIRECTION_LTR);
        fixture.apply(enabled(false, TelemetryConfig.SIDE_RIGHT, 0, TelemetryConfig.ALIGN_DEFAULT));
        layoutWindow();
        StatusPairView readout = fixture.readout();
        int oldWidth = readout.getWidth();
        fixture.battery.getLayoutParams().width = 180;
        fixture.battery.requestLayout();

        DualRowStatusBarLayout.updateTelemetry(new TelemetryReadings(null, null, null, null,
                123L * 1024 * 1024, 246L * 1024 * 1024, true));

        assertTrue(fixture.root.isLayoutRequested());
        assertEquals("Leave sizing to the pending host traversal", oldWidth, readout.getWidth());
        layoutWindow();
        assertEquals(180, fixture.battery.getWidth());
        assertEquals(readout.naturalWidth(), readout.getWidth());
        assertTrue(readout.getWidth() > oldWidth);
    }

    @Test public void anOccludedSpeedReadoutKeepsLatestTextWithoutRequestingRootLayout() throws Exception {
        for (int fixedWidth : new int[] {0, 120}) {
            Fixture fixture = new Fixture(false, View.LAYOUT_DIRECTION_LTR);
            fixture.apply(enabled(false, TelemetryConfig.SIDE_RIGHT, fixedWidth,
                    TelemetryConfig.ALIGN_DEFAULT));
            fixture.battery.getLayoutParams().width = WIDTH / 2;
            layoutWindow();
            StatusPairView readout = fixture.readout();
            assertEquals(0, readout.getWidth());
            fixture.root.layoutRequests = 0;

            DualRowStatusBarLayout.updateTelemetry(new TelemetryReadings(
                    null, null, null, null, 1, 2, true));
            DualRowStatusBarLayout.updateTelemetry(new TelemetryReadings(
                    null, null, null, null, 4, 7, true));

            assertEquals("An occluded readout must not request ancestor layout", 0,
                    fixture.root.layoutRequests);
            assertEquals(0, readout.getWidth());
            assertEquals("\u21914B/s \u21937B/s", readout.getContentDescription());

            fixture.battery.getLayoutParams().width = 100;
            fixture.battery.requestLayout();
            layoutWindow();
            TextView upload = (TextView) readout.getChildAt(0);
            assertEquals("\u21914B/s", upload.getText().toString());
            assertEquals(fixedWidth == 0 ? readout.naturalWidth() : fixedWidth, readout.getWidth());
            assertFalse(fixture.textInk(upload).isEmpty());
        }
    }

    private void layoutWindow() {
        View decor = activityController.get().getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, WIDTH, HEIGHT);
        assertFalse(decor.isLayoutRequested());
        decor.getViewTreeObserver().dispatchOnPreDraw();
    }

    @Test public void screenWakeStartsWithUnavailableSpeedUntilNewCountersArrive() throws Exception {
        Fixture fixture = new Fixture(false, View.LAYOUT_DIRECTION_LTR);
        fixture.apply(enabled(false, TelemetryConfig.SIDE_RIGHT, 0,
                TelemetryConfig.ALIGN_DEFAULT));
        fixture.measureAndLayout();
        StatusPairView readout = fixture.readout();
        assertTrue(TelemetrySampler.latest().speedValid);
        Method interactive = TelemetrySampler.class.getDeclaredMethod("updateInteractive", boolean.class);
        interactive.setAccessible(true);

        interactive.invoke(null, false);
        assertEquals(View.GONE, readout.getVisibility());
        field(TelemetrySampler.class, "polling").set(null, IDLE_POLLING);
        interactive.invoke(null, true);

        assertEquals(View.VISIBLE, readout.getVisibility());
        assertEquals("\u2191-- \u2193--", readout.getContentDescription());
        assertFalse(TelemetrySampler.latest().speedValid);
        assertEquals(TelemetryReadings.SAMPLE.batteryCelsius, TelemetrySampler.latest().batteryCelsius);
        fixture.measureAndLayout();
        DualRowStatusBarLayout.updateTelemetry(new TelemetryReadings(
                null, null, null, null, 4, 7, true));
        fixture.measureAndLayout();
        assertEquals("\u21914B/s \u21937B/s", readout.getContentDescription());
    }

    @Test public void disablingRecordsTheCurrentColumnContractWithoutRequiringAnUninstall()
            throws Exception {
        for (boolean originalTwoColumns : new boolean[] {false, true}) {
            for (int direction : directions()) {
                for (int side : new int[] {TelemetryConfig.SIDE_LEFT, TelemetryConfig.SIDE_RIGHT}) {
                    Fixture fixture = new Fixture(originalTwoColumns, direction);
                    FusionConfig disabled = base().withTelemetry(TelemetryConfig.defaults());
                    fixture.apply(disabled);
                    fixture.measureAndLayout();
                    Snapshot freshDisabled = fixture.capture("fresh-disabled");
                    assertNull(fixture.readout());
                    FusionConfig enabled = enabled(false, side, 0, TelemetryConfig.ALIGN_DEFAULT);
                    fixture.apply(enabled);
                    fixture.measureAndLayout();
                    StatusPairView originalReadout = fixture.readout();
                    Snapshot beforeDisable = fixture.capture("before-disable");
                    fixture.apply(disabled);
                    fixture.measureAndLayout();
                    Snapshot afterDisable = fixture.capture("after-disable");
                    assertNull(fixture.readout());
                    fixture.assertNativeContentVisible();
                    // Record the difference; restoring the native tree is not an agreed contract.
                    System.out.println("NetworkLeadingGap disabled-contract: "
                            + freshDisabled + " -> " + afterDisable);
                    fixture.apply(enabled);
                    fixture.measureAndLayout();
                    Snapshot reenabled = fixture.capture("reenabled");
                    assertSame(originalReadout, fixture.readout());
                    assertEquals(beforeDisable.toString() + " -> " + reenabled,
                            beforeDisable.readoutFrame, reenabled.readoutFrame);
                }
            }
        }
    }

    @Test public void transparentTextStillFailsTheNonemptyInkAssertionAfterPreDraw()
            throws Exception {
        Fixture fixture = new Fixture(false, View.LAYOUT_DIRECTION_LTR);
        fixture.apply(enabled(false, TelemetryConfig.SIDE_RIGHT, 0, TelemetryConfig.ALIGN_DEFAULT));
        fixture.measureAndLayout();
        fixture.capture("transparent-control-before");
        TextView line = (TextView) fixture.readout().getChildAt(0);
        int originalColor = line.getCurrentTextColor();
        try {
            line.setTextColor(0x00000000);
            AssertionError failure = assertThrows(AssertionError.class, () -> fixture.textInk(line));
            assertEquals("The text must have nonempty raster ink", failure.getMessage());
        } finally {
            line.setTextColor(originalColor);
        }
    }

    @Test public void movingTheRealReadoutOverTheClockStillFailsTheSameGapAssertion()
            throws Exception {
        Fixture fixture = new Fixture(false, View.LAYOUT_DIRECTION_LTR);
        fixture.apply(enabled(false, TelemetryConfig.SIDE_RIGHT, 0, TelemetryConfig.ALIGN_DEFAULT));
        fixture.measureAndLayout();
        Snapshot before = fixture.capture("overlap-control-before");
        fixture.assertClockToReadoutGap(before);
        StatusPairView readout = fixture.readout();
        int offset = Math.round(before.leftInk.centerX() - before.readoutFrame.centerX());
        readout.offsetLeftAndRight(offset);
        try {
            Snapshot overlapping = fixture.capture("overlap-control-moved-view");
            assertTrue("The actual readout frame must overlap clock ink",
                    RectF.intersects(overlapping.leftInk, overlapping.readoutFrame));
            AssertionError failure = assertThrows(AssertionError.class,
                    () -> fixture.assertClockToReadoutGap(overlapping));
            assertTrue(failure.getMessage().startsWith("The interval can exist without a hidden icon:"));
        } finally {
            readout.offsetLeftAndRight(-offset);
        }
    }

    @Test public void wrongAlignmentStillFailsTheSameParagraphInkMovementAssertion()
            throws Exception {
        Fixture fixture = new Fixture(false, View.LAYOUT_DIRECTION_RTL);
        fixture.apply(enabled(false, TelemetryConfig.SIDE_LEFT, 140, TelemetryConfig.ALIGN_LEFT));
        fixture.measureAndLayout();
        fixture.assertParagraphAlignment(TelemetryConfig.ALIGN_LEFT);
        Snapshot left = fixture.capture("wrong-alignment-control-left");
        fixture.apply(enabled(false, TelemetryConfig.SIDE_LEFT, 140, TelemetryConfig.ALIGN_CENTER));
        StatusPairView readout = fixture.readout();
        try {
            readout.setAlignment(TelemetryConfig.ALIGN_LEFT);
            fixture.measureAndLayout();
            Snapshot wrong = fixture.capture("wrong-alignment-control-request-center-actual-left");
            AssertionError failure = assertThrows(AssertionError.class,
                    () -> assertFixedParagraphAdvance(left, wrong));
            assertTrue(failure.getMessage().startsWith("LTR paragraph alignment must move ink physically right:"));
        } finally {
            readout.setAlignment(TelemetryConfig.ALIGN_CENTER);
        }
    }

    private static void assertFixedParagraphAdvance(Snapshot previous, Snapshot current) {
        assertEquals("Alignment must retain the fixed readout frame: " + current,
                previous.readoutFrame, current.readoutFrame);
        assertTrue("LTR paragraph alignment must move ink physically right: " + previous + " -> " + current,
                current.readoutInk.centerX() > previous.readoutInk.centerX());
    }

    private static int[] directions() {
        return new int[] {View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL};
    }

    private static FusionConfig base() {
        return FusionConfig.defaults().withDoubleRow(false).withSpanRows(false)
                .withStatusBarHeight(HEIGHT).withSystemPosition(FusionConfig.SIDE_RIGHT, 0)
                .withFusionPosition(FusionConfig.SIDE_RIGHT, 0);
    }

    private static FusionConfig enabled(boolean doubleRow, int side, int width, int alignment) {
        TelemetryConfig telemetry = TelemetryConfig.defaults()
                .withEnabled(TelemetryConfig.NET_SPEED, true)
                .withPosition(TelemetryConfig.NET_SPEED, side * 2)
                .withFixedWidth(TelemetryConfig.NET_SPEED, width)
                .withAlignment(TelemetryConfig.NET_SPEED, alignment);
        return base().withDoubleRow(doubleRow).withTelemetry(telemetry);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    // Keep the real configure call's existing-config fast path; no worker or receiver is started.
    private static void primeSampler(Context context, TelemetryConfig telemetry) throws Exception {
        field(TelemetrySampler.class, "config").set(null, telemetry);
        field(TelemetrySampler.class, "latest").set(null, TelemetryReadings.SAMPLE);
        field(TelemetrySampler.class, "interactive").setBoolean(null, true);
        field(TelemetrySampler.class, "lastInteractiveCheck").setLong(null, SystemClock.uptimeMillis());
        field(TelemetrySampler.class, "appContext").set(null, context);
        field(TelemetrySampler.class, "polling").set(null, telemetry.anyEnabled() ? IDLE_POLLING : null);
    }

    private static final ScheduledFuture<Object> IDLE_POLLING = new ScheduledFuture<Object>() {
        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed other) { return 0; }
        @Override public boolean cancel(boolean interrupt) { return true; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return false; }
        @Override public Object get() { return null; }
        @Override public Object get(long timeout, TimeUnit unit) { return null; }
    };

    private final class Fixture {
        final LayoutProbe root;
        final LinearLayout common;
        final LinearLayout originalLeft;
        final LinearLayout originalSystem;
        final TextView leftText;
        final View cutout;
        final MiuiStatusBatteryContainer battery;
        final FrameLayout statusIcons;
        final TransformProbe firstIcon;
        final View secondIcon;
        final View batteryIcon;
        final boolean originalTwoColumns;
        final int direction;
        FusionConfig currentConfig;

        Fixture(boolean originalTwoColumns, int direction) {
            this.originalTwoColumns = originalTwoColumns;
            this.direction = direction;
            window.removeAllViews();
            Context base = activityController.get();
            Resources resources = new NamedSystemUiResources(base.getResources());
            ApplicationInfo applicationInfo = new ApplicationInfo(base.getApplicationInfo());
            applicationInfo.flags |= ApplicationInfo.FLAG_SUPPORTS_RTL;
            applicationInfo.targetSdkVersion = Math.max(applicationInfo.targetSdkVersion,
                    Build.VERSION_CODES.JELLY_BEAN_MR1);
            Context context = new ContextWrapper(base) {
                @Override public Resources getResources() { return resources; }
                @Override public ApplicationInfo getApplicationInfo() { return applicationInfo; }
            };
            root = new LayoutProbe(context);
            root.setLayoutDirection(direction);
            common = new LinearLayout(context);
            common.setLayoutDirection(direction);
            common.setOrientation(LinearLayout.HORIZONTAL);
            root.addView(common, new FrameLayout.LayoutParams(WIDTH, HEIGHT));
            originalLeft = new LinearLayout(context);
            originalLeft.setId(LEFT);
            leftText = new TextView(context);
            leftText.setId(CLOCK);
            leftText.setText("Tue 12:34");
            leftText.setTextSize(14);
            leftText.setTextColor(0xffffffff);
            leftText.setIncludeFontPadding(false);
            leftText.setSingleLine(true);
            leftText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            originalLeft.addView(leftText, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            cutout = new View(context);
            common.addView(originalLeft, originalTwoColumns ? column() : new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            common.addView(cutout, new LinearLayout.LayoutParams(originalTwoColumns ? 40 : 0, HEIGHT));
            originalSystem = new LinearLayout(context);
            originalSystem.setId(SYSTEM);
            originalSystem.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            battery = new MiuiStatusBatteryContainer(context);
            battery.setOrientation(LinearLayout.HORIZONTAL);
            battery.setId(BATTERY);
            statusIcons = new FrameLayout(context);
            statusIcons.setId(STATUS_ICONS);
            statusIcons.setClipChildren(false);
            firstIcon = new TransformProbe(context);
            firstIcon.setBackgroundColor(0xffffffff);
            secondIcon = new View(context);
            secondIcon.setBackgroundColor(0xffffffff);
            statusIcons.addView(firstIcon, new FrameLayout.LayoutParams(24, HEIGHT, Gravity.START));
            statusIcons.addView(secondIcon, new FrameLayout.LayoutParams(24, HEIGHT, Gravity.END));
            battery.addView(statusIcons, new LinearLayout.LayoutParams(64, HEIGHT));
            batteryIcon = new View(context);
            batteryIcon.setBackgroundColor(0xffffffff);
            battery.addView(batteryIcon, new LinearLayout.LayoutParams(32, HEIGHT));
            originalSystem.addView(battery, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            common.addView(originalSystem, originalTwoColumns ? column() : new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            window.addView(root, new FrameLayout.LayoutParams(WIDTH, HEIGHT));
            assertTrue("The complete fixture must be attached before measurement", root.isAttachedToWindow());
        }

        void apply(FusionConfig config) throws Exception {
            currentConfig = config;
            primeSampler(root.getContext(), config.telemetry);
            DualRowStatusBarLayout.apply(root, config);
            DualRowStatusBarLayout.updateTelemetry(TelemetryReadings.SAMPLE);
            if (config.telemetry.enabled(TelemetryConfig.NET_SPEED)) {
                assertNotNull("The fixture must use the real install path",
                        root.getTag(R.id.dual_row_layout_state));
                assertTrue("State.syncTelemetry must mount the module readout",
                        DualRowStatusBarLayout.showsTelemetry(root, TelemetryConfig.NET_SPEED));
            }
        }

        void measureAndLayout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, WIDTH, HEIGHT);
            for (View view : new View[] {root, common, root.findViewById(LEFT), root.findViewById(SYSTEM)}) {
                assertResolvedDirection(view);
            }
            StatusPairView readout = readout();
            if (readout != null) {
                assertResolvedDirection((View) readout.getParent());
                assertResolvedDirection(readout);
                for (int index = 0; index < readout.getChildCount(); index++) {
                    assertResolvedDirection(readout.getChildAt(index));
                }
            }
            assertSame("Use the actual attached tree's shared observer",
                    window.getViewTreeObserver(), root.getViewTreeObserver());
            assertSame("TextView pre-draw must be registered on that same observer",
                    root.getViewTreeObserver(), leftText.getViewTreeObserver());
            if (readout != null) {
                for (int index = 0; index < readout.getChildCount(); index++) {
                    assertSame(root.getViewTreeObserver(), readout.getChildAt(index).getViewTreeObserver());
                }
            }
            String beforePreDraw = textViewportDiagnostics(root);
            assertFalse("The tree must permit this drawing pass",
                    root.getViewTreeObserver().dispatchOnPreDraw());
            String afterPreDraw = textViewportDiagnostics(root);
            if (!beforePreDraw.equals(afterPreDraw)) {
                System.out.println("NetworkLeadingGap preDraw: " + beforePreDraw + " -> " + afterPreDraw);
            }
        }

        void assertClockToReadoutGap(Snapshot snapshot) {
            assertTrue("The interval can exist without a hidden icon: " + snapshot,
                    start(snapshot.readoutFrame) > end(snapshot.leftInk));
        }

        void assertParagraphAlignment(int requestedAlignment) {
            // TEXT_ALIGNMENT_GRAVITY makes START/END paragraph-relative, even in an RTL View.
            Layout.Alignment expected = switch (requestedAlignment) {
                case TelemetryConfig.ALIGN_LEFT -> Layout.Alignment.ALIGN_NORMAL;
                case TelemetryConfig.ALIGN_CENTER -> Layout.Alignment.ALIGN_CENTER;
                case TelemetryConfig.ALIGN_RIGHT -> Layout.Alignment.ALIGN_OPPOSITE;
                default -> throw new AssertionError("An explicit alignment request is required");
            };
            StatusPairView readout = readout();
            assertNotNull(readout);
            for (int index = 0; index < readout.getChildCount(); index++) {
                View child = readout.getChildAt(index);
                if (child.getVisibility() != View.VISIBLE) continue;
                assertTrue(child instanceof TextView);
                TextView text = (TextView) child;
                Layout layout = text.getLayout();
                assertNotNull(layout);
                assertEquals("Use the actual TextView alignment policy", View.TEXT_ALIGNMENT_GRAVITY,
                        text.getTextAlignment());
                assertTrue("The measured readout must have a paragraph", layout.getLineCount() > 0);
                assertEquals("The telemetry sample contains a Latin LTR paragraph",
                        Layout.DIR_LEFT_TO_RIGHT, layout.getParagraphDirection(0));
                assertEquals("Layout alignment must match the independent request contract",
                        expected, layout.getAlignment());
            }
        }

        String textViewportDiagnostics(View view) {
            StringBuilder result = new StringBuilder();
            appendTextViewportDiagnostics(view, result);
            return result.toString();
        }

        void appendTextViewportDiagnostics(View view, StringBuilder result) {
            if (view instanceof TextView text && view.getVisibility() == View.VISIBLE) {
                Layout layout = text.getLayout();
                result.append("{text=").append(text.getText())
                        .append(",frame=").append(text.getWidth()).append('x').append(text.getHeight())
                        .append(",gravity=").append(text.getGravity())
                        .append(",textAlignment=").append(text.getTextAlignment())
                        .append(",scroll=").append(text.getScrollX()).append('/').append(text.getScrollY())
                        .append(",baseline=").append(text.getBaseline())
                        .append(",layoutWidth=").append(layout == null ? -1 : layout.getWidth());
                if (layout != null && layout.getLineCount() > 0) {
                    result.append(",lineLeft/right=").append(layout.getLineLeft(0))
                            .append('/').append(layout.getLineRight(0))
                            .append(",paragraphDirection=").append(layout.getParagraphDirection(0))
                            .append(",layoutAlignment=").append(layout.getAlignment());
                }
                result.append('}');
            }
            if (view instanceof ViewGroup group) {
                for (int index = 0; index < group.getChildCount(); index++) {
                    appendTextViewportDiagnostics(group.getChildAt(index), result);
                }
            }
        }

        void assertResolvedDirection(View view) {
            assertEquals("Actual layout direction for " + view.getClass().getSimpleName(),
                    direction, view.getLayoutDirection());
        }

        StatusPairView readout() { return findReadout(root); }
        float start(RectF bounds) { return direction == View.LAYOUT_DIRECTION_RTL ? WIDTH - bounds.right : bounds.left; }
        float end(RectF bounds) { return direction == View.LAYOUT_DIRECTION_RTL ? WIDTH - bounds.left : bounds.right; }

        void assertNativeContentVisible() {
            for (View view : new View[] {originalLeft, leftText, battery, statusIcons,
                    firstIcon, secondIcon, batteryIcon}) {
                View ancestor = view;
                while (ancestor != root) {
                    assertEquals("No hidden native slot in this fixture", View.VISIBLE, ancestor.getVisibility());
                    assertTrue("Native identity remains under its root", ancestor.getParent() instanceof View);
                    ancestor = (View) ancestor.getParent();
                }
            }
        }

        Snapshot capture(String phase) {
            Snapshot result = new Snapshot();
            View leftColumn = root.findViewById(LEFT);
            View rightColumn = root.findViewById(SYSTEM);
            result.label = "originalTwoColumns=" + originalTwoColumns + ",direction=" + direction
                    + ",side=" + TelemetryConfig.side(currentConfig.telemetry.position(TelemetryConfig.NET_SPEED))
                    + ",doubleRow=" + currentConfig.doubleRow + "," + phase;
            result.leftColumn = bounds(leftColumn);
            result.rightColumn = bounds(rightColumn);
            result.leftInk = textInk(leftText);
            result.nativeFrame = bounds(battery);
            result.statusFrame = bounds(statusIcons);
            result.nativeLeafFrames = bounds(firstIcon);
            result.nativeLeafFrames.union(bounds(secondIcon));
            result.nativeLeafFrames.union(bounds(batteryIcon));
            result.twoEqualColumns = weightedColumn(leftColumn) && weightedColumn(rightColumn)
                    && leftColumn.getWidth() == rightColumn.getWidth();
            StatusPairView readout = readout();
            if (readout != null) {
                result.readoutFrame = bounds(readout);
                result.readoutInk = new RectF();
                for (int index = 0; index < readout.getChildCount(); index++) {
                    View line = readout.getChildAt(index);
                    if (line instanceof TextView text && line.getVisibility() == View.VISIBLE) {
                        result.readoutInk.union(textInk(text));
                    }
                }
                assertFalse("Readout text must actually draw: " + result.label, result.readoutInk.isEmpty());
                result.clockInkToReadoutFrame = start(result.readoutFrame) - end(result.leftInk);
                result.readoutTextInset = start(result.readoutInk) - start(result.readoutFrame);
            }
            System.out.println("NetworkLeadingGap " + result);
            return result;
        }

        RectF bounds(View view) {
            return map(view, new RectF(0, 0, view.getWidth(), view.getHeight()));
        }

        RectF textInk(TextView text) {
            assertNotNull("Text must be measured before drawing", text.getLayout());
            assertNull("The scan measures text, not a background", text.getBackground());
            assertTrue(text.getWidth() > 0 && text.getHeight() > 0);
            Bitmap bitmap = Bitmap.createBitmap(text.getWidth(), text.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(bitmap);
                // Match the real viewport: parent drawing compensates the view's actual scroll.
                canvas.translate(-text.getScrollX(), -text.getScrollY());
                text.draw(canvas);
                int left = bitmap.getWidth(), top = bitmap.getHeight(), right = 0, bottom = 0;
                for (int y = 0; y < bitmap.getHeight(); y++) {
                    for (int x = 0; x < bitmap.getWidth(); x++) {
                        if ((bitmap.getPixel(x, y) >>> 24) == 0) continue;
                        left = Math.min(left, x);
                        top = Math.min(top, y);
                        right = Math.max(right, x + 1);
                        bottom = Math.max(bottom, y + 1);
                    }
                }
                assertTrue("The text must have nonempty raster ink", right > left && bottom > top);
                return map(text, new RectF(left, top, right, bottom));
            } finally {
                bitmap.recycle();
            }
        }

        RectF map(View view, RectF rect) {
            Matrix matrix = new Matrix();
            view.transformMatrixToGlobal(matrix);
            root.transformMatrixToLocal(matrix);
            matrix.mapRect(rect);
            return rect;
        }
    }

    private static final class TextWrites implements TextWatcher {
        int count;
        TextWrites(TextView view) { view.addTextChangedListener(this); }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable text) { count++; }
    }

    private static final class TransformProbe extends View {
        int transformReads;
        TransformProbe(Context context) { super(context); }
        @Override public void transformMatrixToGlobal(Matrix matrix) {
            transformReads++;
            super.transformMatrixToGlobal(matrix);
        }
    }

    private static final class LayoutProbe extends FrameLayout {
        int layoutRequests;
        LayoutProbe(Context context) { super(context); }
        @Override public void requestLayout() {
            layoutRequests++;
            super.requestLayout();
        }
    }

    private static LinearLayout.LayoutParams column() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private static boolean weightedColumn(View view) {
        return view.getLayoutParams() instanceof LinearLayout.LayoutParams params
                && params.width == 0 && params.weight == 1f;
    }

    private static StatusPairView findReadout(View view) {
        if (view instanceof StatusPairView readout) return readout;
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                StatusPairView readout = findReadout(group.getChildAt(index));
                if (readout != null) return readout;
            }
        }
        return null;
    }

    private static final class Snapshot {
        String label;
        RectF leftColumn, rightColumn, leftInk, readoutFrame, readoutInk;
        RectF nativeFrame, statusFrame, nativeLeafFrames;
        boolean twoEqualColumns;
        float clockInkToReadoutFrame, readoutTextInset;

        @Override public String toString() {
            return label + " columns=" + leftColumn + "/" + rightColumn
                    + " equalWeighted=" + twoEqualColumns + " leftInk=" + leftInk
                    + " readoutFrame=" + readoutFrame + " readoutInk=" + readoutInk
                    + " nativeFrame=" + nativeFrame + " statusFrame=" + statusFrame
                    + " nativeLeafFrames=" + nativeLeafFrames
                    + " clockInkToReadoutFrame=" + clockInkToReadoutFrame
                    + " readoutTextInset=" + readoutTextInset;
        }
    }

    private static final class NamedSystemUiResources extends Resources {
        @SuppressWarnings("deprecation") NamedSystemUiResources(Resources base) {
            super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
        }

        @Override public int getIdentifier(String name, String type, String packageName) {
            if ("id".equals(type) && "com.android.systemui".equals(packageName)) {
                return switch (name) {
                    case "phone_status_bar_left_container" -> LEFT;
                    case "system_icon_area" -> SYSTEM;
                    case "system_icons" -> BATTERY;
                    case "statusIcons" -> STATUS_ICONS;
                    case "clock" -> CLOCK;
                    default -> 0;
                };
            }
            return super.getIdentifier(name, type, packageName);
        }
    }
}

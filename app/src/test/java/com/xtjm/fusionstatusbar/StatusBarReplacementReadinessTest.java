package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.view.View;
import android.widget.FrameLayout;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
public class StatusBarReplacementReadinessTest {
    private static final int SYSTEM_ICONS = 0x7f0e0201;
    private static final int STATUS_ICONS = 0x7f0e0202;
    private static final int BATTERY = 0x7f0e0203;
    private static final int BATTERY_ARTIFACT = 0x7f0e0204;

    private Context context;
    private Field batteryClass;
    private Object previousBatteryClass;

    @Before
    public void setUp() throws Exception {
        Context base = RuntimeEnvironment.getApplication();
        Resources resources = new NamedResources(base.getResources());
        context = new ContextWrapper(base) {
            @Override public Resources getResources() { return resources; }
        };
        batteryClass = SystemUiHooks.class.getDeclaredField("batteryClass");
        batteryClass.setAccessible(true);
        previousBatteryClass = batteryClass.get(null);
        batteryClass.set(null, FrameLayout.class);
    }

    @After
    public void restoreFixtureBatteryClass() throws Exception {
        batteryClass.set(null, previousBatteryClass);
    }

    @Test
    public void anotherHostCannotHideNativeIconsWhenThisHostHasNoReplacement() throws Exception {
        Host first = new Host(true);
        first.measureReplacement();
        first.layoutReplacement();
        assertEquals(View.GONE, first.nativeBattery.getVisibility());

        Host second = new Host(false);
        applyPlacement(second.root);

        assertEquals(View.VISIBLE, second.artifact.getVisibility());
        assertEquals(View.VISIBLE, second.mobile.getVisibility());
        assertEquals(1f, second.mobile.getAlpha(), 0f);
    }

    @Test
    public void localMeasurementAloneKeepsNativeIconsUntilLocalLayout() throws Exception {
        Host first = new Host(true);
        first.measureReplacement();
        first.layoutReplacement();
        Host second = new Host(true);

        second.measureReplacement();
        applyPlacement(second.root);
        assertEquals(View.VISIBLE, second.nativeBattery.getVisibility());
        assertEquals(View.VISIBLE, second.mobile.getVisibility());

        second.layoutReplacement();
        applyPlacement(second.root);
        assertEquals(View.GONE, second.nativeBattery.getVisibility());
        assertEquals(View.GONE, second.mobile.getVisibility());
        assertEquals(View.VISIBLE, second.volume.getVisibility());
    }

    @Test
    public void replacementLossRestoresModuleChangesAndKeepsHostIgnoredSlots() throws Exception {
        Host host = new Host(true);
        host.signals.addIgnoredSlots(List.of("volume", "wifi"));
        host.mobile.setAlpha(0.6f);
        host.nativeBattery.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        host.volume.setAlpha(0.4f);
        host.measureReplacement();
        host.layoutReplacement();
        applyPlacement(host.root);
        assertEquals(View.GONE, host.artifact.getVisibility());
        assertEquals(List.of("volume", "wifi", "mobile"), host.signals.ignoredSlots);

        host.battery.removeView(host.fusion);
        applyPlacement(host.root);
        assertEquals(View.VISIBLE, host.artifact.getVisibility());
        assertEquals(View.VISIBLE, host.nativeBattery.getVisibility());
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES,
                host.nativeBattery.getImportantForAccessibility());
        assertEquals(View.VISIBLE, host.mobile.getVisibility());
        assertEquals(0.6f, host.mobile.getAlpha(), 0f);
        assertEquals(0.4f, host.volume.getAlpha(), 0f);
        assertEquals(List.of("volume", "wifi"), host.signals.ignoredSlots);
    }

    @Test
    public void activeFusionLeavesSoundSlotsUnderHostPolicy() throws Exception {
        Host host = new Host(true);
        host.signals.addIgnoredSlots(List.of("volume", "mute", "zen"));
        host.volume.setVisibility(View.INVISIBLE);
        host.volume.setAlpha(0.3f);
        host.measureReplacement();
        host.layoutReplacement();
        applyPlacement(host.root);

        assertEquals(List.of("volume", "mute", "zen", "mobile"), host.signals.ignoredSlots);
        assertEquals(View.INVISIBLE, host.volume.getVisibility());
        assertEquals(0.3f, host.volume.getAlpha(), 0f);
    }

    @Test
    public void coveredReplacementKeepsVisibleSiblingSignals() throws Exception {
        Host host = new Host(true);
        host.measureReplacement();
        host.layoutReplacement();
        assertEquals(View.GONE, host.mobile.getVisibility());

        host.batteryWrapper.setAlpha(0f);
        applyPlacement(host.root);

        assertEquals(View.VISIBLE, host.mobile.getVisibility());
        assertEquals(View.VISIBLE, host.artifact.getVisibility());
    }

    @Test
    public void fallbackRespectsNativeBatteryHideWhileReplacementIsActive() throws Exception {
        Host host = new Host(true);
        host.measureReplacement();
        host.layoutReplacement();
        Method hide = StatusBarReplacementReadinessTest.class.getDeclaredMethod("hideView", View.class);
        runHook("interceptChildVisibility", null, host.nativeBattery, hide,
                () -> hideView(host.nativeBattery));

        host.battery.removeView(host.fusion);
        applyPlacement(host.root);

        assertEquals(View.GONE, host.nativeBattery.getVisibility());
        assertEquals(0f, host.nativeBattery.getAlpha(), 0f);
    }

    @Test
    public void fallbackRespectsDirectNativeBatteryVisibilityUpdate() throws Exception {
        Host host = new Host(true);
        host.measureReplacement();
        host.layoutReplacement();
        runHook("afterUpdate", host.battery, null, null,
                () -> host.nativeBattery.setVisibility(View.GONE));

        host.battery.removeView(host.fusion);
        applyPlacement(host.root);

        assertEquals(View.GONE, host.nativeBattery.getVisibility());
    }

    @Test
    public void hostCanAdoptAnAlreadyIgnoredSlotWhileFusionIsActive() throws Exception {
        Host host = new Host(true);
        host.measureReplacement();
        host.layoutReplacement();
        assertEquals(List.of("mobile"), host.signals.ignoredSlots);

        List<String> added = List.of("mobile", "volume");
        try {
            runHook("afterAddIgnored", host.signals, added, null,
                    () -> host.signals.addIgnoredSlots(added));
        } catch (NoSuchMethodException unhooked) {
            host.signals.addIgnoredSlots(added);
        }
        host.battery.removeView(host.fusion);
        applyPlacement(host.root);

        assertEquals(List.of("mobile", "volume"), host.signals.ignoredSlots);
    }

    @Test
    public void soundTrackingCanStartBeforeTheChildAttaches() throws Exception {
        Host host = new Host(false);
        assertFalse(host.signals.isAttachedToWindow());
        Method track = SystemUiHooks.class.getDeclaredMethod("trackSoundContainer", View.class);
        track.setAccessible(true);
        track.invoke(null, host.root);
        try (var activity = Robolectric.buildActivity(Activity.class).setup()) {
            activity.get().setContentView(host.root);
            assertTrue(host.signals.isAttachedToWindow());
            ShadowLooper.idleMainLooper(250, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertTrue(ShadowLog.getLogsForTag("FusionStatusBar").stream()
                    .anyMatch(entry -> entry.msg.contains("status-bar sound")
                            && entry.msg.contains("volumeChild=present")));
        }
    }

    @Test
    public void fallbackRestoresOnlyTheFusionAlignmentOffset() throws Exception {
        Host host = new Host(true);
        try (var activity = Robolectric.buildActivity(Activity.class).setup()) {
            activity.get().setContentView(host.root);
            host.measureReplacement();
            host.layoutReplacement();
            host.system.layout(0, 0, 200, 48);
            host.batteryWrapper.layout(125, 0, 200, 48);
            host.signals.layout(12, 0, 42, 20);
            host.signals.setTranslationX(3f);
            Method align = SystemUiHooks.class.getDeclaredMethod("alignNativeStatusIcons",
                    android.view.ViewGroup.class);
            align.setAccessible(true);
            align.invoke(null, host.system);
            assertTrue(host.signals.getTranslationX() != 3f);

            host.battery.removeView(host.fusion);
            assertEquals(3f, host.signals.getTranslationX(), 0f);
        }
    }

    private static void hideView(View view) {
        view.setVisibility(View.GONE);
        view.setAlpha(0f);
    }

    private static void applyPlacement(View root) throws Exception {
        Method method = SystemUiHooks.class.getDeclaredMethod("applyRootPlacement",
                android.view.ViewGroup.class);
        method.setAccessible(true);
        method.invoke(null, root);
    }

    private static void runHook(String name, View view) throws Exception {
        runHook(name, view, null, null, () -> { });
    }

    private static void runHook(String name, View view, Object argument, Method executable,
            Runnable nativeCall) throws Exception {
        XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                StatusBarReplacementReadinessTest.class.getClassLoader(),
                new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, args) ->
                        switch (method.getName()) {
                            case "getThisObject" -> view;
                            case "getExecutable" -> executable;
                            case "getArg" -> argument == null
                                    ? View.MeasureSpec.makeMeasureSpec(32, View.MeasureSpec.EXACTLY)
                                    : argument;
                            case "proceed" -> {
                                nativeCall.run();
                                yield null;
                            }
                            default -> null;
                        });
        Method hook = SystemUiHooks.class.getDeclaredMethod(name, XposedInterface.Chain.class);
        hook.setAccessible(true);
        hook.invoke(null, chain);
    }

    private final class Host {
        final FrameLayout root = new FrameLayout(context);
        final FrameLayout system = new FrameLayout(context);
        final FrameLayout batteryWrapper = new FrameLayout(context);
        final FrameLayout battery = new FrameLayout(context);
        final IconContainer signals = new IconContainer(context);
        final View nativeBattery = new View(context);
        final View artifact = new View(context);
        final SlotView mobile = new SlotView(context, "mobile");
        final SlotView volume = new SlotView(context, "volume");
        FusionIconView fusion;

        Host(boolean supportsFusion) {
            system.setId(SYSTEM_ICONS);
            root.addView(system);
            signals.setId(STATUS_ICONS);
            signals.addView(mobile);
            signals.addView(volume);
            system.addView(signals);
            artifact.setId(BATTERY_ARTIFACT);
            system.addView(artifact);
            if (supportsFusion) {
                battery.setId(BATTERY);
                battery.addView(nativeBattery);
                fusion = new FusionIconView(context);
                battery.addView(fusion);
                batteryWrapper.addView(battery);
                system.addView(batteryWrapper);
            }
        }

        void measureReplacement() throws Exception {
            runHook("batteryMeasure", battery);
            assertTrue(fusion.getMeasuredWidth() > 0);
            assertEquals(0, fusion.getWidth());
        }

        void layoutReplacement() throws Exception {
            battery.layout(0, 0, battery.getMeasuredWidth(), battery.getMeasuredHeight());
            runHook("batteryLayout", battery);
            assertTrue(fusion.getWidth() > 0);
        }
    }

    public static final class IconContainer extends FrameLayout {
        final List<String> ignoredSlots = new ArrayList<>();

        IconContainer(Context context) { super(context); }

        public void addIgnoredSlots(List<String> slots) {
            for (String slot : slots) {
                if (!ignoredSlots.contains(slot)) ignoredSlots.add(slot);
            }
        }
    }

    public static final class SlotView extends View {
        private final String slot;
        SlotView(Context context, String slot) {
            super(context);
            this.slot = slot;
        }
        public String getSlot() { return slot; }
    }

    private static final class NamedResources extends Resources {
        @SuppressWarnings("deprecation")
        NamedResources(Resources base) {
            super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
        }

        @Override public int getIdentifier(String name, String type, String packageName) {
            if ("com.android.systemui".equals(packageName) && "id".equals(type)) {
                return switch (name) {
                    case "system_icons" -> SYSTEM_ICONS;
                    case "statusIcons" -> STATUS_ICONS;
                    case "battery" -> BATTERY;
                    case "battery_indicator" -> BATTERY_ARTIFACT;
                    default -> 0;
                };
            }
            return super.getIdentifier(name, type, packageName);
        }
    }
}

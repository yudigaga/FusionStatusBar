package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView;
import io.github.libxposed.api.XposedInterface;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
public class KeyguardStatusBarHeightTest {
    private static final int WIDTH = 400;
    private static final int NATIVE_HEIGHT = 24;
    private static final int CONFIGURED_HEIGHT = 72;
    private final Map<Field, Object> previousFields = new LinkedHashMap<>();
    private Map<String, RuntimeApplicationPolicy.Result> previousFeatureStates;

    @Before public void isolateHostConfigLoading() throws Exception {
        // Keep asynchronous config IO outside this layout regression; the actual
        // keyguard hooks, view measurements and icon placement remain in use.
        replaceField("configLifecycle", new RuntimeConfigLifecycle(
                new Handler(Looper.getMainLooper()), (task, delay) -> false,
                new RuntimeConfigLifecycle.Factory() {
                    @Override public RuntimeConfigLifecycle.ConfigAccess createConfig(
                            Context context, RuntimeConfigCoordinator.Listener listener) {
                        return new RuntimeConfigLifecycle.ConfigAccess() {
                            @Override public void reload() { }
                            @Override public void report(Context source, long revision,
                                    String feature, String state, String reason) { }
                        };
                    }
                    @Override public RuntimeConfigLifecycle.ObserverAccess createObservers(
                            Context context, Handler main, Runnable reload, Runnable preview) {
                        return () -> { };
                    }
                }, new RuntimeConfigLifecycle.Host() {
                    @Override public void apply(FusionConfig config) { }
                    @Override public void onApplyFailure(FusionConfig config, Throwable error) { }
                    @Override public void onRecovered() { }
                    @Override public void requestPreview(Context context) { }
                }));
        replaceField("batteryClass", BatteryView.class);
        replaceField("currentStatusRoot", new WeakReference<ViewGroup>(null));
        replaceField("currentKeyguardStatusRoot", new WeakReference<ViewGroup>(null));
        replaceField("configApplied", true);
        previousFeatureStates = new LinkedHashMap<>(featureStates());
        Field menuConfig = SystemMenuHooks.class.getDeclaredField("config");
        menuConfig.setAccessible(true);
        previousFields.put(menuConfig, menuConfig.get(null));
    }

    @After public void restoreHostState() throws Exception {
        // Drain any queued real refresh before restoring its config and roots.
        ShadowLooper.idleMainLooper();
        for (Map.Entry<Field, Object> saved : previousFields.entrySet()) {
            saved.getKey().set(null, saved.getValue());
        }
        featureStates().clear();
        featureStates().putAll(previousFeatureStates);
    }

    @Test public void singleRowKeyguardUsesConfiguredHeightAndShowsTheFullEnlargedIcon()
            throws Exception {
        Fixture fixture = fixture(false);

        applyAndRelayout(fixture, "keyguardAttach");

        assertExpandedAndVisible(fixture);
    }

    @Test public void spanningKeyguardIconFitsThroughFixedNativeHeightAncestors()
            throws Exception {
        Fixture fixture = fixture(true);
        assertTrue("The repro must have an icon taller than the native lockscreen bar",
                fixture.expectedIconHeight > NATIVE_HEIGHT);

        applyAndRelayout(fixture, "keyguardAttach");

        assertExpandedAndVisible(fixture);
    }

    @Test public void keyguardLayoutRestoresHeightAfterTheHostResetsItsFixedContainers()
            throws Exception {
        Fixture fixture = fixture(true);
        applyAndRelayout(fixture, "keyguardAttach");
        resetNativeBounds(fixture);

        applyAndRelayout(fixture, "keyguardLayout");

        assertExpandedAndVisible(fixture);
    }

    @Test public void phoneStatusBarKeepsItsConfiguredTotalHeight() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        root.setLayoutParams(new FrameLayout.LayoutParams(WIDTH, NATIVE_HEIGHT));
        measureAndLayout(root, NATIVE_HEIGHT);

        SystemUiHooks.applyStatusBarHeight(root,
                FusionConfig.defaults().withDoubleRow(true).withStatusBarHeight(CONFIGURED_HEIGHT));

        assertEquals(CONFIGURED_HEIGHT, root.getLayoutParams().height);
        assertEquals(CONFIGURED_HEIGHT, root.getMeasuredHeight());
    }

    @Test public void keyguardMeasureExpandsTheNativeSpecBeforeMeasuringTheHierarchy()
            throws Exception {
        Fixture fixture = fixture(true);
        int[] proceededSpecs = invokeMeasureHook(fixture.root);

        assertEquals(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                proceededSpecs[0]);
        assertEquals("The native keyguard onMeasure must receive the configured height",
                View.MeasureSpec.makeMeasureSpec(CONFIGURED_HEIGHT, View.MeasureSpec.EXACTLY),
                proceededSpecs[1]);
        fixture.root.layout(0, 0, fixture.root.getMeasuredWidth(), fixture.root.getMeasuredHeight());
        invokeHook("keyguardLayout", fixture.root);

        assertExpandedAndVisible(fixture);
    }

    @Test public void inheritedKeyguardCallbacksDoNotResizeAnUnrelatedFrameLayout()
            throws Exception {
        Fixture fixture = fixture(true);
        FrameLayout unrelated = new FrameLayout(fixture.root.getContext());
        fixture.root.removeView(fixture.container);
        unrelated.addView(fixture.container,
                new FrameLayout.LayoutParams(WIDTH, NATIVE_HEIGHT));
        unrelated.setLayoutParams(new FrameLayout.LayoutParams(WIDTH, NATIVE_HEIGHT));
        measureAndLayout(unrelated, NATIVE_HEIGHT);

        invokeHook("keyguardAttach", unrelated);
        invokeHook("keyguardLayout", unrelated);
        int[] proceededSpecs = invokeMeasureHook(unrelated);

        assertEquals("A hook inherited from a framework ViewGroup must filter its receiver",
                NATIVE_HEIGHT, unrelated.getLayoutParams().height);
        assertEquals(NATIVE_HEIGHT, unrelated.getMeasuredHeight());
        assertEquals(NATIVE_HEIGHT, fixture.container.getLayoutParams().height);
        assertEquals(NATIVE_HEIGHT, fixture.systemIcons.getLayoutParams().height);
        assertEquals(View.MeasureSpec.makeMeasureSpec(NATIVE_HEIGHT, View.MeasureSpec.EXACTLY),
                proceededSpecs[1]);
    }

    @Test public void configRefreshUpdatesPhoneAndKeyguardWithoutReplacingEitherRoot()
            throws Exception {
        Fixture keyguard = fixture(true);
        FusionConfig previous = (FusionConfig) fieldValue("currentConfig");
        FusionConfig baseline = previous.withControlCenter(previous.controlCenter.withEnabled(false));
        replaceField("currentConfig", baseline);
        FrameLayout phone = new FrameLayout(keyguard.root.getContext());
        phone.setLayoutParams(new FrameLayout.LayoutParams(WIDTH, NATIVE_HEIGHT));
        measureAndLayout(phone, NATIVE_HEIGHT);
        invokeHook("phoneAttached", phone);
        SystemUiHooks.applyStatusBarHeight(phone, baseline);
        measureAndLayout(phone, CONFIGURED_HEIGHT);
        applyAndRelayout(keyguard, "keyguardAttach");
        assertExpandedAndVisible(keyguard);
        assertSame(phone, currentRoot("currentStatusRoot"));
        assertSame(keyguard.root, currentRoot("currentKeyguardStatusRoot"));

        Method applyConfig = SystemUiHooks.class.getDeclaredMethod("applyLoadedConfig", FusionConfig.class);
        applyConfig.setAccessible(true);
        applyConfig.invoke(null, baseline.withStatusBarHeight(80));
        ShadowLooper.idleMainLooper();

        // These assertions precede manual layout: both independent production
        // schedulers must have received the configuration transition themselves.
        assertEquals(80, phone.getLayoutParams().height);
        assertEquals(80, phone.getMeasuredHeight());
        assertEquals(80, keyguard.root.getLayoutParams().height);
        assertEquals(80, keyguard.root.getMeasuredHeight());
        assertSame("Refreshing keyguard must keep the application status bar registered",
                phone, currentRoot("currentStatusRoot"));
        assertSame(keyguard.root, currentRoot("currentKeyguardStatusRoot"));
        measureAndLayout(phone, phone.getLayoutParams().height);
        measureAndLayout(keyguard.root, keyguard.root.getLayoutParams().height);
        invokeHook("keyguardLayout", keyguard.root);
        keyguard.expectedIconHeight = 80;
        assertExpandedAndVisible(keyguard, 80);
    }

    private Fixture fixture(boolean doubleRow) throws Exception {
        FusionConfig config = FusionConfig.defaults().withDoubleRow(doubleRow)
                .withSpanRows(doubleRow).withStatusBarHeight(CONFIGURED_HEIGHT).withIconScale(150);
        replaceField("currentConfig", config);
        replaceField("currentPlan", StatusBarLayoutPlan.from(config));
        Context base = RuntimeEnvironment.getApplication();
        Resources resources = new NamedSystemUiResources(base.getResources());
        Context context = new ContextWrapper(base) {
            @Override public Resources getResources() { return resources; }
        };
        Fixture fixture = new Fixture();
        fixture.root = new MiuiKeyguardStatusBarView(context);
        fixture.container = namedFrame(context, "system_icons_container");
        fixture.systemIcons = namedFrame(context, "system_icons");
        fixture.battery = new BatteryView(context);
        fixture.battery.setId(resources.getIdentifier("battery", "id", "com.android.systemui"));
        fixture.icon = new FusionIconView(context);
        fixture.icon.setDisplayConfig(config);
        // Measure the real icon with room to grow, independently of host clipping.
        // This is the size the user's magnification setting requests.
        fixture.icon.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        fixture.expectedIconHeight = fixture.icon.getMeasuredHeight();
        fixture.root.setLayoutParams(new FrameLayout.LayoutParams(WIDTH, NATIVE_HEIGHT));
        fixture.root.addView(fixture.container, new FrameLayout.LayoutParams(WIDTH, NATIVE_HEIGHT));
        fixture.container.addView(fixture.systemIcons,
                new FrameLayout.LayoutParams(WIDTH, NATIVE_HEIGHT));
        fixture.systemIcons.addView(fixture.battery,
                new FrameLayout.LayoutParams(WIDTH, ViewGroup.LayoutParams.MATCH_PARENT));
        fixture.battery.addView(fixture.icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        measureAndLayout(fixture.root, NATIVE_HEIGHT);
        return fixture;
    }

    private static void applyAndRelayout(Fixture fixture, String entry) throws Exception {
        invokeHook(entry, fixture.root);
        // The next native parent pass obeys the root's new LayoutParams. Fixed
        // descendant heights intentionally remain until production handles them.
        measureAndLayout(fixture.root, fixture.root.getLayoutParams().height);
        invokeHook("keyguardLayout", fixture.root);
    }

    private static void assertExpandedAndVisible(Fixture fixture) {
        assertExpandedAndVisible(fixture, CONFIGURED_HEIGHT);
    }

    private static void assertExpandedAndVisible(Fixture fixture, int configuredHeight) {
        assertEquals("Lockscreen height must follow the same total-height setting as the phone bar",
                configuredHeight, fixture.root.getLayoutParams().height);
        assertEquals(configuredHeight, fixture.root.getHeight());
        assertEquals("Native fixed-height containers must not compress the enlarged fusion icon",
                fixture.expectedIconHeight, fixture.icon.getMeasuredHeight());
        assertTrue("The enlarged icon must be laid out", fixture.icon.getHeight() > NATIVE_HEIGHT);
        int top = fixture.icon.getTop();
        int bottom = fixture.icon.getBottom();
        View child = fixture.icon;
        while (child.getParent() instanceof ViewGroup ancestor) {
            if (ancestor.getClipChildren()) {
                assertTrue("Icon top must remain inside every clipping ancestor: "
                        + ancestor.getId(), top >= 0);
                assertTrue("Icon bottom must remain inside every clipping ancestor: "
                        + ancestor.getId(), bottom <= ancestor.getHeight());
            }
            if (ancestor.getClipToPadding()) {
                assertTrue(top >= ancestor.getPaddingTop());
                assertTrue(bottom <= ancestor.getHeight() - ancestor.getPaddingBottom());
            }
            top += ancestor.getTop();
            bottom += ancestor.getTop();
            child = ancestor;
        }
    }

    private static void resetNativeBounds(Fixture fixture) {
        for (ViewGroup group : new ViewGroup[] {
                fixture.root, fixture.container, fixture.systemIcons}) {
            group.getLayoutParams().height = NATIVE_HEIGHT;
            group.setClipChildren(true);
            group.setClipToPadding(true);
            group.requestLayout();
        }
        measureAndLayout(fixture.root, NATIVE_HEIGHT);
    }

    private static FrameLayout namedFrame(Context context, String name) {
        FrameLayout view = new FrameLayout(context);
        view.setId(context.getResources().getIdentifier(name, "id", "com.android.systemui"));
        return view;
    }

    private static void measureAndLayout(View view, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static void invokeHook(String name, ViewGroup root) throws Exception {
        XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                KeyguardStatusBarHeightTest.class.getClassLoader(),
                new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getThisObject")) return root;
                    return null;
                });
        Method hook = SystemUiHooks.class.getDeclaredMethod(name, XposedInterface.Chain.class);
        hook.setAccessible(true);
        hook.invoke(null, chain);
    }

    private static int[] invokeMeasureHook(ViewGroup root) throws Exception {
        Object[] originalSpecs = {
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(NATIVE_HEIGHT, View.MeasureSpec.EXACTLY)};
        int[] proceededSpecs = new int[2];
        XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                KeyguardStatusBarHeightTest.class.getClassLoader(),
                new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getThisObject")) return root;
                    if (method.getName().equals("getArgs")) return Arrays.asList(originalSpecs);
                    if (method.getName().equals("getArg")) {
                        return originalSpecs[(Integer) arguments[0]];
                    }
                    if (method.getName().equals("proceed")) {
                        Object[] nativeSpecs = arguments != null && arguments.length == 1
                                && arguments[0] instanceof Object[] supplied
                                ? supplied : originalSpecs;
                        proceededSpecs[0] = (Integer) nativeSpecs[0];
                        proceededSpecs[1] = (Integer) nativeSpecs[1];
                        root.measure(proceededSpecs[0], proceededSpecs[1]);
                    }
                    return null;
                });
        Method hook = SystemUiHooks.class.getDeclaredMethod("keyguardMeasure",
                XposedInterface.Chain.class);
        hook.setAccessible(true);
        hook.invoke(null, chain);
        return proceededSpecs;
    }

    private void replaceField(String name, Object value) throws Exception {
        Field field = SystemUiHooks.class.getDeclaredField(name);
        field.setAccessible(true);
        if (!previousFields.containsKey(field)) previousFields.put(field, field.get(null));
        field.set(null, value);
    }

    private static Object fieldValue(String name) throws Exception {
        Field field = SystemUiHooks.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static ViewGroup currentRoot(String name) throws Exception {
        return (ViewGroup) ((WeakReference<?>) fieldValue(name)).get();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, RuntimeApplicationPolicy.Result> featureStates() throws Exception {
        return (Map<String, RuntimeApplicationPolicy.Result>) fieldValue("FEATURE_STATES");
    }

    private static final class Fixture {
        FrameLayout root;
        FrameLayout container;
        FrameLayout systemIcons;
        BatteryView battery;
        FusionIconView icon;
        int expectedIconHeight;
    }

    public static final class BatteryView extends FrameLayout {
        public BatteryView(Context context) { super(context); }
    }

    private static final class NamedSystemUiResources extends Resources {
        @SuppressWarnings("deprecation") NamedSystemUiResources(Resources base) {
            super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
        }
        @Override public int getIdentifier(String name, String type, String packageName) {
            if ("id".equals(type) && "com.android.systemui".equals(packageName)) {
                return switch (name) {
                    case "system_icons_container" -> 0x7f0e0001;
                    case "system_icons" -> 0x7f0e0002;
                    case "battery" -> 0x7f0e0003;
                    default -> 0;
                };
            }
            return super.getIdentifier(name, type, packageName);
        }
    }
}

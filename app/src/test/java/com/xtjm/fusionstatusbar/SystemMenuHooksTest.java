package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.SeekBar;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 34, 35})
public class SystemMenuHooksTest {
    private Context context;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        SystemMenuHooks.updateConfig(settings(false, false, false, false));
    }

    @After public void restore() { SystemMenuHooks.updateConfig(settings(false, false, false, false)); }

    @Test public void compactHeightUsesRealNativeSizeAndKeepsExpandedAndEmbeddedPanels() throws Throwable {
        Registry hooks = install(Map.of(SystemMenuHooks.VOLUME_COLUMN_RES, ColumnResources.class));
        SystemMenuHooks.updateConfig(settings(true, true, false, false));
        int normal = dp(172);
        assertEquals(dp(142), hooks.call(ColumnResources.class, "getHeight", null,
                List.of(context, true, false), normal));
        assertEquals(normal, hooks.call(ColumnResources.class, "getHeight", null,
                List.of(context, true, true), normal));
        assertEquals(normal, hooks.call(ColumnResources.class, "getHeight", null,
                List.of(context, false, false), normal));
        assertEquals(dp(120), hooks.call(ColumnResources.class, "getHeight", null,
                List.of(context, true, false), dp(120)));
        SystemMenuHooks.updateConfig(settings(true, false, false, false));
        assertEquals(normal, hooks.call(ColumnResources.class, "getHeight", null,
                List.of(context, true, false), normal));
        SystemMenuHooks.updateConfig(settings(false, true, false, false));
        assertEquals(normal, hooks.call(ColumnResources.class, "getHeight", null,
                List.of(context, true, false), normal));
    }

    @Test public void compactPanelAndShadowLoseOnlyTheColumnHeightDifference() throws Throwable {
        Registry hooks = install(Map.of(SystemMenuHooks.VOLUME_DIALOG_RES, DialogResources.class));
        SystemMenuHooks.updateConfig(settings(true, true, false, false));
        assertEquals(dp(242), hooks.call(DialogResources.class, "getHeight", null,
                List.of(context, true, false), dp(272)));
        assertEquals(dp(396), hooks.call(DialogResources.class, "getShadowHeight", null,
                List.of(context, true, false, 0, false), dp(426)));
        assertEquals(dp(272), hooks.call(DialogResources.class, "getHeight", null,
                List.of(context, true, true), dp(272)));
        assertEquals(dp(272), hooks.call(DialogResources.class, "getHeight", null,
                List.of(context, false, false), dp(272)));
    }

    @Test public void nativePasswordGateStaysOutsideTheExtendedMenuPreference() throws Throwable {
        Registry hooks = install(Map.of(SystemMenuHooks.POWER_UTIL, PowerUtil.class));
        SystemMenuHooks.updateConfig(settings(false, false, true, true));
        boolean passwordEnabled = true;
        boolean extended = !passwordEnabled && (Boolean) hooks.call(PowerUtil.class,
                "isExtendedPowerMenu", null, List.of(context), false);
        assertFalse(extended);
        assertEquals(Boolean.TRUE, hooks.call(PowerUtil.class, "isExtendedPowerMenu", null,
                List.of(context), false));
        SystemMenuHooks.updateConfig(settings(false, false, true, false));
        assertEquals(Boolean.FALSE, hooks.call(PowerUtil.class, "isExtendedPowerMenu", null,
                List.of(context), true));
        SystemMenuHooks.updateConfig(settings(false, false, false, true));
        assertEquals(Boolean.FALSE, hooks.call(PowerUtil.class, "isExtendedPowerMenu", null,
                List.of(context), false));
    }

    @Test public void powerTracksRetainOutsideLabelsAndRestoreNativePlacement() throws Throwable {
        Registry hooks = install(Map.of(SystemMenuHooks.POWER_DIALOG, PowerDialog.class));
        PowerDialog dialog = new PowerDialog(context);
        SystemMenuHooks.updateConfig(settings(false, false, true, true).withTileScale(115).withSpacing(8));
        hooks.call(PowerDialog.class, "initViews", dialog, List.of(), null);
        for (int i = 0; i < 3; i++) assertFalse(dialog.mSliderViews.valueAt(i).getClipToOutline());
        assertEquals(1.15f, dialog.mSliderViews.get(0).getScaleX(), .001f);
        assertTrue(((FrameLayout.LayoutParams) dialog.mSliderViews.get(-1).getLayoutParams()).rightMargin > 12);
        SystemMenuHooks.updateConfig(settings(false, false, false, false));
        for (int i = 0; i < 3; i++) {
            View track = dialog.mSliderViews.valueAt(i);
            assertEquals(1f, track.getScaleX(), 0f);
            assertEquals(12, ((FrameLayout.LayoutParams) track.getLayoutParams()).rightMargin);
            assertSame(dialog.originals.get(track), track.getBackground());
        }
    }

    @Test public void volumeProgressRetainsItsAudioLevelClipAndRestoresItsDrawable() throws Throwable {
        Registry hooks = install(Map.of(SystemMenuHooks.VOLUME_CONTROLLER, VolumeController.class));
        VolumeController controller = new VolumeController(context);
        View progress = controller.mColumns.get(0).progressView;
        ViewOutlineProvider levelOutline = progress.getOutlineProvider();
        Object original = progress.getBackground();
        SystemMenuHooks.updateConfig(settings(true, false, false, false).withSurface(ControlCenterSurface.DARK));
        hooks.call(VolumeController.class, "initPanelView", controller, List.of(), null);
        assertSame(levelOutline, progress.getOutlineProvider());
        assertTrue(progress.getClipToOutline());
        assertSame("The audio level fill is never replaced", original, progress.getBackground());
        SystemMenuHooks.updateConfig(settings(false, false, false, false));
        assertSame(original, progress.getBackground());
        assertEquals(1, controller.sizeRefreshes);
    }

    @Test public void powerShaderIsPreservedUntilASuccessfulCustomSurfaceOwnsTheTrack() throws Throwable {
        Registry hooks = install(Map.of(SystemMenuHooks.POWER_SLIDER, PowerSlider.class));
        PowerSlider slider = new PowerSlider(context);
        SystemMenuHooks.updateConfig(settings(false, false, true, true));
        SystemSurfaceStyle.applyTrack(slider, ControlCenterConfig.defaults(), true);
        assertEquals("native", hooks.call(PowerSlider.class, "onDraw", slider, List.of(new Canvas()), "native"));
        SystemMenuHooks.updateConfig(settings(false, false, true, true).withSurface(ControlCenterSurface.DARK));
        SystemSurfaceStyle.applyTrack(slider, ControlCenterConfig.defaults().withSurface(ControlCenterSurface.DARK), true);
        assertNull(hooks.call(PowerSlider.class, "onDraw", slider, List.of(new Canvas()), "native"));
        SystemSurfaceStyle.restore(slider);
        assertEquals("native", hooks.call(PowerSlider.class, "onDraw", slider, List.of(new Canvas()), "native"));
    }

    @Test public void absentOemClassesDoNotPreventOtherHooksAndOverloadsAreExact() {
        Registry hooks = install(Map.of(SystemMenuHooks.VOLUME_COLUMN_RES, ColumnResources.class));
        assertEquals(1, hooks.callbacks.size());
        assertTrue(hooks.callbacks.keySet().stream().allMatch(method -> method.getParameterCount() == 3));
    }

    @Test public void stockSingleSliderCreatesTwoSideTracksWithoutExtendedOemApi() throws Throwable {
        Registry hooks = install(Map.of(SystemMenuHooks.POWER_DIALOG, StockPowerDialog.class));
        StockPowerDialog dialog = new StockPowerDialog(context);
        SystemMenuHooks.updateConfig(settings(false, false, true, true));
        hooks.call(StockPowerDialog.class, "initViews", dialog, List.of(), null);
        assertEquals("Enabled three-column power menu must create two tracks on stock ROM", 3,
                dialog.mRoot.getChildCount());
        assertSame(dialog.mRoot, dialog.mSliderView.getParent());
        hooks.call(StockPowerDialog.class, "initViews", dialog, List.of(), null);
        assertEquals("Repeated lifecycle callbacks must not add duplicate tracks", 3, dialog.mRoot.getChildCount());
        SystemMenuHooks.updateConfig(settings(false, false, true, false));
        assertEquals("Turning off three columns restores the stock single slider", 1,
                dialog.mRoot.getChildCount());
    }

    @Test public void stockExtensionRestoresScaleAndHonorsShutdownPassword() throws Throwable {
        Registry hooks = install(Map.of(SystemMenuHooks.POWER_DIALOG, StockPowerDialog.class));
        StockPowerDialog dialog = new StockPowerDialog(context);
        SystemMenuHooks.updateConfig(settings(false, false, true, false).withTileScale(120));
        hooks.call(StockPowerDialog.class, "initViews", dialog, List.of(), null);
        assertEquals(1.2f, dialog.mSliderView.getScaleX(), .001f);
        SystemMenuHooks.updateConfig(settings(false, false, true, true).withTileScale(120));
        assertEquals(3, dialog.mRoot.getChildCount());
        assertEquals(1.2f, dialog.mSliderView.getScaleX(), .001f);
        dialog.mShutDownPasswordEnabled = true;
        hooks.call(StockPowerDialog.class, "initViews", dialog, List.of(), null);
        assertEquals(1, dialog.mRoot.getChildCount());
        SystemMenuHooks.updateConfig(settings(false, false, false, false));
        assertEquals(1f, dialog.mSliderView.getScaleX(), 0f);
    }

    @Test public void nativeSingleSliderAppearanceWorksWithThreeColumnsDisabled() throws Throwable {
        Registry hooks = install(Map.of(SystemMenuHooks.POWER_DIALOG, StockPowerDialog.class));
        StockPowerDialog dialog = new StockPowerDialog(context);
        Object original = dialog.mSliderView.getBackground();
        SystemMenuHooks.updateConfig(settings(false, false, true, false).withSurface(ControlCenterSurface.DARK));
        hooks.call(StockPowerDialog.class, "initViews", dialog, List.of(), null);
        assertEquals(1, dialog.mRoot.getChildCount());
        assertTrue(SystemSurfaceStyle.isApplied(dialog.mSliderView));
        SystemMenuHooks.updateConfig(settings(false, false, false, false));
        assertSame(original, dialog.mSliderView.getBackground());
    }

    private static final class StockPowerDialog {
        final Context mContext;
        final FrameLayout mRoot;
        final View mSliderView;
        boolean mShutDownPasswordEnabled;
        StockPowerDialog(Context context) {
            mContext = context;
            mRoot = new FrameLayout(context);
            mRoot.layout(0, 0, 1080, 2400);
            mSliderView = new View(context);
            mSliderView.setBackground(new ColorDrawable(Color.GRAY));
            mRoot.addView(mSliderView, new FrameLayout.LayoutParams(160, 700, android.view.Gravity.CENTER));
        }
        void initViews() { }
    }

    private ControlCenterConfig settings(boolean volume, boolean compact, boolean power, boolean columns) {
        return ControlCenterConfig.defaults().withSystemSurfaces(SystemSurfaceConfig.defaults()
                .withVolumeDialog(volume).withCompactVolume(compact)
                .withPowerMenu(power).withExtendedPowerMenu(columns));
    }

    private Registry install(Map<String, Class<?>> classes) {
        Registry registry = new Registry();
        SystemMenuHooks.install(getClass().getClassLoader(), registry, (name, loader) -> classes.get(name));
        return registry;
    }

    private int dp(int value) { return Math.round(context.getResources().getDisplayMetrics().density * value); }

    private static class Registry implements SystemMenuHooks.Registrar {
        final Map<Method, XposedInterface.Hooker> callbacks = new HashMap<>();
        @Override public boolean install(String id, Method method, XposedInterface.Hooker hooker) {
            callbacks.put(method, hooker); return true;
        }
        Object call(Class<?> type, String name, Object target, List<?> arguments, Object nativeResult) throws Throwable {
            Method selected = callbacks.keySet().stream().filter(method -> method.getDeclaringClass() == type
                    && method.getName().equals(name) && method.getParameterCount() == arguments.size()).findFirst().orElseThrow();
            XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {XposedInterface.Chain.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getThisObject" -> target;
                        case "getArgs" -> arguments;
                        case "proceed" -> nativeResult;
                        default -> null;
                    });
            return callbacks.get(selected).intercept(chain);
        }
    }

    private static class ColumnResources {
        static int getHeight(Context context, boolean dialog, boolean expanded) { return 0; }
        static int getHeight(Context context) { return 0; }
    }
    private static class DialogResources {
        static int getHeight(Context context, boolean dialog, boolean expanded) { return 0; }
        static int getShadowHeight(Context context, boolean footer, boolean expanded, int height, boolean dual) { return 0; }
    }
    private static class PowerUtil { static boolean isExtendedPowerMenu(Context context) { return false; } }
    private static class PowerSlider extends FrameLayout {
        PowerSlider(Context context) { super(context); }
        @Override public void onDraw(Canvas canvas) { }
    }
    private static class PowerDialog {
        final SparseArray<View> mSliderViews = new SparseArray<>();
        final FrameLayout mRoot;
        final Map<View, Object> originals = new HashMap<>();
        PowerDialog(Context context) {
            mRoot = new FrameLayout(context);
            mRoot.layout(0, 0, 1440, 3200);
            for (int i = -1; i <= 1; i++) {
                View view = new FrameLayout(context);
                view.setBackground(new ColorDrawable(Color.GRAY));
                originals.put(view, view.getBackground());
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(200, 1000);
                params.rightMargin = 12;
                mRoot.addView(view, params);
                mSliderViews.put(i, view);
            }
        }
        void initViews() { }
    }
    private static class VolumeController {
        final boolean mNeedShowDialog = true;
        final View mExpandBgView;
        final List<Column> mColumns;
        int sizeRefreshes;
        VolumeController(Context context) {
            mExpandBgView = new View(context);
            mExpandBgView.setBackground(new ColorDrawable(Color.GRAY));
            mColumns = List.of(new Column(context));
        }
        void initPanelView() { }
        void updateVolumePanelSize() { sizeRefreshes++; }
    }
    private static class Column {
        final boolean isNeedShowDialog = true;
        final View slider, progressView, view;
        Column(Context context) {
            slider = new SeekBar(context); progressView = new View(context); view = new FrameLayout(context);
            progressView.setBackground(new ColorDrawable(Color.WHITE));
            progressView.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) { outline.setRect(0, 70, 20, 100); }
            });
            progressView.setClipToOutline(true);
        }
        void setRadius(int radius) { }
    }
}

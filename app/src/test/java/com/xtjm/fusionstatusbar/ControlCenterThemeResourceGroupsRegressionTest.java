package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterThemeResourceGroupsRegressionTest {
    private static final int WIDTH = 160, HEIGHT = 100;

    @After public void clearMaterials() { ControlCenterMaterials.clearForTest(); }

    @Test public void aLatePluginResourceChangeRefreshesBodyAndRimWhileItsWindowAlreadyStaysDay() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup().visible();
        try {
            Activity activity = controller.get();
            FrameLayout content = new FrameLayout(activity);
            activity.setContentView(content);
            controller.visible();
            View window = activity.getWindow().getDecorView();
            setNight(window.getResources(), true);
            PluginContext plugin = new PluginContext(activity, true);
            QSCardItemView card = card(plugin);
            content.addView(card, new FrameLayout.LayoutParams(WIDTH, HEIGHT));
            render(card).recycle();
            assertTrue(card.isAttachedToWindow());
            assertSame(window, card.getRootView());
            assertNotSame("The plugin owns an independent Resources instance", window.getResources(),
                    card.getResources());
            register(card);
            window.getViewTreeObserver().dispatchOnPreDraw();

            int nativeCallsBeforeWindowChange = card.nativeCalls;
            setNight(window.getResources(), false);
            window.getViewTreeObserver().dispatchOnPreDraw();
            assertTrue("The existing window observer must actually process the first mode change",
                    card.nativeCalls > nativeCallsBeforeWindowChange);
            assertEquals("The plugin receives its resource change later", Configuration.UI_MODE_NIGHT_YES,
                    nightMode(plugin.getResources()));
            assertEquals(Configuration.UI_MODE_NIGHT_NO, nightMode(window.getResources()));

            setNight(plugin.getResources(), false);
            // Do not dispatch configuration callbacks or refresh material settings here.
            window.getViewTreeObserver().dispatchOnPreDraw();
            assertMatchesFreshDay(card);
            assertStableWindowDoesNoMaterialWork(window, card);
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void attachingAPreconfiguredCardDoesNotSwallowItsPendingNightToDayChange() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup().visible();
        try {
            Activity activity = controller.get();
            FrameLayout content = new FrameLayout(activity);
            activity.setContentView(content);
            controller.visible();
            View window = activity.getWindow().getDecorView();
            setNight(window.getResources(), false);

            QSCardItemView existing = card(activity);
            content.addView(existing, new FrameLayout.LayoutParams(WIDTH, HEIGHT));
            render(existing).recycle();
            register(existing);
            window.getViewTreeObserver().dispatchOnPreDraw();

            PluginContext plugin = new PluginContext(activity, true);
            QSCardItemView pending = card(plugin);
            render(pending).recycle();
            assertFalse(pending.isAttachedToWindow());
            register(pending);
            assertEquals(ControlCenterSurface.SOFT_GLASS.color,
                    ((GradientDrawable) pending.getBackground()).getColor().getDefaultColor());

            setNight(plugin.getResources(), false);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(WIDTH, HEIGHT);
            params.topMargin = HEIGHT + 4;
            content.addView(pending, params);
            assertTrue("The pending card must exercise a real attach callback", pending.isAttachedToWindow());
            assertSame(window, pending.getRootView());
            // Its pre-attach observer must migrate to the already-day window without
            // accepting day as an applied baseline while the old body/rim is still night.
            window.getViewTreeObserver().dispatchOnPreDraw();
            assertMatchesFreshDay(pending);
            assertStableWindowDoesNoMaterialWork(window, pending);
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void rootPreDrawInvalidatesTheAttachedGridGlassBeforeAnyDisplayListRedraw() throws Exception {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup().visible();
        ControlCenterRuntimeGrid.GridView grid = null;
        Drawable glass = null;
        Drawable.Callback previous = null;
        try {
            Activity activity = controller.get();
            FrameLayout content = new FrameLayout(activity);
            activity.setContentView(content);
            controller.visible();
            View window = activity.getWindow().getDecorView();
            setNight(window.getResources(), true);
            PluginContext plugin = new PluginContext(activity, true);
            grid = new ControlCenterRuntimeGrid.GridView(plugin, 4, 8, 12, 0, 1);
            QSTileItemView tile = new QSTileItemView(plugin);
            ImageView icon = new ImageView(plugin);
            icon.setImageDrawable(new android.graphics.drawable.ColorDrawable(Color.BLUE));
            tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
            grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("theme.hardware", 0, 0, 1, 1)
                    .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), -1);
            content.addView(grid, new FrameLayout.LayoutParams(400, 240));
            assertTrue("The grid must belong to an attached window", grid.isAttachedToWindow());
            grid.applyMaterials(settings());
            grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY));
            grid.layout(0, 0, 400, 240);
            Bitmap recorded = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888);
            try { grid.draw(new Canvas(recorded)); }
            finally { recorded.recycle(); }
            window.getViewTreeObserver().dispatchOnPreDraw();
            glass = decorationGlass(grid);
            Shader night = rimShader(glass);
            assertNotNull("The first draw records a real night decoration shader", night);
            previous = glass.getCallback();
            Invalidations invalidations = new Invalidations(previous);
            glass.setCallback(invalidations);

            setNight(window.getResources(), false);
            setNight(plugin.getResources(), false);
            window.getViewTreeObserver().dispatchOnPreDraw();
            // A cached HWUI display list can skip Drawable.draw entirely. The theme
            // observer must invalidate and update the existing decoration beforehand.
            assertNotSame("Root pre-draw must switch the grid shader before any subsequent draw", night,
                    rimShader(glass));
            assertEquals("A mode change requests one decoration display-list refresh", 1,
                    invalidations.count);
            Shader day = rimShader(glass);
            for (int frame = 0; frame < 30; frame++) window.getViewTreeObserver().dispatchOnPreDraw();
            assertSame(day, rimShader(glass));
            assertEquals("Stable root frames must not repeatedly invalidate the cached decoration", 1,
                    invalidations.count);
        } finally {
            if (glass != null) glass.setCallback(previous);
            if (grid != null) grid.releaseMaterials();
            controller.pause().stop().destroy();
        }
    }

    private static Drawable decorationGlass(ControlCenterRuntimeGrid.GridView grid) throws Exception {
        for (int index = 0; index < grid.getChildCount(); index++) {
            View view = grid.getChildAt(index);
            if (!view.getClass().getSimpleName().equals("DecorationView")) continue;
            Method current = ControlCenterSoftGlass.class.getDeclaredMethod("current", View.class);
            current.setAccessible(true);
            Object state = current.invoke(null, view);
            assertNotNull("The grid decoration must own a live glass overlay", state);
            Field drawable = state.getClass().getDeclaredField("drawable");
            drawable.setAccessible(true);
            return (Drawable) drawable.get(state);
        }
        throw new AssertionError("The fixture must create an actual grid decoration");
    }

    private static Shader rimShader(Drawable glass) throws Exception {
        Field rim = glass.getClass().getDeclaredField("rim");
        rim.setAccessible(true);
        return ((Paint) rim.get(glass)).getShader();
    }

    private static ControlCenterConfig settings() {
        return ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withBlur(100, 100, 100);
    }

    private static QSCardItemView card(Context context) {
        QSCardItemView card = new QSCardItemView(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff353840);
        background.setCornerRadius(20f);
        card.setBackground(background);
        return card;
    }

    private static void register(QSCardItemView card) {
        ControlCenterConfig settings = settings();
        ControlCenterMaterials.registerRoot(card, settings);
        Object[] mode = ControlCenterMaterials.nativeArguments(card, "setMiViewBlurMode",
                new Object[] {1}, settings, false);
        card.setMiViewBlurMode((Integer) mode[0]);
        ArrayList<Point> requested = new ArrayList<>(List.of(new Point(Color.CYAN, 3)));
        Object[] colors = ControlCenterMaterials.nativeArguments(card, "setMiBackgroundBlendColors",
                new Object[] {requested}, settings, false);
        @SuppressWarnings("unchecked") ArrayList<Point> transformed = (ArrayList<Point>) colors[0];
        card.setMiBackgroundBlendColors(transformed);
        ControlCenterMaterials.updateNativeSurface(card, settings, false);
    }

    private static void assertMatchesFreshDay(QSCardItemView actual) {
        QSCardItemView fresh = card(new PluginContext(RuntimeEnvironment.getApplication(), false));
        render(fresh).recycle();
        register(fresh);
        Bitmap expected = render(fresh), observed = render(actual);
        try {
            assertEquals("The existing card body must adopt the same day material as a fresh consumer",
                    expected.getPixel(WIDTH / 2, HEIGHT / 2), observed.getPixel(WIDTH / 2, HEIGHT / 2));
            for (int depth = 0; depth <= 8; depth++) {
                assertEquals("The existing card rim must adopt the fresh day shader at depth=" + depth,
                        expected.getPixel(WIDTH / 2, depth), observed.getPixel(WIDTH / 2, depth));
            }
            assertTrue("Body, gradient and rounded shape must match the fresh day rendering",
                    expected.sameAs(observed));
        } finally { expected.recycle(); observed.recycle(); }
    }

    private static void assertStableWindowDoesNoMaterialWork(View window, QSCardItemView card) {
        card.backgroundReads = 0;
        card.nativeCalls = 0;
        for (int frame = 0; frame < 30; frame++) window.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals("Stable resource groups must not rescan registered backgrounds each frame", 0,
                card.backgroundReads);
        assertEquals("Stable resource groups must not replay native material setters each frame", 0,
                card.nativeCalls);
    }

    private static Bitmap render(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
        Bitmap image = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    private static int nightMode(Resources resources) {
        return resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }

    @SuppressWarnings("deprecation")
    private static void setNight(Resources resources, boolean night) {
        Configuration mode = new Configuration(resources.getConfiguration());
        mode.uiMode = (mode.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        resources.updateConfiguration(mode, resources.getDisplayMetrics());
    }

    /** Mirrors a plugin context whose Resources configuration updates after its host window. */
    private static final class PluginContext extends ContextWrapper {
        private final Resources resources;
        @SuppressWarnings("deprecation")
        PluginContext(Context base, boolean night) {
            super(base);
            Configuration mode = new Configuration(base.getResources().getConfiguration());
            mode.uiMode = (mode.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            DisplayMetrics metrics = new DisplayMetrics();
            metrics.setTo(base.getResources().getDisplayMetrics());
            resources = new Resources(base.getAssets(), metrics, mode);
        }
        @Override public Resources getResources() { return resources; }
    }

    public static final class QSCardItemView extends FrameLayout {
        int backgroundReads, nativeCalls;
        int viewMode = 1, backgroundMode = 2, radius = 180;
        ArrayList<Point> colors = new ArrayList<>();
        QSCardItemView(Context context) { super(context); }
        @Override public Drawable getBackground() { backgroundReads++; return super.getBackground(); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiViewBlurMode(int value) { viewMode = value; nativeCalls++; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; nativeCalls++; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; nativeCalls++; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) {
            colors = new ArrayList<>(value);
            nativeCalls++;
        }
        public void clearMiBackgroundBlendColor() { colors.clear(); nativeCalls++; }
    }

    public static final class QSTileItemView extends FrameLayout {
        public int state = 1;
        QSTileItemView(Context context) { super(context); }
    }

    private static final class Invalidations implements Drawable.Callback {
        private final Drawable.Callback original;
        int count;
        Invalidations(Drawable.Callback original) { this.original = original; }
        @Override public void invalidateDrawable(Drawable drawable) {
            count++;
            if (original != null) original.invalidateDrawable(drawable);
        }
        @Override public void scheduleDrawable(Drawable drawable, Runnable action, long when) {
            if (original != null) original.scheduleDrawable(drawable, action, when);
        }
        @Override public void unscheduleDrawable(Drawable drawable, Runnable action) {
            if (original != null) original.unscheduleDrawable(drawable, action);
        }
    }
}

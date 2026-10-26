package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
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
public class ControlCenterGlassAutomaticThemeRegressionTest {
    private static final int BODY = 0xff353840, WIDTH = 180, HEIGHT = 110;
    private static final ControlCenterConfig SETTINGS = ControlCenterConfig.defaults().withEnabled(true)
            .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withCornerRadius(18)
            .withGlobalBorder(false, 1).withBlur(100, 100, 100)
            .withSystemSurfaces(SystemSurfaceConfig.defaults().withNotifications(true));
    private final List<Fixture> fixtures = new ArrayList<>();

    @After public void clear() {
        for (Fixture fixture : fixtures) fixture.close();
        ControlCenterMaterials.clearForTest();
    }

    @Test public void configuredOverlayUsesTheDayProfileOnItsNextDrawWithoutAnotherApply() throws Exception {
        assertAutomaticDraw(Kind.OVERLAY);
    }

    @Test public void actualGridDecorationUsesTheDayProfileWithoutAConfigurationCallback() throws Exception {
        assertAutomaticDraw(Kind.GRID_DECORATION);
    }

    @Test public void systemSurfaceUsesMatchingDayBodyAndReflectionWithoutAnotherApply() throws Exception {
        assertAutomaticDraw(Kind.SYSTEM);
    }

    @Test public void boundedNotificationUsesTheDayProfileWithoutRebindingItsBackground() throws Exception {
        Fixture fixture = fixture(Kind.NOTIFICATION, true);
        Drawable bounded = fixture.target.getBackground();
        Drawable material = ((NotificationSurfaceHooks.BoundedBackground) bounded).content;
        fixture.draw().recycle();
        changeResourcesOnly(fixture.target, false);
        Bitmap automatic = fixture.draw(), cold = fixture(Kind.NOTIFICATION, false).draw();
        try {
            assertTrue("A notification that missed the theme callback must draw like a freshly configured day notification",
                    cold.sameAs(automatic));
            assertSame("An automatic theme change must retain native notification clipping", bounded,
                    fixture.target.getBackground());
            assertSame("The existing styled material must be updated instead of re-wrapping the notification", material,
                    ((NotificationSurfaceHooks.BoundedBackground) fixture.target.getBackground()).content);
            assertSame(fixture.original, ((NotificationBackgroundView) fixture.target).mBackground);
        } finally { automatic.recycle(); cold.recycle(); }
        assertStableFrames(fixture);
    }

    @Test public void aNightMaterialRegisteredOffscreenResumesInDayWithoutNewSettings() throws Exception {
        ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup().visible();
        FrameLayout host = new FrameLayout(activity.get());
        activity.get().setContentView(host);
        QSCardItemView card = styledCard(true);
        Bitmap night = draw(card);
        changeResourcesOnly(card, false);
        host.addView(card, new FrameLayout.LayoutParams(WIDTH, HEIGHT));
        layout(host, WIDTH, HEIGHT);
        assertTrue("The same previously offscreen card must attach to a real window", card.isAttachedToWindow());
        try {
            View windowRoot = card.getRootView();
            // These are normal frame callbacks, not a configuration callback or a material refresh.
            windowRoot.getViewTreeObserver().dispatchOnPreDraw();
            windowRoot.getViewTreeObserver().dispatchOnPreDraw();
            Bitmap resumed = draw(card), cold = draw(styledCard(false));
            try {
                assertFalse("The fixture must have genuinely crossed theme profiles", night.sameAs(cold));
                assertTrue("Changing root ownership must not accept day as a new baseline while retaining night material",
                        cold.sameAs(resumed));
                assertEquals("No configuration callback was delivered to this surviving card", 0,
                        card.configurationCallbacks);
            } finally { resumed.recycle(); cold.recycle(); }
        } finally {
            night.recycle();
            host.removeView(card);
            activity.pause().stop().destroy();
            ControlCenterMaterials.refresh(SETTINGS.withEnabled(false));
        }
    }

    @Test public void detachingDuringNightAndReattachingInDayDoesNotRequireSettingsReplay() throws Exception {
        ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup().visible();
        FrameLayout host = new FrameLayout(activity.get());
        activity.get().setContentView(host);
        Fixture fixture = fixture(Kind.SYSTEM, true);
        host.addView(fixture.root, new FrameLayout.LayoutParams(WIDTH, HEIGHT));
        layout(host, WIDTH, HEIGHT);
        assertTrue(fixture.root.isAttachedToWindow());
        fixture.draw().recycle();
        host.removeView(fixture.root);
        assertFalse(fixture.root.isAttachedToWindow());
        changeResourcesOnly(fixture.target, false);
        try {
            host.addView(fixture.root, new FrameLayout.LayoutParams(WIDTH, HEIGHT));
            layout(host, WIDTH, HEIGHT);
            Bitmap resumed = fixture.draw(), cold = fixture(Kind.SYSTEM, false).draw();
            try { assertTrue("Reattachment must reconcile the surviving drawable with its consumer's current mode",
                    cold.sameAs(resumed)); }
            finally { resumed.recycle(); cold.recycle(); }
            assertStableFrames(fixture);
        } finally {
            host.removeView(fixture.root);
            activity.pause().stop().destroy();
        }
    }

    @Test public void eachSurvivingConsumerKeepsItsOwnModeInsteadOfFollowingAnUnrelatedRoot() throws Exception {
        Fixture changed = fixture(Kind.OVERLAY, true), unchanged = fixture(Kind.OVERLAY, true);
        Bitmap untouchedNight = unchanged.draw();
        changed.draw().recycle();
        changeResourcesOnly(changed.target, false);
        Bitmap day = changed.draw(), coldDay = fixture(Kind.OVERLAY, false).draw(), stillNight = unchanged.draw();
        try {
            assertTrue(coldDay.sameAs(day));
            assertTrue("A different view's theme change must not select a global glass profile",
                    untouchedNight.sameAs(stillNight));
        } finally { untouchedNight.recycle(); day.recycle(); coldDay.recycle(); stillNight.recycle(); }
    }

    @Test public void repeatedDayNightTransitionsReuseTheSameConsumersWithoutSettingsReplay() throws Exception {
        for (Kind kind : Kind.values()) {
            Fixture surviving = fixture(kind, true);
            surviving.draw().recycle();
            for (boolean night : new boolean[] {false, true, false}) {
                changeResourcesOnly(surviving.target, night);
                Bitmap actual = surviving.draw(), expected = fixture(kind, night).draw();
                try {
                    assertTrue("The same " + kind + " must follow repeated automatic transitions; night=" + night,
                            expected.sameAs(actual));
                } finally { actual.recycle(); expected.recycle(); }
            }
            assertStableFrames(surviving);
        }
    }

    @Test public void densityOnlyChangeKeepsSystemBodyAndReflectionConsistentWithoutAConfigurationCallback() {
        View surviving = new View(context(false)), cold = new View(context(false));
        surviving.setBackground(background()); cold.setBackground(background());
        layout(surviving, WIDTH, HEIGHT); layout(cold, WIDTH, HEIGHT);
        SystemSurfaceStyle.apply(surviving, SETTINGS, true);
        Bitmap initial = draw(surviving);
        changeDensityResourcesOnly(surviving, 2f);
        changeDensityResourcesOnly(cold, 2f);
        SystemSurfaceStyle.apply(cold, SETTINGS, true);
        Bitmap automatic = draw(surviving), expected = draw(cold);
        try {
            assertFalse("The fixture must exercise a visible density-dependent radius and reflection", initial.sameAs(expected));
            assertTrue("The surviving body radius and glass edge must both use the current consumer density",
                    expected.sameAs(automatic));
        } finally {
            initial.recycle(); automatic.recycle(); expected.recycle();
            SystemSurfaceStyle.restore(surviving); SystemSurfaceStyle.restore(cold);
        }
    }

    private void assertAutomaticDraw(Kind kind) throws Exception {
        Fixture fixture = fixture(kind, true);
        Bitmap night = fixture.draw();
        Drawable sameBackground = fixture.target.getBackground();
        changeResourcesOnly(fixture.target, false);
        Bitmap automatic = fixture.draw(), cold = fixture(kind, false).draw();
        try {
            assertFalse("The comparison must exercise different night/day profiles", night.sameAs(cold));
            assertTrue("A surviving " + kind + " must draw the new day profile without apply, settings, or configuration callbacks",
                    cold.sameAs(automatic));
            assertSame("Changing the shader profile does not replace the existing background", sameBackground,
                    fixture.target.getBackground());
        } finally { night.recycle(); automatic.recycle(); cold.recycle(); }
        assertStableFrames(fixture);
    }

    private static void assertStableFrames(Fixture fixture) throws Exception {
        Drawable glass = glass(fixture);
        Paint rim = (Paint) field(glass, "rim").get(glass);
        Shader stable = rim.getShader();
        Object shape = field(glass, "shape").get(glass);
        Field stroke = field(shape, "strokePaint");
        CountingPaint counter = new CountingPaint((Paint) stroke.get(shape));
        stroke.set(shape, counter);
        Drawable.Callback previous = glass.getCallback();
        Invalidations invalidations = new Invalidations();
        glass.setCallback(invalidations);
        Bitmap image = Bitmap.createBitmap(fixture.target.getWidth(), fixture.target.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        try {
            for (int frame = 0; frame < 30; frame++) {
                image.eraseColor(Color.TRANSPARENT);
                fixture.target.draw(canvas);
                assertSame("A stable mode must not rebuild its shader on every draw", stable, rim.getShader());
            }
            assertEquals("Mode polling must not rebuild warmed stroke geometry", 0, counter.calls);
            assertEquals("Mode polling must not schedule an endless redraw loop", 0, invalidations.calls);
        } finally { glass.setCallback(previous); image.recycle(); }
    }

    private static Drawable glass(Fixture fixture) throws Exception {
        if (fixture.kind == Kind.SYSTEM || fixture.kind == Kind.NOTIFICATION) {
            Drawable material = fixture.target.getBackground();
            if (material instanceof NotificationSurfaceHooks.BoundedBackground bounded) material = bounded.content;
            return (Drawable) field(material, "reflection").get(material);
        }
        Method current = ControlCenterSoftGlass.class.getDeclaredMethod("current", View.class);
        current.setAccessible(true);
        Object state = current.invoke(null, fixture.target);
        assertNotNull(state);
        return (Drawable) field(state, "drawable").get(state);
    }

    private Fixture fixture(Kind kind, boolean night) {
        Fixture fixture = new Fixture(kind, night);
        fixtures.add(fixture);
        return fixture;
    }

    private enum Kind { OVERLAY, GRID_DECORATION, SYSTEM, NOTIFICATION }

    private static final class Fixture {
        final Kind kind;
        final View root, target;
        final GradientDrawable original;
        Fixture(Kind kind, boolean night) {
            this.kind = kind;
            Context context = context(night);
            original = background();
            if (kind == Kind.NOTIFICATION) {
                NotificationRow row = new NotificationRow(context);
                NotificationBackgroundView background = row.mBackgroundNormal;
                background.mBackground = original;
                background.setBackground(original);
                layout(row, WIDTH, 160);
                NotificationSurfaceHooks.applyRow(row, FusionConfig.defaults().withControlCenter(SETTINGS));
                root = row; target = background;
            } else if (kind == Kind.GRID_DECORATION) {
                ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(context, 4, 8, 18, 0, 1);
                QSTileItemView tile = new QSTileItemView(context);
                ImageView icon = new ImageView(context);
                icon.setImageDrawable(new Glyph());
                tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
                grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("rotation", 0, 0, 1, 1)
                        .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), -1);
                grid.applyMaterials(SETTINGS);
                layout(grid, 400, 180);
                View decoration = null;
                for (int index = 0; index < grid.getChildCount(); index++) {
                    View child = grid.getChildAt(index);
                    if (child.getClass().getSimpleName().equals("DecorationView")) decoration = child;
                }
                assertNotNull("The fixture must render the actual grid decoration consumer", decoration);
                root = grid; target = decoration;
            } else {
                FrameLayout view = new FrameLayout(context);
                view.setBackground(original);
                addContent(view);
                layout(view, WIDTH, HEIGHT);
                if (kind == Kind.SYSTEM) SystemSurfaceStyle.apply(view, SETTINGS, true);
                else ControlCenterSoftGlass.apply(view, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
                root = view; target = view;
            }
        }
        Bitmap draw() { return ControlCenterGlassAutomaticThemeRegressionTest.draw(target); }
        void close() {
            if (kind == Kind.NOTIFICATION) NotificationSurfaceHooks.applyRow(root,
                    FusionConfig.defaults().withControlCenter(SETTINGS.withSystemSurfaces(
                            SETTINGS.systemSurfaces.withNotifications(false))));
            else if (kind == Kind.SYSTEM) SystemSurfaceStyle.restore(target);
            else if (kind == Kind.GRID_DECORATION) ((ControlCenterRuntimeGrid.GridView) root).releaseMaterials();
            else ControlCenterSoftGlass.apply(target, false, 0);
        }
    }

    private static QSCardItemView styledCard(boolean night) {
        QSCardItemView card = new QSCardItemView(context(night));
        card.setBackground(background());
        addContent(card);
        layout(card, WIDTH, HEIGHT);
        ControlCenterMaterials.registerRoot(card, SETTINGS);
        return card;
    }

    private static GradientDrawable background() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(BODY);
        background.setCornerRadius(18f);
        return background;
    }

    private static void addContent(FrameLayout view) {
        View icon = new View(view.getContext());
        icon.setBackgroundColor(Color.BLUE);
        view.addView(icon, new FrameLayout.LayoutParams(20, 20, Gravity.CENTER));
        TextView label = new TextView(view.getContext());
        label.setText("WLAN"); label.setTextColor(Color.WHITE);
        label.setTextSize(16f);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(70, 28);
        labelParams.leftMargin = 72; labelParams.topMargin = 68;
        view.addView(label, labelParams);
    }

    @SuppressWarnings("deprecation")
    private static Context context(boolean night) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration mode = new Configuration(app.getResources().getConfiguration());
        mode.uiMode = (mode.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        Context configured = app.createConfigurationContext(mode);
        Resources source = configured.getResources();
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setTo(source.getDisplayMetrics());
        // Equal override configurations can share Resources in ResourcesManager. Give
        // each consumer its own resource group so changing one does not mutate the control fixture.
        Resources isolated = new Resources(source.getAssets(), metrics, new Configuration(mode));
        return new ContextWrapper(configured) {
            @Override public Resources getResources() { return isolated; }
        };
    }

    @SuppressWarnings("deprecation")
    private static void changeResourcesOnly(View consumer, boolean night) {
        Configuration mode = new Configuration(consumer.getResources().getConfiguration());
        mode.uiMode = (mode.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        consumer.getResources().updateConfiguration(mode, consumer.getResources().getDisplayMetrics());
    }

    @SuppressWarnings("deprecation")
    private static void changeDensityResourcesOnly(View consumer, float scale) {
        Resources resources = consumer.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.setTo(resources.getDisplayMetrics());
        metrics.densityDpi = Math.round(metrics.densityDpi * scale);
        metrics.density *= scale;
        metrics.scaledDensity *= scale;
        configuration.densityDpi = metrics.densityDpi;
        resources.updateConfiguration(configuration, metrics);
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    private static Field field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class CountingPaint extends Paint {
        int calls;
        CountingPaint(Paint original) { super(original); }
        @Override public boolean getFillPath(Path source, Path destination) {
            calls++;
            return super.getFillPath(source, destination);
        }
    }

    private static final class Invalidations implements Drawable.Callback {
        int calls;
        @Override public void invalidateDrawable(Drawable drawable) { calls++; }
        @Override public void scheduleDrawable(Drawable drawable, Runnable action, long time) { }
        @Override public void unscheduleDrawable(Drawable drawable, Runnable action) { }
    }

    public static final class QSCardItemView extends FrameLayout {
        int configurationCallbacks;
        QSCardItemView(Context context) { super(context); }
        @Override protected void onConfigurationChanged(Configuration configuration) {
            configurationCallbacks++;
            super.onConfigurationChanged(configuration);
        }
    }
    public static final class QSTileItemView extends FrameLayout {
        public int state = 1;
        QSTileItemView(Context context) { super(context); }
    }
    public static final class NotificationRow extends FrameLayout {
        final NotificationBackgroundView mBackgroundNormal;
        NotificationRow(Context context) {
            super(context);
            mBackgroundNormal = new NotificationBackgroundView(context);
            addView(mBackgroundNormal, new FrameLayout.LayoutParams(-1, -1));
        }
    }
    public static final class NotificationBackgroundView extends View {
        int mActualHeight = 72, mActualWidth = -1;
        int mClipTopAmount, mClipBottomAmount, mDrawableAlpha = 255;
        boolean mExpandAnimationRunning, mBottomIsRounded, mBottomAmountClips = true;
        Drawable mBackground;
        NotificationBackgroundView(Context context) { super(context); }
    }
    private static final class Glyph extends Drawable {
        private final Paint paint = new Paint();
        @Override public void draw(Canvas canvas) {
            paint.setColor(Color.BLUE);
            canvas.drawRect(getBounds().width() * .4f, getBounds().height() * .4f,
                    getBounds().width() * .6f, getBounds().height() * .6f, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

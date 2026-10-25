package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.Map;
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
public class NotificationGlassThemeRestoreRegressionTest {
    private static final int OEM_COLOR = 0xff224466;

    @After public void clearMaterials() { ControlCenterMaterials.clearForTest(); }

    @Test public void themeRefreshKeepsTheOwnedBoundsWrapperAndTheActualOemBackground() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.apply();
            NotificationSurfaceHooks.BoundedBackground bounded =
                    (NotificationSurfaceHooks.BoundedBackground) fixture.background.getBackground();
            Drawable material = bounded.content;
            fixture.theme(true);

            // Check identity before any DEFAULT drawing: adopting this wrapper as the
            // OEM original would create material -> bounded -> material recursion.
            assertSame("Theme refresh must not capture the module bounds wrapper as the OEM original",
                    fixture.original, originalOf(fixture.background));
            assertSame("The notification must retain actual-height clipping after a theme refresh",
                    bounded, fixture.background.getBackground());
            assertSame("Theme refresh reuses the same styled material", material, bounded.content);
            assertNotSame("A styled material must never store itself as its original",
                    material, field(material, "original"));
            assertNotSame("The original must not lead back through the owning bounds wrapper",
                    bounded, field(material, "original"));
        } finally { fixture.disable(); }
    }

    @Test public void switchingToDefaultAndDisablingAfterAThemeChangeRestoreTheOemSurface() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.apply();
            NotificationSurfaceHooks.BoundedBackground bounded =
                    (NotificationSurfaceHooks.BoundedBackground) fixture.background.getBackground();
            fixture.theme(true);
            assertSame("Reject a self-referential background before attempting native DEFAULT drawing",
                    fixture.original, originalOf(fixture.background));

            NotificationSurfaceHooks.applyRow(fixture.row, fixture.settings.withControlCenter(
                    fixture.settings.controlCenter.withSurface(ControlCenterSurface.DEFAULT)));
            assertSame(bounded, fixture.background.getBackground());
            assertSame("DEFAULT must draw the actual OEM original", fixture.original,
                    field(bounded.content, "original"));
            Bitmap pixels = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
            try {
                fixture.background.draw(new Canvas(pixels));
                assertEquals("The native original remains drawable after a day/night transition",
                        OEM_COLOR, pixels.getPixel(80, 30));
                assertEquals("The bounded DEFAULT surface still honors native actual height", 0,
                        pixels.getPixel(80, 120));
            } finally { pixels.recycle(); }

            fixture.disable();
            assertSame("Disabling must restore the OEM drawable rather than a module wrapper",
                    fixture.original, fixture.background.getBackground());
            assertSame(fixture.row, fixture.label.getParent());
            assertEquals("Notification title", fixture.label.getText().toString());
            assertEquals(20, fixture.label.getTop());
            assertEquals(1f, fixture.label.getAlpha(), 0f);
        } finally { fixture.disable(); }
    }

    private static Object originalOf(View background) throws Exception {
        Field surfaces = SystemSurfaceStyle.class.getDeclaredField("SURFACES");
        surfaces.setAccessible(true);
        Object state = ((Map<?, ?>) surfaces.get(null)).get(background);
        assertNotNull("The fixture must own a registered system surface", state);
        return field(state, "background");
    }

    private static Object field(Object object, String name) throws Exception {
        Field value = object.getClass().getDeclaredField(name);
        value.setAccessible(true);
        return value.get(object);
    }

    private static final class Fixture {
        final Context context;
        final NotificationRow row;
        final NotificationBackgroundView background;
        final GradientDrawable original = new GradientDrawable();
        final TextView label;
        final FusionConfig settings = FusionConfig.defaults().withControlCenter(
                ControlCenterConfig.defaults().withEnabled(true)
                        .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS)
                        .withBlur(100, 100, 100));

        Fixture() {
            Context app = RuntimeEnvironment.getApplication();
            Configuration configuration = new Configuration(app.getResources().getConfiguration());
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_NO;
            context = app.createConfigurationContext(configuration);
            row = new NotificationRow(context);
            background = row.mBackgroundNormal;
            original.setColor(OEM_COLOR);
            original.setCornerRadius(12);
            background.mBackground = original;
            background.setBackground(original);
            label = new TextView(context);
            label.setText("Notification title");
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(100, 30);
            labelParams.topMargin = 20;
            row.addView(label, labelParams);
            row.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY));
            row.layout(0, 0, 160, 160);
        }

        void apply() { NotificationSurfaceHooks.applyRow(row, settings); }

        @SuppressWarnings("deprecation")
        void theme(boolean night) {
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            context.getResources().updateConfiguration(configuration, context.getResources().getDisplayMetrics());
            background.getViewTreeObserver().dispatchOnPreDraw();
        }

        void disable() {
            NotificationSurfaceHooks.applyRow(row, settings.withControlCenter(settings.controlCenter
                    .withSystemSurfaces(settings.controlCenter.systemSurfaces.withNotifications(false))));
        }
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
}

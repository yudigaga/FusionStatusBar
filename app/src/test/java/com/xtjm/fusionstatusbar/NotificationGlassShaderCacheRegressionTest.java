package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import java.lang.reflect.Field;
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
public class NotificationGlassShaderCacheRegressionTest {
    @After public void clearMaterials() { ControlCenterMaterials.clearForTest(); }

    @Test public void unchangedCollapsedRefreshReusesTheShaderForTheActualDrawableBounds() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.applyAndDraw();
            Shader initial = rimShader(fixture.background);
            assertNotNull(initial);
            for (int update = 0; update < 8; update++) {
                fixture.applyAndDraw();
                assertSame("An unchanged collapsed notification must not alternate shaders for full and actual height",
                        initial, rimShader(fixture.background));
            }
        } finally { fixture.restore(); }
    }

    @Test public void nativeFadeChangesOpacityWithoutRebuildingTheGlassGradient() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.applyAndDraw();
            Shader initial = rimShader(fixture.background);
            for (int alpha : new int[] {128, 32, 0, 255}) {
                fixture.original.setAlpha(alpha);
                fixture.draw();
                assertSame("Native fade changes paint alpha, not the material shader", initial,
                        rimShader(fixture.background));
            }
        } finally { fixture.restore(); }
    }

    @Test public void actualHeightChangesReplaceTheShaderOnceAndThenReuseIt() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.applyAndDraw();
            Shader collapsed = rimShader(fixture.background);
            fixture.background.mActualHeight = 96;
            fixture.draw();
            Shader expanded = rimShader(fixture.background);
            assertNotSame("The visible height defines the gradient geometry", collapsed, expanded);
            fixture.draw();
            assertSame("Stable actual height reuses its gradient", expanded, rimShader(fixture.background));
        } finally { fixture.restore(); }
    }

    @Test public void systemThemeChangesReplaceTheShaderOnceWithoutReturningToFullHeight() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.applyAndDraw();
            Shader day = rimShader(fixture.background);
            setNight(fixture.background);
            fixture.applyAndDraw();
            Shader night = rimShader(fixture.background);
            assertNotSame("A consumer theme change needs the night glass profile", day, night);
            fixture.applyAndDraw();
            assertSame("Unchanged night refreshes keep the actual-bounds shader", night,
                    rimShader(fixture.background));
        } finally { fixture.restore(); }
    }

    private static Shader rimShader(View background) throws Exception {
        Drawable styled = ((NotificationSurfaceHooks.BoundedBackground) background.getBackground()).content;
        Field reflection = SystemSurfaceStyle.SurfaceDrawable.class.getDeclaredField("reflection");
        reflection.setAccessible(true);
        Object glass = reflection.get(styled);
        assertNotNull(glass);
        Field rim = glass.getClass().getDeclaredField("rim");
        rim.setAccessible(true);
        return ((Paint) rim.get(glass)).getShader();
    }

    @SuppressWarnings("deprecation")
    private static void setNight(View view) {
        Configuration mode = new Configuration(view.getResources().getConfiguration());
        mode.uiMode = (mode.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        view.getResources().updateConfiguration(mode, view.getResources().getDisplayMetrics());
    }

    private static final class Fixture {
        final NotificationGlassBoundsRegressionTest.NotificationRow row;
        final NotificationGlassBoundsRegressionTest.NotificationBackgroundView background;
        final GradientDrawable original = new GradientDrawable();
        final FusionConfig config;

        Fixture() {
            Context app = RuntimeEnvironment.getApplication();
            Configuration mode = new Configuration(app.getResources().getConfiguration());
            mode.uiMode = (mode.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
            Context context = app.createConfigurationContext(mode);
            row = new NotificationGlassBoundsRegressionTest.NotificationRow(context);
            background = row.mBackgroundNormal;
            original.setColor(0xff353840);
            original.setCornerRadius(12f);
            background.mBackground = original;
            background.setBackground(original);
            background.mActualHeight = 64;
            row.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY));
            row.layout(0, 0, 160, 160);
            config = FusionConfig.defaults().withControlCenter(ControlCenterConfig.defaults().withEnabled(true)
                    .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withCornerRadius(12)
                    .withSystemSurfaces(SystemSurfaceConfig.defaults().withNotifications(true)));
        }

        void applyAndDraw() {
            NotificationSurfaceHooks.applyRow(row, config);
            draw();
        }

        void draw() {
            Bitmap image = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
            try { background.draw(new Canvas(image)); }
            finally { image.recycle(); }
        }

        void restore() {
            NotificationSurfaceHooks.applyRow(row, config.withControlCenter(config.controlCenter
                    .withSystemSurfaces(config.controlCenter.systemSurfaces.withNotifications(false))));
        }
    }
}

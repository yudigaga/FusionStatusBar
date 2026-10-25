package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
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
public class ControlCenterGlassSoftEdgeRegressionTest {
    private static final int WIDTH = 180, HEIGHT = 110, BODY = 0xff353840;

    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void qsAndSystemSurfacesUseTheSameAutomaticGlassEdgeInBothThemes() {
        for (boolean night : new boolean[] {false, true}) {
            Context context = context(night);
            QSCardItemView qs = new QSCardItemView(context);
            FrameLayout notification = new FrameLayout(context);
            configure(qs); configure(notification);
            ControlCenterConfig config = ControlCenterConfig.defaults().withEnabled(true)
                    .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withCornerRadius(24)
                    .withGlobalBorder(false, 1).withBlur(100, 100, 100);
            try {
                ControlCenterMaterials.registerRoot(qs, config);
                SystemSurfaceStyle.apply(notification, config, true);
                Bitmap qsImage = draw(qs), notificationImage = draw(notification);
                try {
                    assertEquals("The comparison must use the same material body", qsImage.getPixel(90, 90),
                            notificationImage.getPixel(90, 90));
                    for (int depth = 0; depth <= 7; depth++) {
                        assertEquals("QS and notification top reflection must share one profile at night=" + night
                                        + " depth=" + depth,
                                qsImage.getPixel(WIDTH / 2, depth), notificationImage.getPixel(WIDTH / 2, depth));
                        assertEquals("QS and notification side reflection must share one profile at night=" + night
                                        + " depth=" + depth,
                                qsImage.getPixel(depth, HEIGHT / 2), notificationImage.getPixel(depth, HEIGHT / 2));
                    }
                } finally { qsImage.recycle(); notificationImage.recycle(); }
            } finally {
                SystemSurfaceStyle.restore(notification);
                ControlCenterMaterials.refresh(config.withEnabled(false));
            }
        }
    }

    @Test public void daytimeReflectionHasADiffuseTailInsteadOfOnlyAHardOuterLine() {
        assertSoftReflection(false);
    }

    @Test public void nighttimeReflectionHasADiffuseTailInsteadOfOnlyAHardOuterLine() {
        assertSoftReflection(true);
    }

    @Test public void zeroStrengthAndDisablingRestoreTheUnpaintedEdgeWithoutChangingContent() {
        for (boolean night : new boolean[] {false, true}) {
            FrameLayout surface = new FrameLayout(context(night));
            configure(surface);
            Bitmap original = draw(surface);
            try {
                ControlCenterSoftGlass.apply(surface, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
                Bitmap visible = draw(surface);
                try { assertFalse("The fixture must visibly exercise the reflection", original.sameAs(visible)); }
                finally { visible.recycle(); }
                ControlCenterSoftGlass.apply(surface, ControlCenterSurface.HIGHLIGHT_GLASS, true, 0);
                Bitmap zero = draw(surface);
                try { assertTrue("Zero strength removes the reflection and leaves foreground content intact",
                        original.sameAs(zero)); }
                finally { zero.recycle(); }
                ControlCenterSoftGlass.apply(surface, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
                ControlCenterSoftGlass.apply(surface, ControlCenterSurface.HIGHLIGHT_GLASS, false, 100);
                Bitmap disabled = draw(surface);
                try { assertTrue("Disabling restores the same original shape and content", original.sameAs(disabled)); }
                finally { disabled.recycle(); }
            } finally {
                original.recycle();
                ControlCenterSoftGlass.apply(surface, false, 0);
            }
        }
    }

    private static void assertSoftReflection(boolean night) {
        FrameLayout surface = new FrameLayout(context(night));
        configure(surface);
        Bitmap original = draw(surface);
        ControlCenterSoftGlass.apply(surface, ControlCenterSurface.HIGHLIGHT_GLASS, true, 100);
        Bitmap reflected = draw(surface);
        try {
            float peak = 0f, tail = 0f;
            for (int depth = 0; depth <= 6; depth++) {
                float light = Math.max(0f, Color.luminance(reflected.getPixel(WIDTH / 2, depth))
                        - Color.luminance(original.getPixel(WIDTH / 2, depth)));
                peak = Math.max(peak, light);
                if (depth >= 2) tail += light;
            }
            assertTrue("The fixture must catch a visible local reflection", peak > .01f);
            assertTrue("A soft bevel needs a measurable fading reflection beyond its first two pixel rows; night="
                    + night + " peak=" + peak + " tail=" + tail, tail > peak * .05f);
            assertEquals("The reflection must fade before reaching the material body",
                    original.getPixel(WIDTH / 2, 10), reflected.getPixel(WIDTH / 2, 10));
            assertEquals("The rounded outer corner keeps its clipping", Color.TRANSPARENT, reflected.getPixel(1, 1));
            for (int y = 20; y < HEIGHT - 20; y++) {
                for (int x = 20; x < WIDTH - 20; x++) {
                    assertEquals("The glass edge must not repaint body pixels, text, or glyphs", original.getPixel(x, y),
                            reflected.getPixel(x, y));
                }
            }
        } finally {
            original.recycle(); reflected.recycle();
            ControlCenterSoftGlass.apply(surface, false, 0);
        }
    }

    private static void configure(FrameLayout surface) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(BODY);
        background.setCornerRadius(24f);
        surface.setBackground(background);
        View glyph = new View(surface.getContext());
        glyph.setBackgroundColor(Color.BLUE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(20, 20);
        iconParams.leftMargin = 30; iconParams.topMargin = 45;
        surface.addView(glyph, iconParams);
        TextView label = new TextView(surface.getContext());
        label.setText("WLAN");
        label.setTextColor(Color.WHITE);
        label.setTextSize(16f);
        label.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(85, 32);
        textParams.leftMargin = 68; textParams.topMargin = 39;
        surface.addView(label, textParams);
        surface.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        surface.layout(0, 0, WIDTH, HEIGHT);
    }

    private static Context context(boolean night) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return app.createConfigurationContext(configuration);
    }

    private static Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    public static final class QSCardItemView extends FrameLayout {
        QSCardItemView(Context context) { super(context); }
    }
}

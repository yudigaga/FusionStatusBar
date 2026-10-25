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
public class ControlCenterGlassBorderInteractionTest {
    private static final int BACKDROP = 0xff353840;

    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void sameRuntimeGlassAndBorderConfigurationRendersIdenticallyInEitherCreationOrder() {
        QSCardItemView glassFirst = card(context(false));
        QSCardItemView borderFirst = card(context(false));
        ControlCenterConfig both = settings(true, 4);
        ControlCenterMaterials.registerRoot(glassFirst, settings(false, 4));
        ControlCenterMaterials.registerRoot(glassFirst, both);
        ControlCenterMaterials.registerRoot(borderFirst, both.withSurface(ControlCenterSurface.DEFAULT));
        ControlCenterMaterials.registerRoot(borderFirst, both);
        Bitmap first = draw(glassFirst), second = draw(borderFirst);
        try {
            assertTrue("The same glass and border settings must not depend on which decoration was created first",
                    first.sameAs(second));
        } finally { first.recycle(); second.recycle(); }
    }

    @Test public void samePreviewGlassAndBorderConfigurationRendersIdenticallyInEitherCreationOrder() {
        ControlCenterCardView glassFirst = preview(context(false));
        ControlCenterCardView borderFirst = preview(context(false));
        glassFirst.setSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
        glassFirst.setGlobalBorder(true, 4);
        borderFirst.setGlobalBorder(true, 4);
        borderFirst.setSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
        layout(glassFirst); layout(borderFirst);
        Bitmap first = draw(glassFirst.getChildAt(0)), second = draw(borderFirst.getChildAt(0));
        try {
            assertTrue("The same preview settings must give the same glass edge in either toggle order",
                    first.sameAs(second));
        } finally { first.recycle(); second.recycle(); }
    }

    @Test public void nativeMaterialKeepsDirectionalReflectionAndRestoresGlassWhenBorderIsRemoved() {
        for (boolean night : new boolean[] {false, true}) {
            for (int width = 1; width <= 4; width++) {
                QSCardItemView card = card(context(night));
                ControlCenterConfig glass = settings(false, width);
                ControlCenterMaterials.registerRoot(card, glass);
                Bitmap glassOnly = draw(card);
                assertTrue("The fixture must contain a directional glass reflection", edgeRange(glassOnly) > 4);
                ControlCenterMaterials.refresh(settings(true, width));
                Bitmap bordered = draw(card);
                try {
                    assertTrue("A border must not make the glass rim uniform for width=" + width + " night=" + night,
                            edgeRange(bordered) > 4);
                    assertEquals("The border must not repaint the glass body", glassOnly.getPixel(40, 50),
                            bordered.getPixel(40, 50));
                    assertEquals("The foreground control stays visible", Color.BLUE, bordered.getPixel(80, 50));
                    assertEquals("Rounded corners retain their clipping", BACKDROP, bordered.getPixel(1, 1));
                    for (int refresh = 0; refresh < 3; refresh++) ControlCenterMaterials.refresh(settings(true, width));
                    Bitmap repeated = draw(card);
                    try { assertTrue("Stable refreshes must not accumulate border opacity", bordered.sameAs(repeated)); }
                    finally { repeated.recycle(); }
                    ControlCenterMaterials.refresh(glass);
                    Bitmap restored = draw(card);
                    try { assertTrue("Removing the border must recover the same glass profile", glassOnly.sameAs(restored)); }
                    finally { restored.recycle(); }
                } finally {
                    glassOnly.recycle();
                    bordered.recycle();
                    ControlCenterMaterials.refresh(glass.withEnabled(false));
                }
            }
        }
    }

    @Test public void applyingGlassAfterBorderAndChangingThemeKeepAVisibleNonuniformRim() {
        QSCardItemView card = card(context(false));
        ControlCenterConfig borderOnly = settings(true, 4).withSurface(ControlCenterSurface.DEFAULT);
        ControlCenterMaterials.registerRoot(card, borderOnly);
        ControlCenterMaterials.refresh(settings(true, 4));
        Bitmap day = draw(card);
        try {
            assertTrue("Enabling glass after the border retains its directional reflection", edgeRange(day) > 4);
            setNight(card, true);
            ControlCenterMaterials.refresh(settings(true, 4));
            Bitmap night = draw(card);
            try {
                assertTrue("Night glass retains a directional reflection", edgeRange(night) > 4);
                assertFalse("System theme changes must still update the consuming material", day.sameAs(night));
                assertEquals(Color.BLUE, night.getPixel(80, 50));
            } finally { night.recycle(); }
            setNight(card, false);
            ControlCenterMaterials.refresh(settings(true, 4));
            Bitmap restoredDay = draw(card);
            try { assertTrue("Switching back must restore the original day profile", day.sameAs(restoredDay)); }
            finally { restoredDay.recycle(); }
        } finally { day.recycle(); }
    }

    @Test public void previewBorderToggleOrderDoesNotRemoveTheGlassReflection() {
        for (boolean night : new boolean[] {false, true}) {
            for (int width = 1; width <= 4; width++) {
                for (boolean borderFirst : new boolean[] {false, true}) {
                    ControlCenterCardView preview = new ControlCenterCardView(context(night));
                    preview.bind(new ControlCenterCardView.Model("wifi", "", "WLAN", null,
                            false, false, false, true, ControlCenterLayoutPlan.Shape.RECTANGLE, 24, 100));
                    if (borderFirst) preview.setGlobalBorder(true, width);
                    preview.setSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
                    if (!borderFirst) preview.setGlobalBorder(true, width);
                    layout(preview);
                    View surface = preview.getChildAt(0);
                    Bitmap bordered = draw(surface);
                    try { assertTrue("Preview must preserve nonuniform glass reflection in either toggle order",
                            edgeRange(bordered) > 4); }
                    finally { bordered.recycle(); }
                    preview.setGlobalBorder(false, width);
                    Bitmap glassOnly = draw(surface);
                    try { assertTrue("Turning the preview border off must leave the glass reflection",
                            edgeRange(glassOnly) > 4); }
                    finally { glassOnly.recycle(); }
                }
            }
        }
    }

    private static ControlCenterConfig settings(boolean border, int width) {
        return ControlCenterConfig.defaults().withEnabled(true).withSurface(ControlCenterSurface.HIGHLIGHT_GLASS)
                .withGlobalBorder(border, width).withBlur(100, 100, 100);
    }

    private static QSCardItemView card(Context context) {
        QSCardItemView card = new QSCardItemView(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(BACKDROP);
        background.setCornerRadius(24);
        card.setBackground(background);
        View control = new View(context);
        control.setBackgroundColor(Color.BLUE);
        card.addView(control, new FrameLayout.LayoutParams(20, 20, Gravity.CENTER));
        layout(card);
        return card;
    }

    private static ControlCenterCardView preview(Context context) {
        ControlCenterCardView preview = new ControlCenterCardView(context);
        preview.bind(new ControlCenterCardView.Model("wifi", "", "WLAN", null,
                false, false, false, true, ControlCenterLayoutPlan.Shape.RECTANGLE, 24, 100));
        return preview;
    }

    private static Context context(boolean night) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return app.createConfigurationContext(configuration);
    }

    @SuppressWarnings("deprecation")
    private static void setNight(View view, boolean night) {
        Configuration configuration = new Configuration(view.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        view.getResources().updateConfiguration(configuration, view.getResources().getDisplayMetrics());
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 160, 100);
    }

    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(BACKDROP);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static int edgeRange(Bitmap image) {
        int width = image.getWidth(), height = image.getHeight();
        int[] samples = {image.getPixel(width / 2, 0), image.getPixel(width / 2, height - 1),
                image.getPixel(0, height / 2), image.getPixel(width - 1, height / 2)};
        int min = 255, max = 0;
        for (int color : samples) {
            min = Math.min(min, Color.red(color));
            max = Math.max(max, Color.red(color));
        }
        return max - min;
    }

    public static final class QSCardItemView extends FrameLayout {
        QSCardItemView(Context context) { super(context); }
    }
}

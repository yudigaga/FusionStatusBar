package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterSoftGlassTest {
    @After public void cleanup() { ControlCenterMaterials.clearForTest(); }

    @Test public void reflectionDoesNotInventASurfaceForNativeLayoutHolders() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View holder = new View(activity);
        holder.layout(0, 0, 100, 60);
        ControlCenterSoftGlass.apply(holder, true, 100);
        Bitmap image = draw(holder);
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 1));
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 30));
        image.recycle();
    }

    @Test public void reflectionUsesTheWrappedOvalSurface() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View surface = new View(activity);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(0xff353840);
        surface.setBackground(new InsetDrawable(oval, 10));
        surface.layout(0, 0, 120, 80);
        ControlCenterSoftGlass.apply(surface, true, 100);
        Bitmap image = draw(surface);
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 1));
        assertEquals(Color.TRANSPARENT, image.getPixel(60, 1));
        assertNotEquals(0xff353840, image.getPixel(60, 10));
        image.recycle();
    }

    @Test public void runtimeGlassHighlightsOnlyOuterSurfaceAndRestoresOnRefresh() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        QSCardItemView card = new QSCardItemView(activity);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff353840);
        background.setCornerRadius(24f);
        card.setBackground(background);
        QSTileItemIconView icon = new QSTileItemIconView(activity);
        icon.setBackgroundColor(Color.BLUE);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(30, 30);
        iconParams.leftMargin = 22;
        iconParams.topMargin = 50;
        card.addView(icon, iconParams);
        layout(card, 160, 100);

        ControlCenterConfig glass = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.SOFT_GLASS);
        ControlCenterMaterials.registerRoot(card, glass);
        Bitmap withGlass = draw(card);
        assertTrue("Glass rim pixels: " + Integer.toHexString(withGlass.getPixel(80, 1))
                        + " vs " + Integer.toHexString(withGlass.getPixel(80, 8)),
                Color.luminance(withGlass.getPixel(80, 1))
                        > Color.luminance(withGlass.getPixel(80, 8)) + 0.08f);
        assertEquals("Inner icon remains unobstructed below the reflection", Color.BLUE,
                withGlass.getPixel(35, 70));
        withGlass.recycle();

        ControlCenterMaterials.refresh(glass.withSurface(ControlCenterSurface.DEFAULT));
        Bitmap restored = draw(card);
        assertEquals(0xff353840, restored.getPixel(80, 1));
        assertEquals(Color.BLUE, restored.getPixel(35, 70));
        restored.recycle();
    }

    public static final class QSCardItemView extends FrameLayout {
        QSCardItemView(Activity activity) { super(activity); }
    }
    public static final class QSTileItemIconView extends FrameLayout {
        QSTileItemIconView(Activity activity) { super(activity); }
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
}

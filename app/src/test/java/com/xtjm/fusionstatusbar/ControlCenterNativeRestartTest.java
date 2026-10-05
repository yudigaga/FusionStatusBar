package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterNativeRestartTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void delayedNativeGlyphKeepsTheSameBorderAsAnImmediatelyBoundTile() {
        for (ControlCenterSurface surface : ControlCenterSurface.values()) {
            Bundle saved = new Bundle();
            ControlCenterConfig.defaults().withEnabled(true).withSurface(surface)
                    .withGlobalBorder(true, 2).writeTo(saved);
            ControlCenterConfig restored = ControlCenterConfig.fromBundle(saved);
            for (boolean delayed : new boolean[] {false, true}) {
                QSTileItemView tile = new QSTileItemView();
                ImageView icon = image(tile);
                if (!delayed) icon.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
                ControlCenterMaterials.registerRoot(tile, restored);
                if (delayed) icon.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
                layout(tile);
                Bitmap pixels = draw(tile);
                assertTrue("A late glyph after process restart must retain the same native border",
                        Color.alpha(pixels.getPixel(20, 50)) > 0);
                assertEquals("The surrounding cell must not become a rectangle", 0,
                        Color.alpha(pixels.getPixel(0, 0)));
                pixels.recycle();
            }
        }
    }

    @Test public void themeReplacesTheImageWhileSettingsStayUnchanged() {
        QSTileItemView tile = new QSTileItemView();
        ImageView oldIcon = image(tile);
        oldIcon.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true).withGlobalBorder(true, 2);
        ControlCenterMaterials.registerRoot(tile, settings);
        layout(tile);
        tile.removeView(oldIcon);
        ImageView replacement = image(tile);
        ControlCenterMaterials.registerRoot(tile, settings);
        replacement.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
        layout(tile);
        Bitmap pixels = draw(tile);
        assertTrue("Decorations follow a recreated empty image before its async content arrives",
                Color.alpha(pixels.getPixel(20, 50)) > 0);
        pixels.recycle();
    }

    @Test public void everyCustomSurfaceRestoresTheBackdropForNativeGlyphOnlyTiles() {
        for (ControlCenterSurface surface : ControlCenterSurface.values()) {
            if (surface == ControlCenterSurface.DEFAULT) continue;
            ControlCenterMaterials.clearForTest();
            QSTileItemView tile = new QSTileItemView();
            ImageView icon = image(tile);
            ColorDrawable glyph = new ColorDrawable(Color.BLUE);
            LayerDrawable layers = new LayerDrawable(new Drawable[] {glyph});
            layers.setLayerInset(0, 20, 20, 20, 20);
            icon.setImageDrawable(layers);
            layout(tile);
            var settings = ControlCenterConfig.defaults().withEnabled(true).withSurface(surface);
            ControlCenterMaterials.registerRoot(tile, settings);
            Bitmap pixels = draw(tile);
            int actual = pixels.getPixel(30, 50);
            assertEquals("Native glyph-only tile must render " + surface.name(),
                    Color.alpha(surface.color), Color.alpha(actual));
            // Skia's premultiplied bitmap conversion can round translucent RGB.
            assertEquals(Color.red(surface.color), Color.red(actual), 2);
            assertEquals(Color.green(surface.color), Color.green(actual), 2);
            assertEquals(Color.blue(surface.color), Color.blue(actual), 2);
            assertEquals("The foreground glyph stays native", Color.BLUE, pixels.getPixel(50, 50));
            assertSame(layers, icon.getDrawable());
            pixels.recycle();
            ControlCenterMaterials.refresh(settings.withSurface(ControlCenterSurface.DEFAULT));
            assertNull("Returning to original restores the original missing view background", icon.getBackground());
        }
    }

    @Test public void deferredIconCompletionRebindsMaterialsAndKeepsTheNativeOutline() {
        QSTileItemView tile = new QSTileItemView();
        ImageView icon = image(tile);
        var settings = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.SOFT_GLASS).withGlobalBorder(true, 2);
        ControlCenterMaterials.registerRoot(tile, settings);
        layout(tile);
        ViewOutlineProvider originalOutline = icon.getOutlineProvider();
        ColorDrawable nativeSurface = new ColorDrawable(Color.RED);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {nativeSurface, new ColorDrawable(Color.BLUE)});
        layers.setLayerInset(1, 20, 20, 20, 20);
        icon.setImageDrawable(layers);
        ControlCenterMaterials.nativeTileUpdated(icon, settings);
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, nativeSurface.getColor());
        assertNull("A drawable-backed tile must drop the temporary blur-only backing", icon.getBackground());
        assertSame(originalOutline, icon.getOutlineProvider());
        ControlCenterMaterials.refresh(settings.withEnabled(false));
        assertEquals(Color.RED, nativeSurface.getColor());
    }

    private ImageView image(QSTileItemView tile) {
        ImageView image = new ImageView(tile.getContext());
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        tile.addView(image, new FrameLayout.LayoutParams(60, 60, Gravity.CENTER));
        return image;
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 100, 100);
    }

    private static Bitmap draw(View view) {
        Bitmap result = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(result));
        return result;
    }

    static final class QSTileItemView extends FrameLayout {
        QSTileItemView() { super(RuntimeEnvironment.getApplication()); }
    }
}

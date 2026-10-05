package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.view.View;
import android.view.ViewOutlineProvider;
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
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterComponentShapeMaterialTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void softGlassAndBorderDoNotBrightenComponentCentersForAnyPreset() {
        for (boolean media : new boolean[] {true, false}) {
            for (ControlCenterCardStyle style : ControlCenterCardStyle.values()) {
                Fixture fixture = new Fixture(media, style, ControlCenterConfig.defaults().withEnabled(true)
                        .withSurface(ControlCenterSurface.SOFT_GLASS).withGlobalBorder(true, 1));
                Bitmap actual = fixture.draw();
                Bitmap expected = fixture.expected();
                int x = fixture.panel.getLeft() + fixture.panel.getWidth() / 2;
                int y = fixture.panel.getTop() + fixture.panel.getHeight() / 2;
                assertEquals("Full material must not add a light mask for " + style,
                        expected.getPixel(x, y), actual.getPixel(x, y));
                actual.recycle();
                expected.recycle();
            }
        }
    }

    @Test public void everyPresetFillsItsOwnShapeInsteadOfKeepingAnIndependentOemContour() {
        for (boolean media : new boolean[] {true, false}) {
            for (ControlCenterCardStyle style : ControlCenterCardStyle.values()) {
                Fixture fixture = new Fixture(media, style);
                Bitmap actual = fixture.draw();
                Bitmap expected = fixture.expected();
                int centerX = fixture.panel.getLeft() + fixture.panel.getWidth() / 2;
                int centerY = fixture.panel.getTop() + fixture.panel.getHeight() / 2;
                assertEquals(style + " center", expected.getPixel(centerX, centerY), actual.getPixel(centerX, centerY));
                assertEquals(style + " must not retain the native background's different corner mask",
                        expected.getPixel(fixture.panel.getLeft() + 18, fixture.panel.getTop() + 18),
                        actual.getPixel(fixture.panel.getLeft() + 18, fixture.panel.getTop() + 18));
                actual.recycle();
                expected.recycle();
            }
        }
    }

    @Test public void disablingCustomMaterialRestoresNativeBackgroundAndOutlineForEveryShape() {
        for (ControlCenterCardStyle style : ControlCenterCardStyle.values()) {
            Fixture fixture = new Fixture(false, style);
            fixture.draw().recycle();
            Drawable nativeBackground = ((DrawableWrapper) fixture.panel.getBackground()).getDrawable();
            assertEquals("The OEM background is retained unchanged behind the custom painter", 0xff9fa1a4,
                    ((GradientDrawable) nativeBackground).getColor().getDefaultColor());
            ControlCenterMaterials.syncComponentView(fixture.panel, fixture.item, fixture.settings.withEnabled(false));
            assertSame(nativeBackground, fixture.panel.getBackground());
            assertSame(ViewOutlineProvider.BACKGROUND, fixture.panel.getOutlineProvider());
            assertFalse(fixture.panel.getClipToOutline());
        }
    }

    @Test public void recreatedAndRetintedOemBackgroundCannotReintroduceItsOwnFill() {
        Fixture fixture = new Fixture(true, ControlCenterCardStyle.SUBTLE);
        fixture.draw().recycle();
        GradientDrawable replacement = nativeBackground(Color.MAGENTA);
        fixture.panel.setBackground(replacement);
        fixture.draw().recycle();
        replacement.setColor(Color.YELLOW);
        replacement.setCornerRadius(80f);
        Bitmap actual = fixture.draw();
        Bitmap expected = fixture.expected();
        int x = fixture.panel.getLeft() + 18;
        int y = fixture.panel.getTop() + 18;
        assertEquals("OEM refresh must not replace the published material", expected.getPixel(x, y), actual.getPixel(x, y));
        fixture.grid.releaseMaterials();
        assertSame("Release restores the current OEM drawable, not a stale predecessor", replacement, fixture.panel.getBackground());
        actual.recycle();
        expected.recycle();
    }

    private static GradientDrawable nativeBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(80f);
        return drawable;
    }

    private static final class Fixture {
        final Context context = RuntimeEnvironment.getApplication();
        final FrameLayout panel;
        final ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(context, 4, 8, 28, 0, 2);
        final ControlCenterLayoutPlan.Item item;
        final ControlCenterConfig settings;

        Fixture(boolean media, ControlCenterCardStyle style) {
            this(media, style, ControlCenterConfig.defaults().withEnabled(true)
                    .withSurface(ControlCenterSurface.DARK).withCornerRadius(28));
        }

        Fixture(boolean media, ControlCenterCardStyle style, ControlCenterConfig settings) {
            this.settings = settings;
            panel = media ? new MediaPlayerPanel(context) : new DeviceCenterEntryFrameLayout(context);
            panel.setBackground(nativeBackground(0xff9fa1a4));
            ControlCenterLayoutPlan.Item placement = ControlCenterLayoutPlan.Item.component(media
                    ? ControlCenterComponentSpec.MEDIA : ControlCenterComponentSpec.DEVICE_CENTER,
                    0, 0, media ? 2 : 4, media ? 2 : 1);
            item = style == ControlCenterCardStyle.CUSTOM ? placement.withShape(ControlCenterLayoutPlan.Shape.RECTANGLE, 13)
                    : style.apply(placement);
            grid.addTile(panel, item, -1);
            grid.applyMaterials(settings);
            grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
            grid.layout(0, 0, grid.getMeasuredWidth(), grid.getMeasuredHeight());
        }

        Bitmap draw() {
            Bitmap bitmap = Bitmap.createBitmap(grid.getWidth(), grid.getHeight(), Bitmap.Config.ARGB_8888);
            grid.draw(new Canvas(bitmap));
            return bitmap;
        }

        Bitmap expected() {
            Bitmap bitmap = Bitmap.createBitmap(grid.getWidth(), grid.getHeight(), Bitmap.Config.ARGB_8888);
            GradientDrawable surface = new GradientDrawable();
            surface.setColor(settings.surface.stateColor(false));
            if (item.shape == ControlCenterLayoutPlan.Shape.CIRCLE) surface.setShape(GradientDrawable.OVAL);
            else surface.setCornerRadius(item.shape == ControlCenterLayoutPlan.Shape.CAPSULE
                    ? Math.min(panel.getWidth(), panel.getHeight()) / 2f : item.cornerRadius > 0 ? item.cornerRadius : 28);
            surface.setBounds(panel.getLeft(), panel.getTop(), panel.getRight(), panel.getBottom());
            surface.draw(new Canvas(bitmap));
            return bitmap;
        }
    }

    public static final class MediaPlayerPanel extends FrameLayout {
        MediaPlayerPanel(Context context) { super(context); }
    }
    public static final class DeviceCenterEntryFrameLayout extends FrameLayout {
        DeviceCenterEntryFrameLayout(Context context) { super(context); }
    }
}

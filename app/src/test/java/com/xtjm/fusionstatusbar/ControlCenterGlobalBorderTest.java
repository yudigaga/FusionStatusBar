package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterGlobalBorderTest {
    @After public void cleanup() { ControlCenterMaterials.clearForTest(); }

    @Test public void roundedBorderStaysHollowWhenRendererPromotesStrokePathsToFills() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View panel = new View(activity);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff3c4149);
        background.setCornerRadius(28f);
        panel.setBackground(background);
        panel.layout(0, 0, 180, 100);
        ControlCenterGlobalBorder.apply(panel, true, 2, false);
        Bitmap bitmap = Bitmap.createBitmap(180, 100, Bitmap.Config.ARGB_8888);
        panel.draw(new StrokePromotionCanvas(bitmap));
        assertEquals("A border must never paint a white mask over the panel center",
                0xff3c4149, bitmap.getPixel(90, 50));
        assertNotEquals("The border itself must remain visible", 0xff3c4149, bitmap.getPixel(1, 50));
        bitmap.recycle();
    }

    private static final class StrokePromotionCanvas extends Canvas {
        StrokePromotionCanvas(Bitmap bitmap) { super(bitmap); }
        @Override public void drawPath(Path path, Paint paint) {
            // Model the observed filled-round-rect result without an OEM renderer.
            Paint promoted = new Paint(paint);
            promoted.setStyle(Paint.Style.FILL);
            super.drawPath(path, promoted);
        }
    }

    @Test public void blurOnlyCardGetsAReversibleGlobalBorder() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        QSCardItemView card = new QSCardItemView(activity);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 12f);
            }
        });
        card.layout(0, 0, 100, 60);
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.LIGHT).withBlur(100, 0, 0)
                .withGlobalBorder(true, 2);
        ControlCenterMaterials.registerRoot(card, settings);
        assertNull(card.getBackground());
        assertTrue(Color.alpha(draw(card).getPixel(1, 30)) > 0);
        ControlCenterMaterials.refresh(settings.withGlobalBorder(false, 2));
        assertEquals(Color.TRANSPARENT, draw(card).getPixel(1, 30));
    }

    public static final class QSCardItemView extends FrameLayout {
        QSCardItemView(Activity activity) { super(activity); }
    }

    @Test public void cardBorderDoesNotOutlineItsNestedIcon() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        QSCardItemView card = new QSCardItemView(activity);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff353840);
        background.setCornerRadius(24);
        card.setBackground(background);
        QSTileItemIconView icon = new QSTileItemIconView(activity);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(0xff353840);
        icon.setBackground(iconBackground);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(54, 54);
        iconParams.leftMargin = 24; iconParams.topMargin = 23;
        card.addView(icon, iconParams);
        layout(card, 180, 100);
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withGlobalBorder(true, 2);
        ControlCenterMaterials.registerRoot(card, settings);
        Bitmap image = draw(card);
        assertTrue("The card outline remains visible", Color.red(image.getPixel(1, 50)) > 53);
        assertEquals("Nested icon edges must keep their native color",
                0xff353840, image.getPixel(25, 50));
        image.recycle();
    }

    public static final class QSTileItemIconView extends FrameLayout {
        QSTileItemIconView(Activity activity) { super(activity); }
    }

    @Test public void compactTileKeepsOnlyItsCircularOuterBorder() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        QSTileItemView tile = new QSTileItemView(activity);
        tile.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        QSTileItemIconView icon = new QSTileItemIconView(activity);
        icon.setBackgroundColor(0xff353840);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(40, 40, Gravity.CENTER);
        tile.addView(icon, params);
        layout(tile, 100, 100);
        ControlCenterMaterials.registerRoot(tile, ControlCenterConfig.defaults().withEnabled(true)
                .withGlobalBorder(true, 2));
        Bitmap image = draw(tile);
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 1));
        assertTrue("The outer circular rim is visible", Color.alpha(image.getPixel(1, 50)) > 0);
        assertEquals("The nested icon has no square border", 0xff353840, image.getPixel(31, 50));
        image.recycle();
    }

    public static final class QSTileItemView extends FrameLayout {
        QSTileItemView(Activity activity) { super(activity); }
    }

    @Test public void nativeTileHolderWithoutShapeDoesNotGetASquareBorder() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        QSTileItemView tile = new QSTileItemView(activity);
        layout(tile, 100, 100);
        ControlCenterMaterials.registerRoot(tile, ControlCenterConfig.defaults().withEnabled(true)
                .withGlobalBorder(true, 2));
        Bitmap image = draw(tile);
        assertEquals("A layout holder has no surface to outline", Color.TRANSPARENT,
                image.getPixel(1, 1));
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 50));
        image.recycle();
    }

    @Test public void unsupportedNativeDrawableDoesNotFallBackToItsRectangularBounds() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View surface = new View(activity);
        surface.setBackground(new UnknownRoundDrawable());
        surface.layout(0, 0, 100, 60);
        ControlCenterGlobalBorder.apply(surface, true, 2, false);
        Bitmap image = draw(surface);
        assertEquals("Unknown OEM shapes must not gain a rectangle", Color.TRANSPARENT,
                image.getPixel(1, 1));
        image.recycle();
    }

    @Test public void insetOvalUsesTheWrappedSurfaceGeometry() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View surface = new View(activity);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(0xff353840);
        surface.setBackground(new InsetDrawable(oval, 10));
        surface.layout(0, 0, 120, 80);
        ControlCenterGlobalBorder.apply(surface, true, 2, false);
        Bitmap image = draw(surface);
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 1));
        assertEquals(Color.TRANSPARENT, image.getPixel(11, 11));
        assertNotEquals("The border follows the oval inside its wrapper", 0xff353840,
                image.getPixel(11, 40));
        image.recycle();
    }

    @Test public void nativePathOutlineDoesNotTurnIntoARectangle() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View surface = new View(activity);
        surface.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                Path shape = new Path();
                shape.addRoundRect(new RectF(0, 0, view.getWidth(), view.getHeight()),
                        new float[] {24, 24, 24, 24, 0, 0, 0, 0}, Path.Direction.CW);
                outline.setPath(shape);
            }
        });
        surface.layout(0, 0, 100, 60);
        ControlCenterGlobalBorder.apply(surface, true, 2, false);
        Bitmap image = draw(surface);
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 1));
        assertTrue("The confirmed native path retains a visible rim",
                Color.alpha(image.getPixel(1, 40)) > 0);
        image.recycle();
    }

    @Test public void smoothRoundNativePathKeepsItsRealCorners() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View surface = new View(activity);
        surface.setBackground(new SmoothRoundDrawable());
        surface.layout(0, 0, 100, 60);
        ControlCenterGlobalBorder.apply(surface, true, 2, false);
        Bitmap image = draw(surface);
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 1));
        assertNotEquals("The OEM path must still receive a border", 0xff353840,
                image.getPixel(1, 40));
        assertEquals(0xff353840, image.getPixel(50, 30));
        image.recycle();
    }

    @Test public void throwingNativeOutlineDoesNotBreakDrawing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View surface = new View(activity);
        surface.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                throw new IllegalStateException("The OEM surface is between layouts");
            }
        });
        surface.layout(0, 0, 100, 60);
        ControlCenterGlobalBorder.apply(surface, true, 2, false);
        Bitmap image = draw(surface);
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 1));
        image.recycle();
    }

    private static final class SmoothRoundDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        SmoothRoundDrawable() { paint.setColor(0xff353840); }
        @Override protected void onBoundsChange(Rect bounds) {
            path.reset();
            path.addRoundRect(new RectF(bounds),
                    new float[] {24, 24, 24, 24, 0, 0, 0, 0}, Path.Direction.CW);
        }
        @Override public void draw(Canvas canvas) { canvas.drawPath(path, paint); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class UnknownRoundDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        UnknownRoundDrawable() { paint.setColor(0xff353840); }
        @Override public void draw(Canvas canvas) {
            canvas.drawRoundRect(new RectF(getBounds()), 24, 24, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    @Test public void borderSurvivesLayoutAndRestoresWithoutChangingTheNativeSurface() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        FrameLayout card = new FrameLayout(activity);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(12);
        ColorDrawable foreground = new ColorDrawable(Color.TRANSPARENT);
        card.setBackground(background);
        card.setForeground(foreground);
        View child = new View(activity);
        child.setBackgroundColor(Color.BLUE);
        card.addView(child, new FrameLayout.LayoutParams(20, 20, Gravity.CENTER));
        int[] clicks = {0};
        card.setOnClickListener(view -> clicks[0]++);
        root.addView(card, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root);

        ControlCenterGlobalBorder.apply(card, true, 2, true);
        layout(root, 100, 60);
        Bitmap first = draw(card);
        assertTrue(Color.red(first.getPixel(1, 30)) < 245);
        assertEquals(Color.BLUE, first.getPixel(50, 30));
        int borderPixel = first.getPixel(1, 30);
        ControlCenterGlobalBorder.apply(card, true, 2, true);
        assertEquals(borderPixel, draw(card).getPixel(1, 30));

        layout(root, 160, 60);
        Bitmap resized = draw(card);
        assertTrue(Color.red(resized.getPixel(158, 30)) < 245);
        assertEquals(Color.BLUE, resized.getPixel(80, 30));
        assertSame(background, card.getBackground());
        assertSame(foreground, card.getForeground());
        assertEquals(1, card.getChildCount());
        card.performClick();
        assertEquals(1, clicks[0]);

        ControlCenterGlobalBorder.apply(card, false, 2, true);
        Bitmap restored = draw(card);
        assertEquals(Color.WHITE, restored.getPixel(1, 30));
        assertEquals(Color.BLUE, restored.getPixel(80, 30));
        assertSame(background, card.getBackground());
        assertSame(foreground, card.getForeground());
    }

    @Test public void circularOutlineGetsAConformingBorder() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View circle = new View(activity);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.WHITE);
        circle.setBackground(background);
        circle.layout(0, 0, 80, 80);
        ControlCenterGlobalBorder.apply(circle, true, 3, true);
        Bitmap image = draw(circle);
        assertEquals(Color.TRANSPARENT, image.getPixel(1, 1));
        assertTrue(Color.red(image.getPixel(2, 40)) < 245);
        assertEquals(Color.WHITE, image.getPixel(40, 40));
        ControlCenterGlobalBorder.apply(circle, false, 3, true);
    }

    @Test public void gridCarrierRemovesOldBorderAndCannotRestoreItOnRefresh() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        QSCardItemView carrier = new QSCardItemView(activity);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff353840);
        background.setCornerRadius(12);
        carrier.setBackground(background);
        layout(carrier, 180, 100);
        var settings = ControlCenterConfig.defaults().withEnabled(true).withGlobalBorder(true, 2);
        ControlCenterMaterials.registerRoot(carrier, settings);
        assertNotEquals(0xff353840, draw(carrier).getPixel(1, 50));
        ControlCenterMaterials.setGridCarrier(carrier, true);
        ControlCenterMaterials.refresh(settings);
        assertEquals(0xff353840, draw(carrier).getPixel(1, 50));
        ControlCenterMaterials.setGridCarrier(carrier, false);
        ControlCenterMaterials.registerRoot(carrier, settings);
        assertNotEquals(0xff353840, draw(carrier).getPixel(1, 50));
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
}

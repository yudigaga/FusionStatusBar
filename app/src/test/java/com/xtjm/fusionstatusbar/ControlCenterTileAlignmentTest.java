package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterTileAlignmentTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void borderFollowsInsetNativeSurfaceInBothTileStates() {
        for (int state : new int[] {1, 2}) {
            Fixture fixture = new Fixture(state);
            Bitmap image = fixture.draw();
            RectF expected = fixture.surfaceBounds();
            View border = fixture.border();
            assertEquals(expected.left, border.getLeft() + border.getTranslationX(), 0.01f);
            assertEquals("The border follows the OEM icon-frame top margin",
                    expected.top, border.getTop() + border.getTranslationY(), 0.01f);
            assertEquals(expected.width(), border.getWidth() * border.getScaleX(), 0.01f);
            assertEquals(expected.height(), border.getHeight() * border.getScaleY(), 0.01f);
            assertEquals("No detached ring remains at the top of the grid cell", 0,
                    Color.alpha(image.getPixel(fixture.tile.getLeft() + fixture.tile.getWidth() / 2, 1)));
            assertEquals("The touch target retains its full grid bounds", 86, fixture.tile.getHeight());
            image.recycle();
        }
    }

    @Test public void fiveGCircleUsesTheSameInsetSurfaceSizeAsOrdinaryTiles() {
        Fixture fixture = new Fixture(2);
        ControlCenter5GTileView fiveG = new ControlCenter5GTileView(fixture.activity);
        fixture.grid.addTile(fiveG, ControlCenterLayoutPlan.Item.component(
                ControlCenterComponentSpec.FIVE_G, 1, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), -1);
        fixture.grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
        fixture.grid.layout(0, 0, fixture.grid.getMeasuredWidth(), fixture.grid.getMeasuredHeight());
        fixture.draw().recycle();
        assertEquals("5G must match the visible native circle rather than fill the grid cell",
                fixture.icon.getWidth(), fiveG.getWidth());
        assertEquals(fixture.icon.getHeight(), fiveG.getHeight());
        assertEquals("Both circular surfaces use the native vertical inset",
                fixture.surfaceBounds().top, fiveG.getTop(), 0.01f);
    }

    @Test public void nativeSurfaceBottomIsNotCutByTheOuterSlotCircle() {
        Fixture fixture = new Fixture(2);
        fixture.frame.setTranslationY(4f);
        Bitmap image = fixture.draw();
        RectF bounds = fixture.surfaceBounds();
        assertTrue("The native circle remains visible below the outer holder's round clip",
                Color.alpha(image.getPixel((int) bounds.centerX(), (int) bounds.bottom - 2)) > 0);
        image.recycle();
    }

    @Test public void borderTracksNativeTranslationAndScaleWithoutAnotherGridLayout() {
        Fixture fixture = new Fixture(2);
        fixture.draw().recycle();
        fixture.frame.setTranslationY(3.5f);
        fixture.frame.setScaleX(0.9f);
        fixture.frame.setScaleY(0.9f);
        fixture.draw().recycle();
        RectF bounds = fixture.surfaceBounds();
        View border = fixture.border();
        assertEquals(bounds.left, border.getLeft() + border.getTranslationX(), 0.01f);
        assertEquals(bounds.top, border.getTop() + border.getTranslationY(), 0.01f);
        assertEquals(bounds.width(), border.getWidth() * border.getScaleX(), 0.01f);
        assertEquals(bounds.height(), border.getHeight() * border.getScaleY(), 0.01f);
    }

    @Test public void panelAnimationDoesNotApplyItsTransformTwiceToTheBorder() {
        Fixture fixture = new Fixture(2);
        fixture.grid.setTranslationX(23f);
        fixture.grid.setTranslationY(41f);
        fixture.grid.setScaleX(0.8f);
        fixture.grid.setScaleY(0.8f);
        fixture.frame.setTranslationY(2f);
        fixture.draw().recycle();
        RectF bounds = fixture.surfaceBounds();
        View border = fixture.border();
        assertEquals(bounds.left, border.getLeft() + border.getTranslationX(), 0.01f);
        assertEquals(bounds.top, border.getTop() + border.getTranslationY(), 0.01f);
        assertEquals(bounds.width(), border.getWidth() * border.getScaleX(), 0.01f);
    }

    @Test public void transparentDecorationMustNotClaimAnOpaqueHardwareOutline() {
        Fixture fixture = new Fixture(2);
        fixture.draw().recycle();
        View border = fixture.border();
        Outline outline = new Outline();
        // View.rebuildOutline seeds alpha=1 before calling the provider.
        outline.setAlpha(1f);
        border.getOutlineProvider().getOutline(border, outline);
        assertFalse("Keep the circular decoration geometry", outline.isEmpty());
        assertEquals("The transparent decoration must not occlude the glyph below it",
                0f, outline.getAlpha(), 0f);
    }

    @Test public void decorationDrawsAboveElevatedTileWithoutCreatingAShadowPlane() {
        Fixture fixture = new Fixture(2);
        fixture.tile.setElevation(20f);
        fixture.draw().recycle();
        assertEquals("Decoration ordering must not enable a second shadow surface",
                0f, fixture.border().getZ(), 0f);
        fixture.tile.setElevation(40f);
        Bitmap image = fixture.draw();
        assertEquals(0f, fixture.border().getZ(), 0f);
        RectF surface = fixture.surfaceBounds();
        assertTrue("The rim still draws over the native tile", Color.blue(image.getPixel(
                (int) surface.left + 1, (int) surface.centerY())) > 69);
        assertEquals("The glyph remains fully visible", Color.BLUE, image.getPixel(
                (int) surface.centerX(), (int) surface.centerY()));
        image.recycle();
    }

    private static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                activity, 4, 8, 12, 0, 2);
        final QSTileItemView tile = new QSTileItemView(activity);
        final FrameLayout frame = new FrameLayout(activity);
        final ImageView icon = new ImageView(activity);

        Fixture(int state) {
            tile.state.state = state;
            tile.setClipChildren(false);
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(0xff3a3f45);
            background.setSize(74, 74);
            LayerDrawable layers = new LayerDrawable(new Drawable[] {background, new ColorDrawable(Color.BLUE)});
            layers.setLayerSize(1, 24, 24);
            layers.setLayerGravity(1, Gravity.CENTER);
            icon.setImageDrawable(layers);
            icon.setScaleType(ImageView.ScaleType.FIT_XY);
            frame.addView(icon, new FrameLayout.LayoutParams(74, 74));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(74, 74, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            params.topMargin = 12;
            tile.addView(frame, params);
            grid.addTile(tile, ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1)
                    .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), 0);
            grid.applyMaterials(ControlCenterConfig.defaults().withEnabled(true).withGlobalBorder(true, 2));
            grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST));
            grid.layout(0, 0, grid.getMeasuredWidth(), grid.getMeasuredHeight());
        }

        View border() { return grid.getChildAt(grid.getChildCount() - 1); }

        RectF surfaceBounds() {
            float x = tile.getLeft() + frame.getLeft() + frame.getTranslationX()
                    + frame.getPivotX() * (1f - frame.getScaleX());
            float y = tile.getTop() + frame.getTop() + frame.getTranslationY()
                    + frame.getPivotY() * (1f - frame.getScaleY());
            return new RectF(x, y, x + 74 * frame.getScaleX(), y + 74 * frame.getScaleY());
        }

        Bitmap draw() {
            Bitmap image = Bitmap.createBitmap(grid.getWidth(), grid.getHeight(), Bitmap.Config.ARGB_8888);
            grid.draw(new Canvas(image));
            return image;
        }
    }

    static final class QSTileItemView extends FrameLayout {
        final TileState state = new TileState();
        QSTileItemView(Activity activity) { super(activity); }
    }
    static final class TileState { int state; }
}

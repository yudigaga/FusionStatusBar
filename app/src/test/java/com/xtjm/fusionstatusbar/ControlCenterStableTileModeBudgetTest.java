package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
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
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ControlCenterStableTileModeBudgetTest {
    @After public void clearMaterials() { ControlCenterMaterials.clearForTest(); }

    @Test public void stableCircleTilesReadTheirSharedNativeModeAtMostOncePerGridDraw() {
        Context context = RuntimeEnvironment.getApplication();
        ModeProvider provider = new ModeProvider();
        ControlCenterRuntimeGrid.GridView grid = grid(context);
        for (int index = 0; index < 2; index++) {
            QSTileItemView tile = new QSTileItemView(context, provider);
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(new Glyph());
            tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
            grid.addTile(tile, item("native." + index, index), -1);
        }
        try {
            grid.applyMaterials(settings(40));
            layout(grid);
            for (int warmup = 0; warmup < 3; warmup++) draw(grid).recycle();
            provider.calls = 0;
            provider.controller.calls = 0;
            for (int frame = 0; frame < 32; frame++) draw(grid).recycle();
            assertTrue("Two stable tiles sharing a native controller allow at most one live mode read per grid draw; actual="
                    + provider.controller.calls, provider.controller.calls <= 32);
            assertTrue("Resolving the shared mode provider must not scale with tile count per frame",
                    provider.calls <= 32);
            System.out.println("[mode-budget] frames=32 tiles=2 providerGets=" + provider.calls
                    + " modeReads=" + provider.controller.calls);
        } finally { grid.releaseMaterials(); }
    }

    @Test public void modeChangesBetweenDrawsReleaseAndRestoreFallbackWithoutAConfigRefresh() {
        Context context = RuntimeEnvironment.getApplication();
        ModeProvider provider = new ModeProvider();
        CustomTileView tile = new CustomTileView(context, provider);
        Drawable original = tile.getBackground();
        ControlCenterRuntimeGrid.GridView grid = grid(context);
        grid.addTile(tile, item("fallback", 0), 0);
        try {
            grid.applyMaterials(settings(40));
            layout(grid);
            assertPixels(grid, tile, 102);
            draw(grid).recycle();

            provider.controller.mode = "EDIT";
            assertPixels(grid, tile, 0);
            assertSame("Entering native edit mode restores the OEM background immediately", original,
                    tile.getBackground());

            provider.controller.mode = "NORMAL";
            assertPixels(grid, tile, 102);
            assertNotSame("Leaving edit mode restores the active fallback without a settings notification",
                    original, tile.getBackground());

            ControlCenterConfig transparent = settings(0);
            ControlCenterMaterials.refresh(transparent);
            grid.updateDecorations(transparent);
            assertPixels(grid, tile, 0);

            ControlCenterConfig disabled = settings(100).withEnabled(false);
            ControlCenterMaterials.refresh(disabled);
            grid.updateDecorations(disabled);
            assertPixels(grid, tile, 0);
            assertSame("The mode budget optimization must preserve disabled-background restoration",
                    original, tile.getBackground());
            assertEquals("Only the background is faded", 1f, tile.getAlpha(), 0f);
        } finally { grid.releaseMaterials(); }
    }

    @Test public void reenablingCustomizationRestoresAnUnchangedActiveFallbackAfterDisabledCleanup() {
        Context context = RuntimeEnvironment.getApplication();
        ModeProvider provider = new ModeProvider();
        CustomTileView tile = new CustomTileView(context, provider);
        Drawable original = tile.getBackground();
        ControlCenterRuntimeGrid.GridView grid = grid(context);
        grid.addTile(tile, item("fallback.reenable", 0), 0);
        try {
            ControlCenterConfig enabled = settings(40);
            grid.applyMaterials(enabled);
            layout(grid);
            assertPixels(grid, tile, 102);
            draw(grid).recycle();

            ControlCenterConfig disabled = enabled.withEnabled(false);
            ControlCenterMaterials.refresh(disabled);
            grid.updateDecorations(disabled);
            assertPixels(grid, tile, 0);
            assertSame(original, tile.getBackground());

            ControlCenterMaterials.refresh(enabled);
            grid.updateDecorations(enabled);
            assertPixels(grid, tile, 102);
            assertNotSame("Re-enabling must recreate the fallback even when active state never changed",
                    original, tile.getBackground());
            draw(grid).recycle();
            assertPixels(grid, tile, 102);
        } finally { grid.releaseMaterials(); }
    }

    private static ControlCenterConfig settings(int strength) {
        return ControlCenterConfig.defaults().withEnabled(true).withSurface(ControlCenterSurface.LIGHT)
                .withBlur(0, strength, strength);
    }

    private static ControlCenterRuntimeGrid.GridView grid(Context context) {
        return new ControlCenterRuntimeGrid.GridView(context, 4, 8, 12, 0, 1);
    }

    private static ControlCenterLayoutPlan.Item item(String spec, int column) {
        return ControlCenterLayoutPlan.Item.tile(spec, column, 0, 1, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0);
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private static Bitmap draw(View view) {
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(image));
        return image;
    }

    private static void assertPixels(ControlCenterRuntimeGrid.GridView grid, View tile, int alpha) {
        Bitmap image = draw(grid);
        try {
            int center = tile.getLeft() + tile.getWidth() / 2;
            assertEquals("The fallback body preserves the configured opacity across native mode changes", alpha,
                    Color.alpha(image.getPixel(center, tile.getTop() + tile.getHeight() / 6)));
            assertEquals("Native mode and material changes must retain the glyph", Color.BLUE,
                    image.getPixel(center, tile.getTop() + tile.getHeight() / 2));
        } finally { image.recycle(); }
    }

    public static final class ModeController {
        public String mode = "NORMAL";
        int calls;
        public String getMode() { calls++; return mode; }
    }

    public static final class ModeProvider {
        final ModeController controller = new ModeController();
        int calls;
        public ModeController get() { calls++; return controller; }
    }

    public static final class QSTileItemView extends FrameLayout {
        public int state = 2;
        public final ModeProvider mainPanelController;
        QSTileItemView(Context context, ModeProvider provider) {
            super(context);
            mainPanelController = provider;
        }
    }

    public static final class CustomTileView extends FrameLayout {
        public int state = 2;
        public final ModeProvider mainPanelController;
        CustomTileView(Context context, ModeProvider provider) {
            super(context);
            mainPanelController = provider;
            setBackgroundColor(Color.TRANSPARENT);
            addView(new View(context) {
                private final Paint glyph = new Paint();
                @Override protected void onDraw(Canvas canvas) {
                    glyph.setColor(Color.BLUE);
                    canvas.drawRect(getWidth() * .4f, getHeight() * .4f,
                            getWidth() * .6f, getHeight() * .6f, glyph);
                }
            }, new FrameLayout.LayoutParams(-1, -1));
        }
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

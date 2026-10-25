package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;
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
public class ControlCenterZeroOpacityRegressionTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void zeroCardStrengthClearsTheRuntimeMediaSessionButKeepsItsControls() {
        Context context = RuntimeEnvironment.getApplication();
        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(context, 4, 8, 12, 0, 2);
        MediaPlayerPanel media = new MediaPlayerPanel(context);
        GradientDrawable original = new GradientDrawable();
        original.setColor(Color.GRAY);
        original.setCornerRadius(12);
        media.setBackground(original);
        View control = new View(context);
        control.setBackgroundColor(Color.BLUE);
        media.addView(control, new FrameLayout.LayoutParams(20, 20));
        grid.addTile(media, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA,
                0, 0, 2, 2), -1);
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.SOFT_GLASS);
        try {
            grid.applyMaterials(settings);
            render(grid, 400, 240).recycle();
            assertTrue("The fixture must reach the runtime component background session",
                    media.getBackground() instanceof ControlCenterMaterials.TileSurfaceDrawable);
            Bitmap visible = render(media, 160, 100);
            try { assertTrue(Color.alpha(visible.getPixel(80, 50)) > 0); }
            finally { visible.recycle(); }

            grid.applyMaterials(settings.withBlur(0, 0, 0));
            render(grid, 400, 240).recycle();
            Bitmap transparent = render(media, 160, 100);
            try {
                assertEquals("Transparent card setting must clear the media background", 0,
                        Color.alpha(transparent.getPixel(80, 50)));
                assertEquals("Foreground media controls must survive", Color.BLUE,
                        transparent.getPixel(10, 10));
            } finally { transparent.recycle(); }
        } finally { grid.releaseMaterials(); }
    }

    @Test public void zeroPreviewStrengthClearsGlassFillButKeepsTheSliderProgress() {
        ControlCenterCardView slider = new ControlCenterCardView(RuntimeEnvironment.getApplication());
        slider.bind(new ControlCenterCardView.Model(ControlCenterComponentSpec.BRIGHTNESS, "", "亮度", null,
                false, false, false, false, ControlCenterLayoutPlan.Shape.RECTANGLE, 16, 100));
        slider.setSurface(ControlCenterSurface.SOFT_GLASS);
        slider.setMaterialStrength(0, 0);
        Bitmap image = render(slider, 100, 200);
        try {
            assertEquals("Transparent preview setting must clear the slider track", 0,
                    Color.alpha(image.getPixel(50, 20)));
            assertTrue("Slider foreground progress must stay visible",
                    Color.alpha(image.getPixel(50, 180)) > 200);
        } finally { image.recycle(); }
    }

    @Test public void everyMaterialClearsRuntimeComponentsAndRestoresTheirFullStrengthAppearance() {
        Context context = RuntimeEnvironment.getApplication();
        for (ControlCenterSurface style : ControlCenterSurface.values()) {
            for (String spec : List.of(ControlCenterComponentSpec.MEDIA,
                    ControlCenterComponentSpec.DEVICE_CENTER, ControlCenterComponentSpec.BRIGHTNESS,
                    ControlCenterComponentSpec.VOLUME)) {
                FrameLayout panel = spec.equals(ControlCenterComponentSpec.MEDIA)
                        ? new MediaPlayerPanel(context)
                        : spec.equals(ControlCenterComponentSpec.DEVICE_CENTER)
                        ? new DeviceCenterEntryFrameLayout(context) : new ToggleSliderView(context);
                panel.setBackground(background());
                View glyph = new View(context);
                glyph.setBackgroundColor(Color.BLUE);
                panel.addView(glyph, new FrameLayout.LayoutParams(20, 20));
                boolean slider = panel instanceof ToggleSliderView;
                if (slider) {
                    View progress = new View(context);
                    progress.setId(android.R.id.progress);
                    progress.setBackgroundColor(Color.WHITE);
                    panel.addView(progress, new FrameLayout.LayoutParams(-1, 30, Gravity.BOTTOM));
                }
                ControlCenterRuntimeGrid.GridView grid = grid(context);
                grid.addTile(panel, ControlCenterLayoutPlan.Item.component(spec, 0, 0, 2, 2), -1);
                ControlCenterConfig full = settings(style, 100, 100);
                try {
                    apply(grid, full);
                    int baseline = pixel(panel, 80, 50);
                    apply(grid, settings(style, 0, 0));
                    assertEquals(style + " " + spec + " background must be transparent at zero", 0,
                            Color.alpha(pixel(panel, 80, 50)));
                    assertEquals(style + " " + spec + " foreground control remains visible", Color.BLUE,
                            pixel(panel, 10, 10));
                    if (slider) assertEquals(style + " slider progress remains visible", Color.WHITE,
                            pixel(panel, 80, 90));
                    apply(grid, full);
                    assertEquals(style + " " + spec + " full strength appearance must restore", baseline,
                            pixel(panel, 80, 50));
                } finally { grid.releaseMaterials(); }
            }
        }
    }

    @Test public void everyMaterialClearsGroupContainersAndNativeTileBackings() {
        Context context = RuntimeEnvironment.getApplication();
        for (ControlCenterSurface style : ControlCenterSurface.values()) {
            ControlCenterRuntimeGrid.GridView groupGrid = grid(context);
            ControlCenterLayoutPlan.Item groupItem = ControlCenterLayoutPlan.Item.group("group:tools",
                    ControlCenterGroupData.empty(), 0, 0, 2, 2);
            ControlCenterGroupView group = new ControlCenterGroupView(context, groupItem, member -> new View(context));
            groupGrid.addTile(group, groupItem, -1);
            try {
                apply(groupGrid, settings(style, 100, 100));
                int baseline = pixel(group, 80, 50);
                apply(groupGrid, settings(style, 0, 100));
                assertEquals(style + " group must follow card transparency", 0,
                        Color.alpha(pixel(group, 80, 50)));
                apply(groupGrid, settings(style, 100, 100));
                assertEquals(style + " group restores full strength", baseline, pixel(group, 80, 50));
            } finally { groupGrid.releaseMaterials(); }

            for (boolean active : new boolean[] {false, true}) {
                QSTileItemView tile = new QSTileItemView(context);
                tile.state = active ? 2 : 1;
                tile.setBackground(background());
                ImageView glyph = new ImageView(context);
                glyph.setImageDrawable(new ColorDrawable(Color.BLUE));
                tile.addView(glyph, new FrameLayout.LayoutParams(20, 20, Gravity.CENTER));
                ControlCenterRuntimeGrid.GridView tileGrid = grid(context);
                tileGrid.addTile(tile, ControlCenterLayoutPlan.Item.tile("rotation", 0, 0, 1, 1)
                        .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), -1);
                try {
                    apply(tileGrid, settings(style, 100, 100));
                    int baseline = pixel(tile, 80, 10);
                    apply(tileGrid, settings(style, 100, 0));
                    assertEquals(style + " native tile active=" + active + " must follow tile transparency", 0,
                            Color.alpha(pixel(tile, 80, 10)));
                    assertEquals("Tile glyph itself must not fade", 255, glyph.getDrawable().getAlpha());
                    assertEquals(1f, glyph.getAlpha(), 0f);
                    apply(tileGrid, settings(style, 100, 100));
                    assertEquals(style + " native tile restores full strength", baseline, pixel(tile, 80, 10));
                } finally { tileGrid.releaseMaterials(); }
            }
        }
    }

    @Test public void everyMaterialClearsActiveAndInactiveModule5gTilesWithoutHidingTheirLabels() {
        Context context = RuntimeEnvironment.getApplication();
        for (ControlCenterSurface style : ControlCenterSurface.values()) {
            for (boolean active : new boolean[] {false, true}) {
                FakeRadio radio = new FakeRadio(active);
                ControlCenter5GTileView tile = new ControlCenter5GTileView(context,
                        new ControlCenter5GController(radio));
                tile.onAttachedToWindow();
                assertEquals(active, tile.networkEnabled());
                ControlCenterRuntimeGrid.GridView grid = grid(context);
                grid.addTile(tile, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.FIVE_G,
                        0, 0, 1, 1).withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0), -1);
                try {
                    apply(grid, settings(style, 100, 100));
                    int baseline = pixel(tile, 80, 10);
                    apply(grid, settings(style, 100, 0));
                    assertEquals(style + " 5G active=" + active + " background must become transparent", 0,
                            Color.alpha(pixel(tile, 80, 10)));
                    TextView label = (TextView) tile.getChildAt(0);
                    assertEquals("5G", label.getText().toString());
                    assertEquals(255, Color.alpha(label.getCurrentTextColor()));
                    assertEquals(1f, label.getAlpha(), 0f);
                    assertEquals(active, tile.networkEnabled());
                    apply(grid, settings(style, 100, 100));
                    assertEquals(style + " 5G restores full strength", baseline, pixel(tile, 80, 10));
                } finally {
                    grid.releaseMaterials();
                    tile.release();
                }
            }
        }
    }

    private static ControlCenterConfig settings(ControlCenterSurface style, int card, int tile) {
        return ControlCenterConfig.defaults().withEnabled(true).withSurface(style).withBlur(100, card, tile);
    }

    private static ControlCenterRuntimeGrid.GridView grid(Context context) {
        return new ControlCenterRuntimeGrid.GridView(context, 4, 8, 12, 0, 2);
    }

    private static void apply(ControlCenterRuntimeGrid.GridView grid, ControlCenterConfig settings) {
        grid.applyMaterials(settings);
        render(grid, 400, 240).recycle();
    }

    private static GradientDrawable background() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff78818c);
        background.setCornerRadius(12);
        return background;
    }

    private static int pixel(View view, int x, int y) {
        Bitmap image = render(view, 160, 100);
        try { return image.getPixel(x, y); }
        finally { image.recycle(); }
    }

    private static Bitmap render(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    public static final class MediaPlayerPanel extends FrameLayout {
        MediaPlayerPanel(Context context) { super(context); }
    }
    public static final class DeviceCenterEntryFrameLayout extends FrameLayout {
        DeviceCenterEntryFrameLayout(Context context) { super(context); }
    }
    public static final class ToggleSliderView extends FrameLayout {
        ToggleSliderView(Context context) { super(context); }
    }
    public static final class QSTileItemView extends FrameLayout {
        int state = 1;
        QSTileItemView(Context context) { super(context); }
    }

    private static final class FakeRadio implements ControlCenter5GController.RadioAccess {
        private boolean enabled;
        FakeRadio(boolean enabled) { this.enabled = enabled; }
        @Override public long supportedNetworkTypes() {
            return TelephonyManager.NETWORK_TYPE_BITMASK_NR | TelephonyManager.NETWORK_TYPE_BITMASK_LTE;
        }
        @Override public long allowedNetworkTypes() {
            return TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                    | (enabled ? TelephonyManager.NETWORK_TYPE_BITMASK_NR : 0L);
        }
        @Override public void setAllowedNetworkTypes(long value) {
            enabled = (value & TelephonyManager.NETWORK_TYPE_BITMASK_NR) != 0;
        }
    }
}

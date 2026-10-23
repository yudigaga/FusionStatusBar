package com.xtjm.fusionstatusbar;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
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
public class ControlCenterGridMaterialDirtyTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void settledFramesDoNotScanTheComponentSubtreeAgain() {
        var fixture = new Fixture();
        fixture.draw();
        fixture.nested.backgroundReads = 0;
        for (int i = 0; i < 8; i++) fixture.draw();
        assertEquals("A stable component must not traverse its background subtree per frame",
                0, fixture.nested.backgroundReads);
    }

    @Test public void registeredSubtreeScanReadsTheCountedBackground() {
        var fixture = new Fixture();
        fixture.draw();
        fixture.nested.backgroundReads = 0;
        ControlCenterMaterials.registerRoot(fixture.panel, fixture.settings);
        assertTrue("The fixture must observe real material scans of the nested background",
                fixture.nested.backgroundReads > 0);
    }

    @Test public void blurScaleAnimationDoesNotRescanSettledComponentSubtree() {
        var fixture = new Fixture();
        ControlCenterMaterials.nativeArguments(fixture.panel,
                "setMiBackgroundBlurScaleRatio", new Object[] {0f}, fixture.settings, false);
        fixture.draw();
        fixture.nested.backgroundReads = 0;
        for (int frame = 0; frame < 8; frame++) {
            ControlCenterMaterials.nativeArguments(fixture.panel,
                    "setMiBackgroundBlurScaleRatio", new Object[] {(frame + 1) / 8f},
                    fixture.settings, false);
            fixture.draw();
        }
        assertEquals("Blur scale animation alone must not rescan component backgrounds each frame",
                0, fixture.nested.backgroundReads);
    }

    @Test public void blurRadiusAnimationDoesNotRescanSettledComponentSubtree() {
        var fixture = new Fixture();
        ControlCenterMaterials.nativeArguments(fixture.panel,
                "setMiBackgroundBlurRadius", new Object[] {0}, fixture.settings, false);
        fixture.draw();
        fixture.nested.backgroundReads = 0;
        for (int frame = 0; frame < 8; frame++) {
            ControlCenterMaterials.nativeArguments(fixture.panel,
                    "setMiBackgroundBlurRadius", new Object[] {(frame + 1) * 24},
                    fixture.settings, false);
            fixture.draw();
        }
        assertEquals("Blur radius animation alone must not rescan component backgrounds each frame",
                0, fixture.nested.backgroundReads);
    }

    @Test public void structuralNativeMaterialUpdatesStillRescanComponentSubtree() {
        var fixture = new Fixture();
        ControlCenterMaterials.nativeArguments(fixture.panel,
                "setMiBackgroundBlurScaleRatio", new Object[] {0f}, fixture.settings, false);
        fixture.draw();
        fixture.nested.backgroundReads = 0;

        ControlCenterMaterials.nativeArguments(fixture.panel,
                "setMiViewBlurMode", new Object[] {1}, fixture.settings, false);
        fixture.draw();
        assertTrue("A native blur mode change must rescan the component background subtree",
                fixture.nested.backgroundReads > 0);

        fixture.nested.backgroundReads = 0;
        var colors = new ArrayList<Point>();
        colors.add(new Point(0x804080c0, 3));
        ControlCenterMaterials.nativeArguments(fixture.panel,
                "setMiBackgroundBlendColors", new Object[] {colors}, fixture.settings, false);
        fixture.draw();
        assertTrue("A native blend color change must rescan the component background subtree",
                fixture.nested.backgroundReads > 0);
    }

    private static final class Fixture {
        final ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                RuntimeEnvironment.getApplication(), 4, 8, 12, 0, 2);
        final MediaPlayerPanel panel = new MediaPlayerPanel();
        final CountingView nested = new CountingView();
        final ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.DARK);

        Fixture() {
            panel.setBackground(surface());
            nested.setBackground(surface());
            panel.addView(nested, new FrameLayout.LayoutParams(20, 20));
            grid.addTile(panel, ControlCenterLayoutPlan.Item.component(
                    ControlCenterComponentSpec.MEDIA, 0, 0, 2, 2), -1);
            grid.applyMaterials(settings);
            grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST));
            grid.layout(0, 0, grid.getMeasuredWidth(), grid.getMeasuredHeight());
        }

        void draw() {
            var bitmap = Bitmap.createBitmap(grid.getWidth(), grid.getHeight(), Bitmap.Config.ARGB_8888);
            grid.draw(new Canvas(bitmap));
            bitmap.recycle();
        }
    }

    private static GradientDrawable surface() {
        var background = new GradientDrawable();
        background.setColor(Color.GRAY);
        background.setCornerRadius(12);
        return background;
    }

    public static final class MediaPlayerPanel extends FrameLayout {
        MediaPlayerPanel() { super(RuntimeEnvironment.getApplication()); }
    }

    private static final class CountingView extends View {
        private static final int PROGRESS_BACKGROUND_ID = 0x12347;
        private Resources hostResources;
        int backgroundReads;
        CountingView() {
            super(RuntimeEnvironment.getApplication());
            setId(PROGRESS_BACKGROUND_ID);
            Resources resources = super.getResources();
            hostResources = new Resources(resources.getAssets(), resources.getDisplayMetrics(),
                    resources.getConfiguration()) {
                @Override public String getResourceEntryName(int id) {
                    if (id == PROGRESS_BACKGROUND_ID) return "progress_bg";
                    return super.getResourceEntryName(id);
                }
            };
        }
        @Override public Resources getResources() {
            return hostResources == null ? super.getResources() : hostResources;
        }
        @Override public Drawable getBackground() { backgroundReads++; return super.getBackground(); }
        @Override public void draw(Canvas canvas) { /* Keep framework drawing from counting getBackground reads. */ }
    }
}

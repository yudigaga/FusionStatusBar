package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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

    private static final class Fixture {
        final ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                RuntimeEnvironment.getApplication(), 4, 8, 12, 0, 2);
        final MediaPlayerPanel panel = new MediaPlayerPanel();
        final CountingView nested = new CountingView();

        Fixture() {
            panel.setBackground(surface());
            nested.setBackground(surface());
            panel.addView(nested, new FrameLayout.LayoutParams(20, 20));
            grid.addTile(panel, ControlCenterLayoutPlan.Item.component(
                    ControlCenterComponentSpec.MEDIA, 0, 0, 2, 2), -1);
            grid.applyMaterials(ControlCenterConfig.defaults().withEnabled(true)
                    .withSurface(ControlCenterSurface.DARK));
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
        int backgroundReads;
        CountingView() { super(RuntimeEnvironment.getApplication()); }
        @Override public Drawable getBackground() { backgroundReads++; return super.getBackground(); }
        @Override public void draw(Canvas canvas) { /* Keep framework drawing from counting getBackground reads. */ }
    }
}

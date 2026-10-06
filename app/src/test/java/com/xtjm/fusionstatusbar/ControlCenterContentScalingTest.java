package com.xtjm.fusionstatusbar;

import android.view.View;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
public class ControlCenterContentScalingTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void oneRegionTransformScalesEverySurfaceContentGapAndReservedHeight() {
        var fixture = new Fixture(120);
        assertEquals(1.2f, fixture.grid.getScaleX(), 0f);
        assertEquals(1.2f, fixture.grid.getScaleY(), 0f);
        assertEquals(Math.round(fixture.grid.contentHeight() * 1.2f),
                fixture.viewport.getMeasuredHeight());
        for (int i = 0; i < fixture.grid.getChildCount(); i++) {
            View panel = fixture.grid.getChildAt(i);
            assertEquals("Each component is scaled only by the region", 1f, panel.getScaleX(), 0f);
            if (panel instanceof FrameLayout group && group.getChildCount() > 0)
                assertEquals("Glyphs must not receive a second multiplier", 1f,
                        group.getChildAt(0).getScaleY(), 0f);
        }
        int nativeHeight = fixture.grid.contentHeight();
        fixture.grid.updateDecorations(fixture.settings.withTileScale(80));
        layout(fixture.viewport);
        assertEquals(nativeHeight, fixture.grid.contentHeight());
        assertEquals(0.8f, fixture.grid.getScaleX(), 0f);
        assertEquals(Math.round(nativeHeight * 0.8f), fixture.viewport.getMeasuredHeight());
        assertEquals("A smaller complete region stays centered", 40, fixture.grid.getLeft());
    }

    @Test public void enlargedRegionIsReachableAtBothEdgesAndRoutesPointerEvents() {
        var fixture = new Fixture(130);
        var region = (FrameLayout) fixture.viewport.getChildAt(0);
        assertEquals(520, region.getWidth());
        assertEquals("Start enlarged content at the center", 60, fixture.viewport.getScrollX());
        fixture.viewport.scrollTo(120, 0);
        assertEquals(120, fixture.viewport.getScrollX());
        fixture.viewport.scrollTo(0, 0);
        assertEquals(0, fixture.viewport.getScrollX());
        var tile = fixture.grid.getChildAt(0);
        int[] touched = {0};
        tile.setOnTouchListener((view, event) -> { touched[0]++; return false; });
        tile.setOnClickListener(view -> { });
        assertTrue(tile.isClickable());
        float x = (tile.getLeft() + tile.getWidth() / 2f) * 1.3f;
        float y = (tile.getTop() + tile.getHeight() / 2f) * 1.3f;
        assertTrue("The scaled tile accepts a down event", dispatch(fixture.viewport,
                MotionEvent.ACTION_DOWN, x, y));
        assertTrue("The scaled tile accepts an up event", dispatch(fixture.viewport,
                MotionEvent.ACTION_UP, x, y));
        assertEquals("The transformed hit target receives both pointer events", 2, touched[0]);
    }

    @Test public void editorUsesTheSameRegionScaleWithoutScalingItsGlyphsAgain() throws Exception {
        var editor = new ControlCenterGridEditor(RuntimeEnvironment.getApplication(),
                new ControlCenterGridEditor.Listener() {
                    public void onItemClicked(String id) { }
                    public void onItemMoved(String id, int x, int y) { }
                });
        editor.setCells(4, java.util.List.of(new ControlCenterGridEditor.Cell("5g",
                ControlCenterComponentSpec.FIVE_G, "", "5G", null, 0, 0, 1, 1, false,
                null, null, ControlCenterLayoutPlan.Shape.CIRCLE, 0, false, false, 0, 130)));
        var viewport = new ControlCenterScaleViewport(editor.getContext(), editor, editor::scalePercent);
        layout(viewport);
        assertEquals(1.3f, editor.getScaleX(), 0f);
        var card = (ControlCenterCardView) ((FrameLayout) editor.getChildAt(0)).getChildAt(0);
        var modelField = ControlCenterCardView.class.getDeclaredField("model");
        modelField.setAccessible(true);
        assertEquals("The editor region supplies the only global multiplier", 100,
                ((ControlCenterCardView.Model) modelField.get(card)).scale);
        assertEquals(Math.round(editor.getMeasuredHeight() * 1.3f), viewport.getMeasuredHeight());
    }

    private static boolean dispatch(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 10, action, x, y, 0);
        boolean handled = view.dispatchTouchEvent(event);
        event.recycle();
        return handled;
    }

    private static final class Fixture {
        final ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(
                RuntimeEnvironment.getApplication(), 4, 8, 12, 0, 4);
        final ControlCenterConfig settings;
        final ControlCenterScaleViewport viewport;

        Fixture(int percent) {
            String[] specs = {ControlCenterComponentSpec.MEDIA, ControlCenterComponentSpec.BRIGHTNESS,
                    ControlCenterComponentSpec.VOLUME, ControlCenterComponentSpec.DEVICE_CENTER};
            for (int i = 0; i < specs.length; i++) {
                var panel = new FrameLayout(grid.getContext());
                panel.addView(new TextView(grid.getContext()), new FrameLayout.LayoutParams(24, 24));
                grid.addTile(panel, ControlCenterLayoutPlan.Item.component(specs[i], 0, i, 2, 1), -1);
            }
            var fiveG = new ControlCenter5GTileView(grid.getContext());
            grid.addTile(fiveG, ControlCenterLayoutPlan.Item.component(
                    ControlCenterComponentSpec.FIVE_G, 2, 0, 1, 1), -1);
            settings = ControlCenterConfig.defaults().withEnabled(true).withTileScale(percent);
            grid.applyMaterials(settings);
            viewport = new ControlCenterScaleViewport(grid.getContext(), grid, grid::scalePercent);
            layout(viewport);
        }
    }

    private static void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }
}

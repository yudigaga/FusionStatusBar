package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterGridEditorShapeTest {
    private String clicked;
    private int edits;
    private ControlCenterGridEditor editor() {
        ControlCenterGridEditor editor = new ControlCenterGridEditor(RuntimeEnvironment.getApplication(),
                new ControlCenterGridEditor.Listener() {
                    public void onItemClicked(String id) { clicked = id; }
                    public void onItemMoved(String id, int x, int y) { edits++; }
                    public void onItemResized(String id, int width, int height) { edits++; }
                });
        editor.setGeometry(4, 24);
        return editor;
    }
    private ControlCenterGridEditor.Cell cell(String id, String spec,
            ControlCenterLayoutPlan.Shape shape, int radius, int x, int width, int height,
            boolean locked, boolean hidden, int z, String second, boolean horizontal) {
        return new ControlCenterGridEditor.Cell(id, spec, "bt", id, second, x, 0,
                width, height, horizontal, null, null, shape, radius, locked, hidden, z, 100);
    }
    private ControlCenterGridEditor.Cell simple(String id, String spec,
            ControlCenterLayoutPlan.Shape shape, int radius) {
        return cell(id, spec, shape, radius, 0, 1, 1, false, false, 0, null, false);
    }
    private void layout(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }
    private FrameLayout surface(ControlCenterGridEditor editor, int index) {
        ViewGroup host = (ViewGroup) editor.getChildAt(index);
        assertNull("The interaction shell must not paint a surface", host.getBackground());
        assertFalse(host.getClipToOutline());
        assertEquals("No permanent resize arrow", 1, host.getChildCount());
        assertTrue(host.getChildAt(0) instanceof ControlCenterCardView);
        return (FrameLayout) ((ViewGroup) host.getChildAt(0)).getChildAt(0);
    }
    private GradientDrawable material(FrameLayout surface) {
        return (GradientDrawable) surface.getBackground();
    }
    @Test public void rectangleHasOneSurfaceWithIndependentRadiusAndNoFixedTileBox() {
        ControlCenterGridEditor editor = editor();
        editor.setCells(4, Collections.singletonList(simple("wifi", "wifi",
                ControlCenterLayoutPlan.Shape.RECTANGLE, 3)));
        layout(editor);
        FrameLayout surface = surface(editor, 0);
        assertEquals(GradientDrawable.RECTANGLE, material(surface).getShape());
        assertEquals(3f, material(surface).getCornerRadius(), 0f);
        assertTrue(surface.getClipToOutline());
        View content = surface.getChildAt(0);
        assertNull(content.getBackground());
        assertEquals(surface.getMeasuredWidth(), content.getMeasuredWidth());
        assertEquals(surface.getMeasuredHeight(), content.getMeasuredHeight());
        assertEquals(0, edits);
    }
    @Test public void circleInWideSlotIsAnInscribedCircleNotAnEllipse() {
        ControlCenterGridEditor editor = editor();
        editor.setCells(4, Collections.singletonList(cell("bt", "bt",
                ControlCenterLayoutPlan.Shape.CIRCLE, 0, 0, 2, 1, false, false, 0, null, false)));
        layout(editor);
        FrameLayout surface = surface(editor, 0);
        assertEquals(GradientDrawable.OVAL, material(surface).getShape());
        assertEquals(surface.getMeasuredWidth(), surface.getMeasuredHeight());
        assertEquals(editor.getChildAt(0).getMeasuredHeight(), surface.getMeasuredHeight());
        assertTrue(surface.getLeft() > 0);
    }
    @Test public void capsuleRadiusIsHalfTheShortEdge() {
        ControlCenterGridEditor editor = editor();
        editor.setCells(4, Collections.singletonList(simple("wifi", "wifi",
                ControlCenterLayoutPlan.Shape.CAPSULE, 3)));
        layout(editor);
        FrameLayout surface = surface(editor, 0);
        assertEquals(Math.min(surface.getWidth(), surface.getHeight()) / 2f,
                material(surface).getCornerRadius(), 0f);
    }
    @Test public void globalRadiusRebindsExistingCardWithoutChangingIndependentRadius() {
        ControlCenterGridEditor editor = editor();
        editor.setCells(4, Arrays.asList(simple("a", "wifi", ControlCenterLayoutPlan.Shape.RECTANGLE, 0),
                simple("b", "bt", ControlCenterLayoutPlan.Shape.RECTANGLE, 3)));
        layout(editor);
        View original = editor.getChildAt(0);
        assertEquals(24f, material(surface(editor, 0)).getCornerRadius(), 0f);
        editor.setGeometry(8, 20);
        layout(editor);
        assertSame(original, editor.getChildAt(0));
        assertEquals(20f, material(surface(editor, 0)).getCornerRadius(), 0f);
        assertEquals(3f, material(surface(editor, 1)).getCornerRadius(), 0f);
    }
    @Test public void zeroGlobalRadiusRemainsAnExplicitSquareSetting() {
        ControlCenterGridEditor editor = editor();
        editor.setGeometry(4, 0);
        editor.setCells(4, Collections.singletonList(simple("a", "wifi", ControlCenterLayoutPlan.Shape.RECTANGLE, 0)));
        layout(editor);
        assertEquals(0f, material(surface(editor, 0)).getCornerRadius(), 0f);
    }
    @Test public void capturedImageIsNotStretchedIntoTheEditableSurface() {
        ControlCenterGridEditor editor = editor();
        Bitmap capture = Bitmap.createBitmap(68, 68, Bitmap.Config.ARGB_8888);
        editor.setCells(4, Collections.singletonList(new ControlCenterGridEditor.Cell("a", "wifi", "",
                "WLAN", null, 0, 0, 1, 1, false, capture, null,
                ControlCenterLayoutPlan.Shape.RECTANGLE, 4, false, false, 0, 100)));
        layout(editor);
        assertFalse(containsImage(surface(editor, 0)));
        assertFalse("Renderer does not own capture lifecycle", capture.isRecycled());
    }
    private boolean containsImage(View view) {
        if (view instanceof ImageView) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsImage(group.getChildAt(i))) return true;
        }
        return false;
    }
    @Test public void activeAndInactiveSurfacesHaveDistinctPalettes() {
        ControlCenterGridEditor editor = editor();
        editor.setCells(4, Arrays.asList(simple("off", "wifi", ControlCenterLayoutPlan.Shape.CIRCLE, 0),
                simple("on", "bt", ControlCenterLayoutPlan.Shape.CIRCLE, 0)));
        layout(editor);
        assertEquals(ControlCenterCardView.INACTIVE_SURFACE, material(surface(editor, 0)).getColor().getDefaultColor());
        assertEquals(ControlCenterCardView.ACTIVE_SURFACE, material(surface(editor, 1)).getColor().getDefaultColor());
    }
    @Test public void hostAndRendererIdentitySurvivePositionAndZChanges() {
        ControlCenterGridEditor editor = editor();
        editor.setCells(4, Arrays.asList(simple("a", "wifi", ControlCenterLayoutPlan.Shape.RECTANGLE, 0),
                simple("b", "bt", ControlCenterLayoutPlan.Shape.RECTANGLE, 0)));
        layout(editor);
        View host = editor.getChildAt(0);
        FrameLayout surface = surface(editor, 0);
        View content = surface.getChildAt(0);
        editor.setCells(4, Arrays.asList(cell("a", "wifi", ControlCenterLayoutPlan.Shape.RECTANGLE,
                        0, 2, 1, 1, false, false, 5, null, false),
                simple("b", "bt", ControlCenterLayoutPlan.Shape.RECTANGLE, 0)));
        layout(editor);
        assertSame(host, editor.getChildAt(1));
        assertSame(surface, surface(editor, 1));
        assertSame(content, surface.getChildAt(0));
        assertEquals(2 * (ControlCenterGridGeometry.columnUnitPx(400, 4, 4) + 4), host.getLeft());
        assertEquals(0, edits);
    }
    @Test public void reboundHostUsesCurrentLockAndHiddenStateAndStillOpensProperties() {
        ControlCenterGridEditor editor = editor();
        editor.setCells(4, Collections.singletonList(simple("a", "wifi", ControlCenterLayoutPlan.Shape.RECTANGLE, 0)));
        View host = editor.getChildAt(0);
        editor.setCells(4, Collections.singletonList(cell("a", "wifi", ControlCenterLayoutPlan.Shape.RECTANGLE,
                0, 0, 1, 1, true, true, 0, null, false)));
        assertSame(host, editor.getChildAt(0));
        assertEquals(0.36f, host.getAlpha(), 0f);
        assertTrue("Locked items can be selected to expose unlock", host.performLongClick());
        host.performClick();
        assertEquals("a", clicked);
        assertTrue(host.getContentDescription().toString().contains("已锁定"));
        assertTrue(host.getContentDescription().toString().contains("已隐藏"));
        assertTrue(host.getContentDescription().toString().contains("示意预览"));
        assertEquals(0, edits);
    }
    @Test public void pairDirectionChangesInsideTheSameRenderer() {
        ControlCenterGridEditor editor = editor();
        editor.setCells(4, Collections.singletonList(cell("pair", "wifi", ControlCenterLayoutPlan.Shape.RECTANGLE,
                20, 0, 2, 1, false, false, 0, "BT", true)));
        layout(editor);
        FrameLayout surface = surface(editor, 0);
        assertEquals(LinearLayout.HORIZONTAL, ((LinearLayout) surface.getChildAt(0)).getOrientation());
        editor.setCells(4, Collections.singletonList(cell("pair", "wifi", ControlCenterLayoutPlan.Shape.RECTANGLE,
                20, 0, 1, 2, false, false, 0, "BT", false)));
        layout(editor);
        assertSame(surface, surface(editor, 0));
        assertEquals(LinearLayout.VERTICAL, ((LinearLayout) surface.getChildAt(0)).getOrientation());
    }
    @Test public void specialComponentsHaveOneClippedSurfaceAndFillTheirSlot() {
        for (String spec : new String[] {ControlCenterComponentSpec.VOLUME,
                ControlCenterComponentSpec.MEDIA, ControlCenterComponentSpec.DEVICE_CENTER}) {
            ControlCenterGridEditor editor = editor();
            editor.setCells(4, Collections.singletonList(simple(spec, spec, ControlCenterLayoutPlan.Shape.RECTANGLE, 20)));
            layout(editor);
            FrameLayout surface = surface(editor, 0);
            assertTrue(surface.getClipToOutline());
            assertNull(surface.getChildAt(0).getBackground());
            assertEquals(surface.getWidth(), surface.getChildAt(0).getWidth());
        }
    }
    @Test public void squareTilesUseIconOnlyContentLikeTheRuntime() {
        ControlCenterGridEditor editor = editor();
        editor.setCells(4, Collections.singletonList(cell("wifi", "wifi", ControlCenterLayoutPlan.Shape.RECTANGLE,
                28, 0, 2, 2, false, false, 0, null, false)));
        layout(editor);
        ViewGroup tile = (ViewGroup) surface(editor, 0).getChildAt(0);
        assertFalse(tile.getChildAt(0) instanceof LinearLayout);
    }
    @Test public void removedItemsAndEmptyLayoutsDoNotLeaveStaleViews() {
        ControlCenterGridEditor editor = editor();
        editor.setCells(4, Arrays.asList(simple("a", "wifi", ControlCenterLayoutPlan.Shape.RECTANGLE, 0),
                simple("b", "bt", ControlCenterLayoutPlan.Shape.RECTANGLE, 0)));
        View retained = editor.getChildAt(1);
        editor.setCells(3, Collections.singletonList(simple("b", "bt", ControlCenterLayoutPlan.Shape.RECTANGLE, 0)));
        assertEquals(1, editor.getChildCount());
        assertSame(retained, editor.getChildAt(0));
        editor.setCells(3, null);
        layout(editor);
        assertEquals(0, editor.getChildCount());
        assertTrue(editor.getMeasuredHeight() > 0);
        assertEquals(0, edits);
    }
    @Test public void canvasHasOnlyALowOpacityScrim() {
        ControlCenterGridEditor editor = editor();
        assertTrue(editor.getBackground() instanceof ColorDrawable);
        assertEquals(10, Color.alpha(((ColorDrawable) editor.getBackground()).getColor()));
    }
}

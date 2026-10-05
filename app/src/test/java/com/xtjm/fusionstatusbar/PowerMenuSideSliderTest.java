package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PowerMenuSideSliderTest {
    private final List<Integer> actions = new ArrayList<>();
    private long eventTime;

    @Test public void commitsBothDirectionsOnceOnlyAfterReleaseAndResetsThumb() {
        PowerMenuSideSlider slider = slider(64, 288);
        touch(slider, MotionEvent.ACTION_DOWN, 32, 144);
        touch(slider, MotionEvent.ACTION_MOVE, 32, 45);
        assertTrue(actions.isEmpty());
        touch(slider, MotionEvent.ACTION_UP, 32, 45);
        assertEquals(List.of(-1), actions);
        assertEquals(0, thumb(slider).getTranslationY(), 0f);
        touch(slider, MotionEvent.ACTION_UP, 32, 45);
        assertEquals(List.of(-1), actions);
        touch(slider, MotionEvent.ACTION_DOWN, 32, 144);
        touch(slider, MotionEvent.ACTION_MOVE, 32, 243);
        touch(slider, MotionEvent.ACTION_UP, 32, 243);
        assertEquals(List.of(-1, 1), actions);
    }

    @Test public void tapsShortDragsOutsideStartsAndReturningToCenterNeverCommit() {
        PowerMenuSideSlider slider = slider(64, 288);
        gesture(slider, 144, 144, MotionEvent.ACTION_UP);
        gesture(slider, 144, 204, MotionEvent.ACTION_UP);
        gesture(slider, 30, 3, MotionEvent.ACTION_UP);
        touch(slider, MotionEvent.ACTION_DOWN, 32, 144);
        touch(slider, MotionEvent.ACTION_MOVE, 32, 40);
        touch(slider, MotionEvent.ACTION_UP, 32, 144);
        assertTrue(actions.isEmpty());
    }

    @Test public void cancelAndMultiplePointersInvalidateAnArmedGesture() {
        PowerMenuSideSlider slider = slider(64, 288);
        gesture(slider, 144, 40, MotionEvent.ACTION_CANCEL);
        touch(slider, MotionEvent.ACTION_UP, 32, 40);
        touch(slider, MotionEvent.ACTION_DOWN, 32, 144);
        touch(slider, MotionEvent.ACTION_MOVE, 32, 40);
        MotionEvent multi = multiPointerEvent();
        try { slider.dispatchTouchEvent(multi); } finally { multi.recycle(); }
        touch(slider, MotionEvent.ACTION_UP, 32, 40);
        assertTrue(actions.isEmpty());
        assertEquals(0, thumb(slider).getTranslationY(), 0f);
    }

    @Test public void disabledActionsAndMidGesturePolicyChangesDoNotExecute() {
        PowerMenuSideSlider slider = slider(64, 288);
        slider.setActionEnabled(false, true);
        gesture(slider, 144, 40, MotionEvent.ACTION_UP);
        assertTrue(actions.isEmpty());
        gesture(slider, 144, 248, MotionEvent.ACTION_UP);
        assertEquals(List.of(1), actions);
        touch(slider, MotionEvent.ACTION_DOWN, 32, 144);
        touch(slider, MotionEvent.ACTION_MOVE, 32, 248);
        slider.setActionEnabled(false, false);
        touch(slider, MotionEvent.ACTION_UP, 32, 248);
        assertEquals(List.of(1), actions);
        assertEquals(0, thumb(slider).getTranslationY(), 0f);
    }

    @Test public void releaseOutsideHorizontalBoundsAndUpWithoutMoveDoNotExecute() {
        PowerMenuSideSlider slider = slider(64, 288);
        touch(slider, MotionEvent.ACTION_DOWN, 32, 144);
        touch(slider, MotionEvent.ACTION_MOVE, 32, 40);
        touch(slider, MotionEvent.ACTION_UP, 140, 40);
        touch(slider, MotionEvent.ACTION_DOWN, 32, 144);
        touch(slider, MotionEvent.ACTION_UP, 32, 40);
        assertTrue(actions.isEmpty());
    }

    @Test public void accessibilityOffersNamedActionsAndHonorsDisabledState() {
        PowerMenuSideSlider slider = slider(64, 288);
        AccessibilityNodeInfo info = slider.createAccessibilityNodeInfo();
        assertTrue(info.getActionList().stream().anyMatch(action -> action.getId()
                == PowerMenuSideSlider.ACTION_TOP && "线刷模式".contentEquals(action.getLabel())));
        assertTrue(info.getActionList().stream().anyMatch(action -> action.getId()
                == PowerMenuSideSlider.ACTION_BOTTOM && "卡刷模式".contentEquals(action.getLabel())));
        assertTrue(slider.performAccessibilityAction(PowerMenuSideSlider.ACTION_TOP, null));
        slider.setActionEnabled(true, false);
        assertFalse(slider.performAccessibilityAction(PowerMenuSideSlider.ACTION_BOTTOM, null));
        slider.setEnabled(false);
        assertFalse(slider.performAccessibilityAction(PowerMenuSideSlider.ACTION_TOP, null));
        assertEquals(List.of(-1), actions);
    }

    @Test public void materialAffectsOnlyTrackAndContentFollowsSharedAppearance() {
        PowerMenuSideSlider slider = slider(64, 288);
        slider.applyAppearance(ControlCenterConfig.defaults().withSurface(ControlCenterSurface.LIGHT)
                .withBlur(100, 100, 25).withCornerRadius(8).withGlobalBorder(true, 2));
        layout(slider, 64, 360);
        assertNull(slider.getBackground());
        assertTrue(SystemSurfaceStyle.isApplied(slider.getChildAt(0)));
        assertFalse(SystemSurfaceStyle.isApplied(slider));
        assertEquals(ControlCenterSurface.LIGHT.neutralForeground(), label(slider, "线刷模式").getCurrentTextColor());
        Bitmap bitmap = Bitmap.createBitmap(64, 360, Bitmap.Config.ARGB_8888);
        slider.draw(new Canvas(bitmap));
        assertTrue(Color.alpha(bitmap.getPixel(32, 116)) > 0);
        assertTrue(Color.alpha(bitmap.getPixel(32, 116)) < 100);
        assertEquals(Color.WHITE, bitmap.getPixel(16, 180));
        bitmap.recycle();
    }

    @Test public void compactGeometryKeepsLabelsAndIconsSeparateWithin320DpScreen() {
        PowerMenuSideSlider slider = slider(48, 144);
        slider.applyAppearance(ControlCenterConfig.defaults().withTileScale(130));
        layout(slider, 48, 216);
        TextView top = label(slider, "线刷模式"), bottom = label(slider, "卡刷模式");
        assertFalse(slider.getClipChildren());
        assertFalse(slider.getClipToPadding());
        assertEquals(0, top.getTop());
        assertEquals(216, bottom.getBottom());
        assertTrue(top.getWidth() <= 80);
        assertTrue(top.getPaint().measureText(top.getText().toString()) <= top.getWidth());
        assertTrue(bottom.getPaint().measureText(bottom.getText().toString()) <= bottom.getWidth());
        assertTrue(slider.getChildAt(1).getBottom() < thumb(slider).getTop());
        assertTrue(slider.getChildAt(2).getTop() > thumb(slider).getBottom());
        FrameLayout parent = new FrameLayout(slider.getContext());
        parent.setClipChildren(false);
        parent.setClipToPadding(false);
        parent.addView(slider, new FrameLayout.LayoutParams(48, 216));
        layout(parent, 320, 240);
        slider.layout(32, 12, 80, 228);
        Bitmap bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
        parent.draw(new Canvas(bitmap));
        java.io.File output = new java.io.File("build/power-menu-compact.png");
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        assertTrue(hasVisiblePixels(bitmap, 20, 12, 92, 40));
        assertTrue(hasVisiblePixels(bitmap, 20, 200, 92, 228));
        bitmap.recycle();
    }

    @Test public void missingModulePackageStillHasPlatformFallbackIcons() {
        Context context = new ContextWrapper(activity()) {
            @Override public String getPackageName() { return "android"; }
            @Override public Context createPackageContext(String name, int flags)
                    throws PackageManager.NameNotFoundException {
                throw new PackageManager.NameNotFoundException(name);
            }
        };
        PowerMenuSideSlider slider = new PowerMenuSideSlider(context, "线刷模式",
                R.drawable.cc_icon_usb, "卡刷模式", R.drawable.cc_icon_settings_backup_restore, actions::add);
        layout(slider, 64, 288);
        assertNotNull(((ImageView) slider.getChildAt(1)).getDrawable());
        assertNotNull(((ImageView) slider.getChildAt(2)).getDrawable());
        assertNotNull(thumb(slider).getDrawable());
    }

    @Test public void touchesOutsideThumbDoNotFallThroughToTheNativePowerGesture() {
        PowerMenuSideSlider slider = slider(64, 288);
        FrameLayout parent = new FrameLayout(slider.getContext());
        int[] parentTouches = {0};
        parent.setOnTouchListener((view, event) -> { parentTouches[0]++; return true; });
        parent.addView(slider, new FrameLayout.LayoutParams(64, 288));
        layout(parent, 100, 320);
        long now = SystemClock.uptimeMillis();
        for (int type : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(now, now += 16, type, 32, 30, 0);
            try { parent.dispatchTouchEvent(event); } finally { event.recycle(); }
        }
        assertEquals(0, parentTouches[0]);
        assertTrue(actions.isEmpty());
    }

    @Test public void theFourSideActionsHaveDistinctRenderedIcons() {
        java.util.Set<Integer> signatures = new java.util.HashSet<>();
        Activity activity = activity();
        for (int resource : new int[] {R.drawable.cc_icon_usb, R.drawable.cc_icon_settings_backup_restore,
                R.drawable.cc_icon_airplanemode_active, R.drawable.cc_icon_notifications_off}) {
            android.graphics.drawable.Drawable icon = activity.getDrawable(resource);
            Bitmap bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, 48, 48);
            icon.draw(new Canvas(bitmap));
            assertTrue(hasVisiblePixels(bitmap, 0, 0, 48, 48));
            int[] pixels = new int[48 * 48];
            bitmap.getPixels(pixels, 0, 48, 0, 0, 48, 48);
            signatures.add(java.util.Arrays.hashCode(pixels));
            bitmap.recycle();
        }
        assertEquals(4, signatures.size());
    }

    private PowerMenuSideSlider slider(int width, int height) {
        PowerMenuSideSlider result = new PowerMenuSideSlider(activity(), "线刷模式",
                R.drawable.cc_icon_usb, "卡刷模式", R.drawable.cc_icon_settings_backup_restore, actions::add);
        layout(result, width, height + 72);
        return result;
    }

    private Activity activity() { return Robolectric.buildActivity(Activity.class).setup().get(); }

    private void gesture(PowerMenuSideSlider slider, float from, float to, int endAction) {
        touch(slider, MotionEvent.ACTION_DOWN, slider.getWidth() / 2f, from);
        touch(slider, MotionEvent.ACTION_MOVE, slider.getWidth() / 2f, to);
        touch(slider, endAction, slider.getWidth() / 2f, to);
    }

    private void touch(PowerMenuSideSlider slider, int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(eventTime, eventTime += 16, action, x, y + 36, 0);
        try { slider.dispatchTouchEvent(event); } finally { event.recycle(); }
    }

    private MotionEvent multiPointerEvent() {
        MotionEvent.PointerProperties first = new MotionEvent.PointerProperties();
        first.id = 0; first.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerProperties second = new MotionEvent.PointerProperties();
        second.id = 1; second.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords firstCoords = new MotionEvent.PointerCoords();
        firstCoords.x = 32; firstCoords.y = 76; firstCoords.pressure = 1;
        MotionEvent.PointerCoords secondCoords = new MotionEvent.PointerCoords();
        secondCoords.x = 32; secondCoords.y = 180; secondCoords.pressure = 1;
        return MotionEvent.obtain(eventTime, eventTime += 16,
                MotionEvent.ACTION_POINTER_DOWN | 1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT,
                2, new MotionEvent.PointerProperties[] {first, second},
                new MotionEvent.PointerCoords[] {firstCoords, secondCoords}, 0, 0, 1, 1, 0, 0, 0, 0);
    }

    private static ImageView thumb(PowerMenuSideSlider slider) {
        return (ImageView) slider.getChildAt(slider.getChildCount() - 1);
    }

    private static TextView label(PowerMenuSideSlider slider, String text) {
        for (int i = 0; i < slider.getChildCount(); i++)
            if (slider.getChildAt(i) instanceof TextView label && text.contentEquals(label.getText())) return label;
        throw new AssertionError("Label not found: " + text);
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static boolean hasVisiblePixels(Bitmap bitmap, int left, int top, int right, int bottom) {
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++)
            if (Color.alpha(bitmap.getPixel(x, y)) > 0) return true;
        return false;
    }
}

package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
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
public class NotificationSurfaceHooksTest {
    @Test public void distinguishesHeadsUpAndDisappearingHeadsUpFromShadeRows() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        NotificationRow row = new NotificationRow(activity);
        assertFalse(NotificationSurfaceHooks.isHeadsUp(row));
        row.mIsHeadsUp = true;
        assertTrue(NotificationSurfaceHooks.isHeadsUp(row));
        row.mIsHeadsUp = false;
        row.mHeadsupDisappearRunning = true;
        assertTrue(NotificationSurfaceHooks.isHeadsUp(row));
        row.mHeadsupDisappearRunning = false;
        row.pinned = true;
        assertTrue(NotificationSurfaceHooks.isHeadsUp(row));
    }

    @Test public void targetsOnlyTheNativeBackgroundAndLeavesContentInPlace() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        NotificationRow row = new NotificationRow(activity);
        View content = new View(activity);
        row.addView(content);
        assertSame(row.mBackgroundNormal, NotificationSurfaceHooks.backgroundOf(row));
        assertSame(row, content.getParent());
        assertEquals(2, row.getChildCount());
    }

    @Test public void collapsedSurfaceUsesActualHeightAndNativeClipAmounts() {
        NotificationBackgroundView view = background();
        view.mActualHeight = 48;
        view.mClipTopAmount = 8;
        view.mClipBottomAmount = 10;
        Bitmap bitmap = draw(view);
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(40, 4));
        assertEquals(Color.RED, bitmap.getPixel(40, 20));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(40, 42));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(40, 70));
        bitmap.recycle();
    }

    @Test public void launchAnimationUsesAnimatedWidthAndHeightCentered() {
        NotificationBackgroundView view = background();
        view.mExpandAnimationRunning = true;
        view.mExpandAnimationWidth = 50;
        view.mExpandAnimationHeight = 70;
        view.mActualHeight = 40;
        view.mClipTopAmount = 30;
        view.mClipBottomAmount = 30;
        Bitmap bitmap = draw(view);
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(10, 20));
        assertEquals(Color.RED, bitmap.getPixel(30, 20));
        assertEquals(Color.RED, bitmap.getPixel(60, 60));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(60, 80));
        bitmap.recycle();
    }

    @Test public void rtlUsesActualWidthAndNativeFadeWithoutMutatingStyle() {
        NotificationBackgroundView view = background();
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        view.mActualWidth = 50;
        view.mActualHeight = 50;
        view.mBackground.setAlpha(128);
        NotificationSurfaceHooks.BoundedBackground material = material(view);
        Drawable content = material.content;
        content.setBounds(1, 2, 3, 4);
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        material.draw(new Canvas(bitmap));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(20, 20));
        assertEquals(128, Color.alpha(bitmap.getPixel(70, 20)));
        assertEquals(255, content.getAlpha());
        assertEquals(new android.graphics.Rect(1, 2, 3, 4), content.getBounds());
        bitmap.recycle();
    }

    @Test public void fullyClippedBackgroundDrawsNothing() {
        NotificationBackgroundView view = background();
        view.mActualHeight = 30;
        view.mClipTopAmount = 20;
        view.mClipBottomAmount = 10;
        Bitmap bitmap = draw(view);
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(40, 20));
        bitmap.recycle();
    }

    private static NotificationBackgroundView background() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        NotificationBackgroundView view = new NotificationBackgroundView(activity);
        view.layout(0, 0, 100, 100);
        return view;
    }

    private static NotificationSurfaceHooks.BoundedBackground material(NotificationBackgroundView view) {
        NotificationSurfaceHooks.BoundedBackground result =
                new NotificationSurfaceHooks.BoundedBackground(view);
        result.content = new ColorDrawable(Color.RED);
        return result;
    }

    private static Bitmap draw(NotificationBackgroundView view) {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        material(view).draw(new Canvas(bitmap));
        return bitmap;
    }

    public static final class NotificationRow extends FrameLayout {
        public final View mBackgroundNormal;
        public boolean mIsHeadsUp, mHeadsupDisappearRunning, pinned;
        NotificationRow(Activity context) {
            super(context);
            mBackgroundNormal = new NotificationBackgroundView(context);
            addView(mBackgroundNormal);
        }
        public boolean isHeadsUpState() { return mIsHeadsUp || mHeadsupDisappearRunning; }
        public boolean isPinned() { return pinned; }
    }

    public static final class NotificationBackgroundView extends View {
        public int mActualHeight = -1, mActualWidth = -1;
        public int mExpandAnimationWidth = -1, mExpandAnimationHeight = -1;
        public int mClipTopAmount, mClipBottomAmount, mDrawableAlpha = 255;
        public boolean mExpandAnimationRunning, mBottomIsRounded, mBottomAmountClips = true;
        public Drawable mBackground = new ColorDrawable(Color.BLUE);
        NotificationBackgroundView(Activity context) { super(context); }
    }
}

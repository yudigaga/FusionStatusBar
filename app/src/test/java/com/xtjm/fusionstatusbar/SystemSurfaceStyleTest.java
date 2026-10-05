package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
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
public class SystemSurfaceStyleTest {
    private Activity activity() { return Robolectric.buildActivity(Activity.class).setup().get(); }

    @Test public void appliesWithoutQsCustomizationAndRestoresOriginalContentAndBackground() {
        Activity activity = activity();
        FrameLayout root = new FrameLayout(activity);
        Drawable original = new ColorDrawable(Color.RED);
        root.setBackground(original);
        root.setPadding(4, 5, 6, 7);
        TextView text = new TextView(activity);
        text.setTextColor(Color.BLUE);
        text.setTextSize(20);
        root.addView(text);
        ControlCenterConfig config = ControlCenterConfig.defaults().withEnabled(false)
                .withSurface(ControlCenterSurface.LIGHT).withTileScale(120);
        SystemSurfaceStyle.apply(root, config, true);
        SystemSurfaceStyle.applyContent(root, config, true);
        assertNotSame(original, root.getBackground());
        assertEquals(ControlCenterSurface.LIGHT.neutralForeground(), text.getCurrentTextColor());
        assertEquals(24, text.getTextSize(), .01f);
        assertEquals(5, root.getPaddingTop());
        assertEquals(1f, root.getAlpha(), 0f);
        SystemSurfaceStyle.apply(root, config, false);
        SystemSurfaceStyle.applyContent(root, config, false);
        assertSame(original, root.getBackground());
        assertEquals(Color.BLUE, text.getCurrentTextColor());
        assertEquals(20, text.getTextSize(), .01f);
    }

    @Test public void repeatedNullBackgroundUpdatesDoNotWrapTheirOwnDrawable() {
        View view = new View(activity());
        ControlCenterConfig config = ControlCenterConfig.defaults();
        SystemSurfaceStyle.apply(view, config, true);
        Drawable material = view.getBackground();
        SystemSurfaceStyle.apply(view, config, true);
        assertSame(material, view.getBackground());
        Bitmap bitmap = draw(view.getBackground());
        assertTrue(Color.alpha(bitmap.getPixel(50, 50)) > 0);
        bitmap.recycle();
        SystemSurfaceStyle.restore(view);
        assertNull(view.getBackground());
    }

    @Test public void cardAndTrackStrengthsAreIndependentAndBorderDoesNotFillTransparentCenter() {
        View card = new View(activity());
        View track = new View(card.getContext());
        ControlCenterConfig config = ControlCenterConfig.defaults().withSurface(ControlCenterSurface.DARK)
                .withBlur(100, 50, 25).withCornerRadius(12);
        SystemSurfaceStyle.apply(card, config, true);
        SystemSurfaceStyle.applyTrack(track, config, true);
        Bitmap first = draw(card.getBackground()), second = draw(track.getBackground());
        assertEquals(100, Color.alpha(first.getPixel(50, 50)));
        assertEquals(50, Color.alpha(second.getPixel(50, 50)));
        assertEquals(0, Color.alpha(first.getPixel(0, 0)));
        first.recycle(); second.recycle();
        SystemSurfaceStyle.apply(card, config.withSurface(ControlCenterSurface.CLEAR).withGlobalBorder(true, 2), true);
        Bitmap border = draw(card.getBackground());
        assertEquals(0, Color.alpha(border.getPixel(50, 50)));
        assertTrue(Color.alpha(border.getPixel(50, 1)) > 0);
        border.recycle();
    }

    @Test public void nativeBlurRequestsDoNotCompoundAndDisablingRestoresLatestNativeRequest() {
        NativeView view = new NativeView(activity());
        ControlCenterConfig config = ControlCenterConfig.defaults().withBlur(100, 50, 100);
        SystemSurfaceStyle.apply(view, config, true);
        assertEquals(60, view.radius);
        SystemSurfaceStyle.apply(view, config, true);
        assertEquals(60, view.radius);
        Object[] requested = SystemSurfaceStyle.nativeArguments(view, "setMiBackgroundBlurRadius", new Object[] {200});
        view.setMiBackgroundBlurRadius((Integer) requested[0]);
        assertEquals(100, view.radius);
        SystemSurfaceStyle.restore(view);
        assertEquals(200, view.radius);
        assertEquals(1, view.mode);
    }

    @Test public void backdropControlsBlurWithoutInstallingACardBackground() {
        Activity activity = activity();
        NativeView root = new NativeView(activity);
        ControlCenterConfig config = ControlCenterConfig.defaults().withBlur(25, 100, 100);
        SystemSurfaceStyle.applyBackdrop(root, activity.getWindow(), config, true);
        assertNull(root.getBackground());
        assertEquals(30, root.radius);
        assertEquals(40, SystemSurfaceStyle.windowRadius(activity.getWindow(), 160));
        SystemSurfaceStyle.applyBackdrop(root, activity.getWindow(), config, false);
        assertEquals(120, root.radius);
        assertEquals(160, SystemSurfaceStyle.windowRadius(activity.getWindow(), 160));
    }

    @Test public void nativeNotificationAlphaIsAppliedOnce() {
        Activity activity = activity();
        NotificationSurfaceHooksTest.NotificationRow row = new NotificationSurfaceHooksTest.NotificationRow(activity);
        NotificationSurfaceHooksTest.NotificationBackgroundView background =
                (NotificationSurfaceHooksTest.NotificationBackgroundView) row.mBackgroundNormal;
        background.layout(0, 0, 100, 100);
        background.mBackground.setAlpha(128);
        NotificationSurfaceHooks.applyRow(row, FusionConfig.defaults());
        Bitmap bitmap = draw(background.getBackground());
        assertEquals(128, Color.alpha(bitmap.getPixel(50, 50)));
        bitmap.recycle();
        NotificationSurfaceHooks.applyRow(row, FusionConfig.defaults().withControlCenter(
                ControlCenterConfig.defaults().withSystemSurfaces(SystemSurfaceConfig.defaults().withNotifications(false))));
        assertNull(background.getBackground());
        assertEquals(128, background.mBackground.getAlpha());
    }

    private static Bitmap draw(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, 100, 100);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    public static final class NativeView extends View {
        int radius = 120, mode = 1, viewMode = 1;
        NativeView(Context context) { super(context); }
        public int getMiBackgroundBlurRadius() { return radius; }
        public int getMiBackgroundBlurMode() { return mode; }
        public int getMiViewBlurMode() { return viewMode; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiBackgroundBlurMode(int value) { mode = value; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
    }
}

package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
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
public class ControlCenterAdaptiveGlassConsumersTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void previewUsesItsOwnThemeAndUpdatesTheSameCardWithoutRebinding() {
        Context day = context(false), night = context(true);
        ControlCenterCardView light = fiveG(day), dark = fiveG(night);
        assertEquals(ControlCenterSurface.HIGHLIGHT_GLASS.color, surfaceColor(light));
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, surfaceColor(dark));
        assertEquals(0xff202124, fiveGText(light).getCurrentTextColor());
        assertEquals(Color.WHITE, fiveGText(dark).getCurrentTextColor());

        changeTheme(light, true);
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, surfaceColor(light));
        assertEquals(Color.WHITE, fiveGText(light).getCurrentTextColor());
        changeTheme(light, false);
        assertEquals(ControlCenterSurface.HIGHLIGHT_GLASS.color, surfaceColor(light));
        assertEquals(0xff202124, fiveGText(light).getCurrentTextColor());
        assertEquals("Updating a different context must not change the night preview",
                ControlCenterSurface.SOFT_GLASS.color, surfaceColor(dark));
    }

    @Test public void runtimeGridRefreshesExistingComponentColorWhenTheThemeChanges() {
        Context context = context(false);
        ControlCenterRuntimeGrid.GridView grid = new ControlCenterRuntimeGrid.GridView(context, 4, 8, 12, 0, 2);
        MediaPlayerPanel panel = new MediaPlayerPanel(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.GRAY);
        background.setCornerRadius(12);
        panel.setBackground(background);
        grid.addTile(panel, ControlCenterLayoutPlan.Item.component(ControlCenterComponentSpec.MEDIA,
                0, 0, 2, 2), -1);
        grid.applyMaterials(adaptive());
        render(grid, 400, 240).recycle();
        assertCenter(panel, ControlCenterSurface.HIGHLIGHT_GLASS.color);
        changeTheme(grid, true);
        render(grid, 400, 240).recycle();
        assertCenter(panel, ControlCenterSurface.SOFT_GLASS.color);
        changeTheme(grid, false);
        render(grid, 400, 240).recycle();
        assertCenter(panel, ControlCenterSurface.HIGHLIGHT_GLASS.color);
        grid.releaseMaterials();
    }

    @Test public void systemSurfacesRefreshContentAndTintOnTheNextFrameAndRestoreOriginals() {
        Context context = context(false);
        FrameLayout root = new FrameLayout(context);
        Drawable original = new android.graphics.drawable.ColorDrawable(Color.RED);
        root.setBackground(original);
        TextView label = new TextView(context);
        label.setTextColor(Color.BLUE);
        root.addView(label);
        SystemSurfaceStyle.apply(root, adaptive(), true);
        SystemSurfaceStyle.applyContent(root, adaptive(), true);
        assertEquals(0xff202124, label.getCurrentTextColor());
        assertCenter(root, ControlCenterSurface.HIGHLIGHT_GLASS.color);

        changeTheme(root, true);
        root.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(Color.WHITE, label.getCurrentTextColor());
        assertCenter(root, ControlCenterSurface.SOFT_GLASS.color);
        changeTheme(root, false);
        root.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(0xff202124, label.getCurrentTextColor());
        assertCenter(root, ControlCenterSurface.HIGHLIGHT_GLASS.color);

        SystemSurfaceStyle.restore(root);
        SystemSurfaceStyle.applyContent(root, adaptive(), false);
        assertSame(original, root.getBackground());
        assertEquals(Color.BLUE, label.getCurrentTextColor());
        changeTheme(root, true);
        root.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals("Restored content must no longer be recolored", Color.BLUE, label.getCurrentTextColor());
    }

    @Test public void groupLabelsAndPowerActionsFollowThemeWithoutReplacingTheirViews() {
        Context context = context(false);
        ControlCenterGroupEntry group = new ControlCenterGroupEntry(context, new View(context),
                true, false, () -> new String[] {"WLAN", "已关闭"});
        group.applySurface(ControlCenterSurface.HIGHLIGHT_GLASS);
        TextView title = (TextView) group.getChildAt(1);
        assertEquals(0xff202124, title.getCurrentTextColor());
        changeTheme(group, true);
        assertEquals(Color.WHITE, title.getCurrentTextColor());

        PowerMenuSideSlider slider = new PowerMenuSideSlider(context, "重启", R.drawable.cc_icon_swap_vert,
                "关机", R.drawable.cc_icon_swap_vert, direction -> { });
        slider.applyAppearance(adaptive());
        ImageView icon = (ImageView) slider.getChildAt(1);
        assertEquals(Color.WHITE, icon.getImageTintList().getDefaultColor());
        changeTheme(slider, false);
        assertEquals(0xff202124, icon.getImageTintList().getDefaultColor());
        changeTheme(slider, true);
        assertEquals(Color.WHITE, icon.getImageTintList().getDefaultColor());
        SystemSurfaceStyle.restore(slider.getChildAt(0));
    }

    private static ControlCenterConfig adaptive() {
        return ControlCenterConfig.defaults().withEnabled(true).withSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
    }

    private static Context context(boolean night) {
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return app.createConfigurationContext(configuration);
    }

    @SuppressWarnings("deprecation")
    private static void changeTheme(View view, boolean night) {
        Configuration configuration = new Configuration(view.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        view.getResources().updateConfiguration(configuration, view.getResources().getDisplayMetrics());
        view.dispatchConfigurationChanged(configuration);
    }

    private static ControlCenterCardView fiveG(Context context) {
        ControlCenterCardView card = new ControlCenterCardView(context);
        card.bind(new ControlCenterCardView.Model(ControlCenterComponentSpec.FIVE_G, "", "5G", null,
                false, false, false, false, ControlCenterLayoutPlan.Shape.CIRCLE, 12, 100));
        card.setSurface(ControlCenterSurface.HIGHLIGHT_GLASS);
        return card;
    }

    private static int surfaceColor(ControlCenterCardView card) {
        return ((GradientDrawable) card.getChildAt(0).getBackground()).getColor().getDefaultColor();
    }

    private static TextView fiveGText(ControlCenterCardView card) {
        return (TextView) ((ViewGroup) card.getChildAt(0)).getChildAt(0);
    }

    private static void assertCenter(View view, int expected) {
        Drawable background = view.getBackground();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        background.setBounds(0, 0, 100, 100);
        background.draw(new Canvas(bitmap));
        int actual = bitmap.getPixel(50, 50);
        assertEquals(Color.alpha(expected), Color.alpha(actual));
        assertEquals(Color.red(expected), Color.red(actual), 1);
        assertEquals(Color.green(expected), Color.green(actual), 1);
        assertEquals(Color.blue(expected), Color.blue(actual), 1);
        bitmap.recycle();
    }

    private static Bitmap render(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    public static final class MediaPlayerPanel extends FrameLayout {
        MediaPlayerPanel(Context context) { super(context); }
    }
}

package com.xtjm.fusionstatusbar;

import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ControlCenterMaterialsTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }
    private ControlCenterConfig config(int background, int card, int tile) {
        return ControlCenterConfig.defaults().withEnabled(true).withBlur(background, card, tile);
    }
    @Test public void settingsClampRoundTripAndSurviveEveryConfigCopy() {
        var base = config(12, 34, 56);
        var changed = base.withOrder("wifi").withHidden("bt").withColumns(5).withTileScale(110)
                .withCornerRadius(22).withSpacing(8).withStyle(1).withTileLayout("")
                .withLayoutPlan(ControlCenterLayoutPlan.blank(4).encode()).withComponentOrder("control:media")
                .withComponentSide("control:media", "left").withEnabled(false);
        Bundle bundle = new Bundle(); changed.writeTo(bundle);
        var roundTrip = ControlCenterConfig.fromBundle(bundle);
        assertArrayEquals(new int[] {12, 34, 56}, amounts(roundTrip));
        var preferences = RuntimeEnvironment.getApplication().getSharedPreferences("materials", 0);
        var editor = preferences.edit(); roundTrip.writeTo(editor); editor.commit();
        assertArrayEquals(amounts(roundTrip), amounts(ControlCenterConfig.read(preferences)));
        assertArrayEquals(new int[] {0, 100, 50}, amounts(base.withBlur(-9, 150, 50)));
        assertArrayEquals(new int[] {100, 100, 100}, amounts(base.clearCustomization()));
        assertArrayEquals(new int[] {100, 100, 100}, amounts(ControlCenterConfig.fromBundle(new Bundle())));
    }
    private int[] amounts(ControlCenterConfig config) { return new int[] {config.backgroundBlur, config.cardBlur, config.tileBlur}; }

    @Test public void materialTargetsAreSeparateAndUnknownViewsAreUntouched() {
        QSCardItemView card = new QSCardItemView();
        QSTileItemIconView tile = new QSTileItemIconView();
        View glyph = new View(tile.getContext()); tile.addView(glyph);
        assertEquals(ControlCenterMaterials.Layer.CARD, ControlCenterMaterials.layer(card));
        assertEquals(ControlCenterMaterials.Layer.TILE, ControlCenterMaterials.layer(glyph));
        assertEquals(ControlCenterMaterials.Layer.NONE, ControlCenterMaterials.layer(new FrameLayout(tile.getContext())));
        assertEquals(30, ControlCenterMaterials.percent(config(10, 30, 60), ControlCenterMaterials.Layer.CARD, false));
        assertEquals(60, ControlCenterMaterials.percent(config(10, 30, 60), ControlCenterMaterials.Layer.TILE, false));
        assertEquals(100, ControlCenterMaterials.percent(config(0, 0, 0), ControlCenterMaterials.Layer.TILE, true));
        assertEquals(100, ControlCenterMaterials.percent(config(0, 0, 0).withEnabled(false), ControlCenterMaterials.Layer.TILE, false));
    }

    @Test public void alphaDoesNotAccumulateAndForegroundAnimationIsNeverModified() {
        QSTileItemIconView tile = new QSTileItemIconView();
        tile.setAlpha(0.4f); tile.setTranslationY(80); tile.setScaleX(0.7f);
        GradientDrawable surface = new GradientDrawable(); surface.setColor(Color.WHITE); surface.setAlpha(200);
        var half = config(100, 100, 50);
        ControlCenterMaterials.register(tile, surface, ControlCenterMaterials.Layer.TILE, half);
        assertEquals(100, surface.getAlpha());
        ControlCenterMaterials.register(tile, surface, ControlCenterMaterials.Layer.TILE, half);
        ControlCenterMaterials.refresh(half);
        assertEquals(100, surface.getAlpha());
        int update = ControlCenterMaterials.drawableAlpha(surface, 80, half);
        assertEquals(40, update);
        surface.setAlpha(update);
        ControlCenterMaterials.refresh(config(100, 100, 0));
        assertEquals(0, surface.getAlpha());
        ControlCenterMaterials.refresh(config(100, 100, 100));
        assertEquals(80, surface.getAlpha());
        assertEquals(0.4f, tile.getAlpha(), 0f);
        assertEquals(80f, tile.getTranslationY(), 0f);
        assertEquals(0.7f, tile.getScaleX(), 0f);
    }

    @Test public void nativeBlendColorsAreCopiedAndScaledWithoutChangingTheSharedToken() {
        QSTileItemIconView tile = new QSTileItemIconView();
        ArrayList<Point> token = new ArrayList<>(List.of(new Point(0x804080c0, 3)));
        Object[] args = {token};
        Object[] modified = ControlCenterMaterials.nativeArguments(tile, "setMiBackgroundBlendColors", args, config(100, 100, 50), false);
        @SuppressWarnings("unchecked") var colors = (ArrayList<Point>) modified[0];
        assertEquals(0x404080c0, colors.get(0).x);
        assertEquals(0x804080c0, token.get(0).x);
        assertEquals(3, colors.get(0).y);
        assertSame(args, ControlCenterMaterials.nativeArguments(new View(tile.getContext()), "setMiBackgroundBlendColors", args, config(0, 0, 0), false));
    }

    @Test public void perTileBlurUsesNativeBackgroundRadiusAndRestoresNativeDefaults() {
        QSTileItemIconView tile = new QSTileItemIconView();
        ControlCenterMaterials.nativeArguments(tile, "setMiViewBlurMode", new Object[] {1}, config(100, 100, 50), false);
        ControlCenterMaterials.updateNativeSurface(tile, config(100, 100, 50), false);
        assertEquals(1, tile.backgroundMode);
        assertEquals(138, tile.radius);
        ControlCenterMaterials.refresh(config(100, 100, 0));
        assertEquals(0, tile.viewMode);
        assertEquals(0, tile.backgroundMode);
        assertEquals(0, tile.radius);
        ControlCenterMaterials.refresh(config(100, 100, 100));
        assertEquals(1, tile.viewMode);
        assertEquals(2, tile.backgroundMode);
        assertEquals(90, tile.radius);
    }

    @Test public void panelRatioRetainsNativeOpenCloseProgressAndCanRestoreImmediately() {
        WindowController controller = new WindowController();
        assertEquals(0.3f, ControlCenterMaterials.windowRatio(controller, 0.6f, config(50, 80, 90), false), 0.001f);
        ControlCenterMaterials.refresh(config(25, 80, 90));
        assertEquals(0.15f, controller.ratio, 0.001f);
        ControlCenterMaterials.refresh(config(25, 80, 90).withEnabled(false));
        assertEquals(0.6f, controller.ratio, 0.001f);
        assertEquals(0f, ControlCenterMaterials.windowRatio(controller, 0f, config(50, 80, 90), false), 0f);
        assertEquals(0.6f, ControlCenterMaterials.windowRatio(controller, 0.6f, config(50, 80, 90), true), 0f);
    }

    @Test public void nativeThemeChangesUpdateTheRestorableRadiusWhileAnOverrideIsActive() {
        QSTileItemIconView tile = new QSTileItemIconView();
        var half = config(100, 100, 50);
        ControlCenterMaterials.nativeArguments(tile, "setMiViewBlurMode", new Object[] {1}, half, false);
        ControlCenterMaterials.updateNativeSurface(tile, half, false);
        assertEquals(138, tile.radius);
        Object[] request = ControlCenterMaterials.nativeArguments(tile, "setMiBackgroundBlurRadius", new Object[] {220}, half, false);
        assertEquals(110, request[0]);
        ControlCenterMaterials.nativeArguments(tile, "setMiBackgroundBlurMode", new Object[] {3}, half, false);
        ControlCenterMaterials.refresh(config(100, 100, 100));
        assertEquals(220, tile.radius);
        assertEquals(3, tile.backgroundMode);
    }

    @Test public void moduleOwnedContainerRequestsRealBackdropBlurAndRestoresWhenDisabled() {
        QSTileItemIconView surface = new QSTileItemIconView();
        surface.backgroundMode = 0; surface.radius = 0; surface.viewMode = 0;
        ControlCenterMaterials.registerOwnedContainer(surface, config(100, 100, 100));
        assertEquals(2, surface.viewMode);
        assertEquals(275, surface.radius);
        ControlCenterMaterials.refresh(config(100, 100, 25));
        assertEquals(69, surface.radius);
        ControlCenterMaterials.refresh(config(100, 100, 0));
        assertEquals(0, surface.radius);
        assertEquals(0, surface.viewMode);
        ControlCenterMaterials.refresh(config(100, 100, 100).withEnabled(false));
        assertEquals(0, surface.backgroundMode);
        assertEquals(0, surface.radius);
        assertEquals(0, surface.viewMode);
    }

    @Test public void disabledOwnedContainerStaysDisabledAcrossRepeatedRefreshes() {
        QSTileItemIconView surface = new QSTileItemIconView();
        surface.backgroundMode = 0; surface.radius = 0; surface.viewMode = 0;
        var disabled = config(100, 100, 100).withEnabled(false);
        ControlCenterMaterials.registerOwnedContainer(surface, disabled);
        for (int i = 0; i < 3; i++) {
            ControlCenterMaterials.refresh(disabled);
            assertEquals(0, surface.viewMode);
            assertEquals(0, surface.backgroundMode);
            assertEquals(0, surface.radius);
        }
        ControlCenterMaterials.refresh(config(100, 100, 50));
        assertEquals(138, surface.radius);
        for (int i = 0; i < 3; i++) {
            ControlCenterMaterials.refresh(disabled);
            assertEquals(0, surface.viewMode);
            assertEquals(0, surface.radius);
        }
    }

    @Test public void nativeRequestsBeforeViewModeRestoreTheirUnscaledValues() {
        QSTileItemIconView tile = new QSTileItemIconView();
        var half = config(100, 100, 50);
        Object[] radius = ControlCenterMaterials.nativeArguments(tile, "setMiBackgroundBlurRadius", new Object[] {220}, half, false);
        tile.setMiBackgroundBlurRadius((Integer) radius[0]);
        Object[] mode = ControlCenterMaterials.nativeArguments(tile, "setMiBackgroundBlurMode", new Object[] {3}, half, false);
        tile.setMiBackgroundBlurMode((Integer) mode[0]);
        ControlCenterMaterials.nativeArguments(tile, "setMiViewBlurMode", new Object[] {1}, half, false);
        ControlCenterMaterials.updateNativeSurface(tile, half, false);
        assertEquals(138, tile.radius);
        ControlCenterMaterials.refresh(config(100, 100, 100));
        assertEquals(220, tile.radius);
        assertEquals(3, tile.backgroundMode);
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void transparentPreviewKeepsGlyphPixelsAndSliderFillVisible() {
        var context = RuntimeEnvironment.getApplication();
        ControlCenterCardView tile = new ControlCenterCardView(context);
        tile.bind(new ControlCenterCardView.Model("wifi", "", "WLAN", null, false, false, false, false,
                ControlCenterLayoutPlan.Shape.CIRCLE, 24, 100));
        tile.setMaterialStrength(100, 0);
        android.graphics.Bitmap bitmap = render(tile);
        assertEquals(0, Color.alpha(bitmap.getPixel(50, 5)));
        int painted = 0;
        for (int y = 0; y < 100; y++) for (int x = 0; x < 100; x++) if (Color.alpha(bitmap.getPixel(x, y)) > 0) painted++;
        assertTrue(painted > 50 && painted < 3000);
        bitmap.recycle();
        ControlCenterCardView slider = new ControlCenterCardView(context);
        slider.bind(new ControlCenterCardView.Model(ControlCenterComponentSpec.VOLUME, "", "音量", null,
                false, false, false, false, ControlCenterLayoutPlan.Shape.RECTANGLE, 20, 100));
        slider.setMaterialStrength(0, 100);
        bitmap = render(slider);
        assertEquals(0, Color.alpha(bitmap.getPixel(50, 5)));
        assertTrue(Color.alpha(bitmap.getPixel(50, 92)) > 200);
        bitmap.recycle();
    }
    private android.graphics.Bitmap render(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 100, 100);
        var bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(bitmap));
        return bitmap;
    }

    public static class QSCardItemView extends FrameLayout { QSCardItemView() { super(RuntimeEnvironment.getApplication()); } }
    public static class QSTileItemIconView extends FrameLayout {
        public int backgroundMode = 2, radius = 90, viewMode = 1;
        QSTileItemIconView() { super(RuntimeEnvironment.getApplication()); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { }
    }
    public static class WindowController {
        float ratio;
        public void setBlurRatio(float value) { ratio = value; }
    }
}

package com.xtjm.fusionstatusbar;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.ColorFilter;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
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
public class ControlCenterSurfaceMaterialsTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }
    private ControlCenterConfig config(ControlCenterSurface surface) {
        return ControlCenterConfig.defaults().withEnabled(true).withSurface(surface);
    }

    @Test public void softGlassKeepsDarkTintWhileCardAndTileBlurChange() {
        assertTrue(Color.luminance(ControlCenterSurface.SOFT_GLASS.color) < 0.10f);
        QSCardItemView card = new QSCardItemView();
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        var low = config(ControlCenterSurface.SOFT_GLASS).withBlur(25, 20, 65);
        ControlCenterMaterials.register(card, background, ControlCenterMaterials.Layer.CARD, low);
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, background.getColor().getDefaultColor());
        assertEquals("Blur strength must not fade the glass tint", 255, background.getAlpha());

        QSTileItemIconView tile = new QSTileItemIconView();
        ArrayList<Point> nativeColors = new ArrayList<>(List.of(new Point(0xffeeeeee, 3)));
        Object[] tint = ControlCenterMaterials.nativeArguments(tile,
                "setMiBackgroundBlendColors", new Object[] {nativeColors}, low, false);
        assertEquals(ControlCenterSurface.SOFT_GLASS.color,
                ((ArrayList<Point>) tint[0]).get(0).x);
        ControlCenterMaterials.nativeArguments(tile, "setMiViewBlurMode", new Object[] {1}, low, false);
        ControlCenterMaterials.updateNativeSurface(tile, low, false);
        assertEquals(Math.round(275 * 0.65f), tile.radius);
    }

    @Test public void globalTintUsesSourceOverInsteadOfOemScreenBlend() {
        QSTileItemIconView tile = new QSTileItemIconView();
        ArrayList<Point> requested = new ArrayList<>(List.of(
                new Point(0xccffffff, 14), new Point(0x80ffffff, 5)));
        Object[] actual = ControlCenterMaterials.nativeArguments(tile, "setMiBackgroundBlendColors",
                new Object[] {requested}, config(ControlCenterSurface.SOFT_GLASS), false);
        ArrayList<Point> colors = (ArrayList<Point>) actual[0];
        assertEquals("A global container tint must not replay the OEM lightening chain", 1, colors.size());
        assertEquals(3, colors.get(0).y);
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, colors.get(0).x);
        assertEquals(14, requested.get(0).y);
    }

    @Test public void sliderProgressKeepsItsFillAfterLateDrawableAndBlurUpdates() {
        ToggleSliderView slider = new ToggleSliderView();
        NativeProgressView fill = new NativeProgressView();
        fill.setId(android.R.id.progress);
        ColorDrawable white = new ColorDrawable(Color.WHITE);
        fill.setBackground(white);
        slider.addView(fill);
        var settings = config(ControlCenterSurface.SOFT_GLASS).withBlur(35, 20, 20);

        ControlCenterMaterials.register(fill, white, ControlCenterMaterials.layer(fill), settings);
        assertEquals("A late slider fill drawable must retain its native white",
                Color.WHITE, white.getColor());
        ArrayList<Point> nativeColors = new ArrayList<>(List.of(new Point(Color.WHITE, 3)));
        Object[] request = {nativeColors};
        assertSame("The native fill blend must not inherit the global container tint", request,
                ControlCenterMaterials.nativeArguments(fill, "setMiBackgroundBlendColors",
                        request, settings, false));
        ControlCenterMaterials.nativeArguments(fill, "setMiViewBlurMode", new Object[] {1}, settings, false);
        ControlCenterMaterials.updateNativeSurface(fill, settings, false);
        assertEquals(90, fill.radius);
        ControlCenterMaterials.refresh(settings.withBlur(80, 5, 5));
        assertEquals(Color.WHITE, white.getColor());
        assertEquals(90, fill.radius);
    }

    @Test public void panelColorFilterAndTintAreClearedAndRestoredAroundGlobalSurface() {
        QSCardItemView card = new QSCardItemView();
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        PorterDuffColorFilter filter = new PorterDuffColorFilter(Color.MAGENTA, PorterDuff.Mode.SRC_IN);
        background.setColorFilter(filter);

        var dark = config(ControlCenterSurface.DARK);
        ControlCenterMaterials.register(card, background, ControlCenterMaterials.Layer.CARD, dark);
        assertNull("A global surface must not retain an OEM filter over its tint", background.getColorFilter());

        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertSame(filter, background.getColorFilter());
    }

    @Test public void latePanelFilterIsSuppressedButRestoredWhenSurfaceIsDisabled() {
        QSCardItemView card = new QSCardItemView();
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        var dark = config(ControlCenterSurface.DARK);
        ControlCenterMaterials.register(card, background, ControlCenterMaterials.Layer.CARD, dark);

        PorterDuffColorFilter late = new PorterDuffColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
        ColorFilter ignored = ControlCenterMaterials.drawableColorFilter(background, late, dark);
        background.setColorFilter(ignored);
        ControlCenterMaterials.drawableUpdated(background, dark);
        assertNull(background.getColorFilter());

        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertSame(late, background.getColorFilter());
    }

    @Test public void unregisteredIconFilterPassesThroughUntouched() {
        GradientDrawable icon = new GradientDrawable();
        PorterDuffColorFilter filter = new PorterDuffColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN);
        assertSame(filter, ControlCenterMaterials.drawableColorFilter(icon, filter,
                config(ControlCenterSurface.DARK)));
        icon.setColorFilter(filter);
        assertSame(filter, icon.getColorFilter());
    }

    @Test public void newStylesComposeWithIndependentWindowCardAndTileBlurStrengths() {
        for (ControlCenterSurface style : new ControlCenterSurface[] {
                ControlCenterSurface.GLASS, ControlCenterSurface.LIGHT,
                ControlCenterSurface.GRAPHITE, ControlCenterSurface.ICE,
                ControlCenterSurface.MINT}) {
            var settings = config(style).withBlur(35, 60, 85);
            var card = new QSCardItemView();
            var background = new GradientDrawable();
            background.setColor(Color.WHITE);
            ControlCenterMaterials.register(card, background, ControlCenterMaterials.Layer.CARD, settings);
            assertEquals(style.color, background.getColor().getDefaultColor());
            assertEquals(Math.round(255 * 0.60f), background.getAlpha());
            var tile = new QSTileItemIconView();
            ControlCenterMaterials.nativeArguments(tile, "setMiViewBlurMode", new Object[] {1}, settings, false);
            ControlCenterMaterials.updateNativeSurface(tile, settings, false);
            assertEquals(Math.round(275 * 0.85f), tile.radius);
            assertEquals(0.28f, ControlCenterMaterials.windowRatio(new Object(), 0.8f, settings, false), 0.001f);
            ControlCenterMaterials.refresh(settings.withSurface(ControlCenterSurface.DEFAULT));
            assertEquals(Color.WHITE, background.getColor().getDefaultColor());
        }
    }

    @Test public void lightSurfaceRestoresNativeNeutralAndColoredText() {
        var root = new QSCardItemView();
        var white = new android.widget.TextView(root.getContext());
        white.setTextColor(Color.WHITE);
        var blue = new android.widget.TextView(root.getContext());
        blue.setTextColor(Color.BLUE);
        root.addView(white); root.addView(blue);
        ControlCenterMaterials.registerRoot(root, config(ControlCenterSurface.LIGHT));
        assertTrue(Color.red(white.getCurrentTextColor()) < 80);
        assertEquals(Color.BLUE, blue.getCurrentTextColor());
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertEquals(Color.WHITE, white.getCurrentTextColor());
        assertEquals(Color.BLUE, blue.getCurrentTextColor());
    }

    @Test public void everyGlobalStyleRestoresLocalColorAndDoesNotChangeForegroundProperties() {
        QSCardItemView card = new QSCardItemView();
        card.setAlpha(0.35f); card.setScaleX(0.7f); card.setTranslationY(12);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x70abcdef); background.setAlpha(180);
        ControlCenterMaterials.register(card, background, ControlCenterMaterials.Layer.CARD, config(ControlCenterSurface.DARK));
        assertEquals(ControlCenterSurface.DARK.color, background.getColor().getDefaultColor());
        for (ControlCenterSurface surface : new ControlCenterSurface[] {ControlCenterSurface.CLEAR, ControlCenterSurface.FROSTED, ControlCenterSurface.DARK}) {
            ControlCenterMaterials.refresh(config(surface));
            assertEquals(surface.color, background.getColor().getDefaultColor());
            assertEquals(180, background.getAlpha());
        }
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertEquals(0x70abcdef, background.getColor().getDefaultColor());
        ControlCenterMaterials.refresh(config(ControlCenterSurface.CLEAR));
        ControlCenterMaterials.refresh(config(ControlCenterSurface.CLEAR).withEnabled(false));
        assertEquals(0x70abcdef, background.getColor().getDefaultColor());
        assertEquals(0.35f, card.getAlpha(), 0); assertEquals(0.7f, card.getScaleX(), 0);
        assertEquals(12f, card.getTranslationY(), 0);
    }

    @Test public void nativeColorChangesRemainRestorableWhileGlobalStyleIsSelected() {
        QSCardItemView card = new QSCardItemView();
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE);
        ControlCenterMaterials.register(card, background, ControlCenterMaterials.Layer.CARD, config(ControlCenterSurface.DARK));
        background.setColor(ControlCenterMaterials.drawableColor(background, Color.RED, config(ControlCenterSurface.DARK)));
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertEquals(Color.RED, background.getColor().getDefaultColor());
        int[] nativeGradient = {Color.RED, Color.BLUE};
        background.setColors(ControlCenterMaterials.drawableColors(background, nativeGradient, config(ControlCenterSurface.FROSTED)));
        assertArrayEquals(new int[] {ControlCenterSurface.FROSTED.color, ControlCenterSurface.FROSTED.color}, background.getColors());
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertArrayEquals(nativeGradient, background.getColors());
    }

    @Test public void nestedColorSetterDoesNotStoreTheAlreadyTransformedColor() throws Throwable {
        QSCardItemView card = new QSCardItemView();
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE);
        ControlCenterMaterials.register(card, background, ControlCenterMaterials.Layer.CARD, config(ControlCenterSurface.DARK));
        int transformed = ControlCenterMaterials.drawableColor(background, Color.RED, config(ControlCenterSurface.DARK));
        ControlCenterMaterials.applyHook(() -> {
            background.setColor(ControlCenterMaterials.drawableColor(background,
                    android.content.res.ColorStateList.valueOf(transformed), config(ControlCenterSurface.DARK)));
            return null;
        });
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertEquals(Color.RED, background.getColor().getDefaultColor());
    }

    @Test public void existingGradientUsesTheGlobalTintAndRestoresAllNativeStops() {
        var card = new QSCardItemView();
        int[] original = {Color.RED, Color.GREEN, Color.BLUE};
        var background = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, original);
        ControlCenterMaterials.register(card, background, ControlCenterMaterials.Layer.CARD, config(ControlCenterSurface.DARK));
        assertArrayEquals(new int[] {ControlCenterSurface.DARK.color, ControlCenterSurface.DARK.color,
                ControlCenterSurface.DARK.color}, background.getColors());
        ControlCenterMaterials.refresh(config(ControlCenterSurface.CLEAR));
        assertArrayEquals(new int[] {0, 0, 0}, background.getColors());
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertArrayEquals(original, background.getColors());
    }

    @Test public void layeredSliderSurfacePreservesProgressAndGlyphLayers() {
        QSCardItemView card = new QSCardItemView();
        ColorDrawable track = new ColorDrawable(0x80abcdef);
        ColorDrawable fill = new ColorDrawable(Color.WHITE);
        ColorDrawable secondary = new ColorDrawable(Color.BLUE);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {track, fill, secondary});
        layers.setId(0, android.R.id.background); layers.setId(1, android.R.id.progress);
        layers.setId(2, android.R.id.secondaryProgress);
        ControlCenterMaterials.register(card, layers, ControlCenterMaterials.Layer.CARD, config(ControlCenterSurface.CLEAR));
        assertEquals(0, track.getAlpha());
        assertEquals(Color.WHITE, fill.getColor()); assertEquals(Color.BLUE, secondary.getColor());
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DARK));
        assertEquals(ControlCenterSurface.DARK.color, track.getColor());
        for (int i = 0; i < 3; i++) ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertEquals(0x80abcdef, track.getColor());
        assertEquals(0x80, track.getAlpha());
    }

    @Test public void unlabelledLayerDrawableNeverTintsAnUnknownSliderFill() {
        var slider = new ToggleSliderView();
        ColorDrawable track = new ColorDrawable(0xff333333);
        ColorDrawable fill = new ColorDrawable(Color.WHITE);
        LayerDrawable unknown = new LayerDrawable(new Drawable[] {track, fill});
        slider.setBackground(unknown);
        ControlCenterMaterials.registerRoot(slider, config(ControlCenterSurface.FROSTED));
        assertEquals("Unknown compound layers may include the live progress fill",
                Color.WHITE, fill.getColor());
    }

    @Test public void unnamedPanelLayersReceiveGlobalSurfaceAndRestoreNativeColors() {
        var card = new QSCardItemView();
        GradientDrawable first = new GradientDrawable();
        first.setColor(Color.WHITE);
        GradientDrawable second = new GradientDrawable();
        second.setColor(0xffd0d4da);
        LayerDrawable background = new LayerDrawable(new Drawable[] {first, second});
        card.setBackground(background);

        var glass = config(ControlCenterSurface.SOFT_GLASS);
        ControlCenterMaterials.registerRoot(card, glass);
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, first.getColor().getDefaultColor());
        assertEquals(ControlCenterSurface.SOFT_GLASS.color, second.getColor().getDefaultColor());

        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertEquals(Color.WHITE, first.getColor().getDefaultColor());
        assertEquals(0xffd0d4da, second.getColor().getDefaultColor());
    }

    @Test public void panelColorFilterIsClearedForGlobalSurfaceAndRestoredOnDisable() {
        var card = new QSCardItemView();
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        PorterDuffColorFilter nativeFilter = new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        background.setColorFilter(nativeFilter);

        ControlCenterMaterials.register(card, background, ControlCenterMaterials.Layer.CARD,
                config(ControlCenterSurface.SOFT_GLASS));
        assertNull("A panel filter must not override the global dark material", background.getColorFilter());

        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertSame(nativeFilter, background.getColorFilter());

        PorterDuffColorFilter lateFilter = new PorterDuffColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN);
        background.setColorFilter(ControlCenterMaterials.drawableColorFilter(background, lateFilter,
                config(ControlCenterSurface.SOFT_GLASS)));
        ControlCenterMaterials.drawableUpdated(background, config(ControlCenterSurface.SOFT_GLASS));
        assertNull(background.getColorFilter());
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertSame(lateFilter, background.getColorFilter());
    }

    @Test public void colorDrawableOpacityAndAnimationDoNotAccumulateAcrossRefreshes() {
        QSCardItemView card = new QSCardItemView();
        ColorDrawable background = new ColorDrawable(0x80abcdef);
        var half = config(ControlCenterSurface.DARK).withBlur(100, 50, 100);
        ControlCenterMaterials.register(card, background, ControlCenterMaterials.Layer.CARD, half);
        assertEquals(100, background.getAlpha());
        for (int i = 0; i < 3; i++) ControlCenterMaterials.refresh(half);
        assertEquals(100, background.getAlpha());
        background.setAlpha(ControlCenterMaterials.drawableAlpha(background, 128, half));
        for (int i = 0; i < 3; i++) ControlCenterMaterials.refresh(half);
        assertEquals(50, background.getAlpha());
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertEquals(64, background.getAlpha());
        background.setColor(ControlCenterMaterials.drawableColor(background, Color.RED, half));
        ControlCenterMaterials.drawableUpdated(background, half);
        assertEquals(100, background.getAlpha());
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertEquals(Color.RED, background.getColor());
    }

    @Test public void nativeOnlySurfaceKeepsBackdropBlurAndRestoresRequestedBlendColors() {
        QSTileItemIconView tile = new QSTileItemIconView();
        ArrayList<Point> nativeColors = new ArrayList<>(List.of(new Point(0x8056789a, 3), new Point(0x809abcde, 5)));
        ControlCenterMaterials.nativeArguments(tile, "setMiBackgroundBlendColors", new Object[] {nativeColors}, config(ControlCenterSurface.FROSTED), false);
        ControlCenterMaterials.nativeArguments(tile, "setMiViewBlurMode", new Object[] {1}, config(ControlCenterSurface.FROSTED), false);
        ControlCenterMaterials.updateNativeSurface(tile, config(ControlCenterSurface.FROSTED), false);
        assertEquals("Keep the OEM rectangle mask for a native blur surface", 1, tile.viewMode);
        assertEquals(1, tile.backgroundMode); assertEquals(275, tile.radius);
        assertEquals(ControlCenterSurface.FROSTED.color, tile.colors.get(0).x);
        assertEquals(1, tile.colors.size());
        assertEquals(3, tile.colors.get(0).y);
        assertEquals(0x8056789a, nativeColors.get(0).x);
        ControlCenterMaterials.refresh(config(ControlCenterSurface.CLEAR));
        assertEquals(0, tile.viewMode); assertEquals(0, tile.radius);
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DARK));
        assertEquals(ControlCenterSurface.DARK.color, tile.colors.get(0).x);
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertEquals(1, tile.viewMode); assertEquals(2, tile.backgroundMode); assertEquals(90, tile.radius);
        assertEquals(0x8056789a, tile.colors.get(0).x); assertEquals(0x809abcde, tile.colors.get(1).x);
    }

    @Test public void nativeOnlySurfaceWithoutBlendRequestCanRestoreAndEditingBypassesStyles() {
        QSTileItemIconView tile = new QSTileItemIconView();
        var clear = config(ControlCenterSurface.CLEAR);
        Object[] requested = {2};
        assertSame(requested, ControlCenterMaterials.nativeArguments(tile, "setMiViewBlurMode", requested, clear, true));
        ControlCenterMaterials.nativeArguments(tile, "setMiViewBlurMode", new Object[] {1}, config(ControlCenterSurface.DARK), false);
        ControlCenterMaterials.updateNativeSurface(tile, config(ControlCenterSurface.DARK), false);
        assertEquals(ControlCenterSurface.DARK.color, tile.colors.get(0).x);
        ControlCenterMaterials.refresh(clear.withEnabled(false));
        assertTrue(tile.colors.isEmpty());
        assertEquals(1, tile.viewMode); assertEquals(90, tile.radius);
    }

    @Test public void treeRegistrationPreservesNestedCardIconAndItsLabelColors() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        QSCardItemView card = new QSCardItemView(); root.addView(card);
        FrameLayout wrapper = new FrameLayout(root.getContext()); card.addView(wrapper);
        QSTileItemIconView tile = new QSTileItemIconView(); wrapper.addView(tile);
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE);
        tile.setBackground(background);
        TextView label = new TextView(root.getContext()); label.setTextColor(0xcc000000); tile.addView(label);
        TextView accent = new TextView(root.getContext()); accent.setTextColor(Color.BLUE); tile.addView(accent);
        ControlCenterMaterials.registerRoot(root, config(ControlCenterSurface.DARK));
        assertEquals(Color.WHITE, background.getColor().getDefaultColor());
        assertEquals(0xcc000000, label.getCurrentTextColor());
        assertEquals(Color.BLUE, accent.getCurrentTextColor());
        label.setTextColor(ControlCenterMaterials.textColor(label, 0x88000000, config(ControlCenterSurface.CLEAR)));
        assertEquals(0x88000000, label.getCurrentTextColor());
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DEFAULT));
        assertEquals(Color.WHITE, background.getColor().getDefaultColor());
        assertEquals(0x88000000, label.getCurrentTextColor());
        assertEquals(Color.BLUE, accent.getCurrentTextColor());
    }

    @Test public void nativeCardIconKeepsItsBlendWhileCompactTileUsesGlobalSurface() {
        QSCardItemView card = new QSCardItemView();
        QSTileItemIconView cardIcon = new QSTileItemIconView();
        card.addView(cardIcon);
        ArrayList<Point> original = new ArrayList<>(List.of(new Point(0xff112233, 3)));
        Object[] cardArgs = {original};
        assertSame(cardArgs, ControlCenterMaterials.nativeArguments(cardIcon,
                "setMiBackgroundBlendColors", cardArgs, config(ControlCenterSurface.DARK), false));

        QSTileItemView compact = new QSTileItemView();
        QSTileItemIconView compactIcon = new QSTileItemIconView();
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        compactIcon.setBackground(background);
        compact.addView(compactIcon);
        ControlCenterMaterials.registerRoot(compact, config(ControlCenterSurface.DARK));
        assertEquals(ControlCenterSurface.DARK.color, background.getColor().getDefaultColor());
        Object[] tileArgs = ControlCenterMaterials.nativeArguments(compactIcon,
                "setMiBackgroundBlendColors", new Object[] {original},
                config(ControlCenterSurface.DARK), false);
        assertEquals(ControlCenterSurface.DARK.color, ((ArrayList<Point>) tileArgs[0]).get(0).x);
    }

    @Test public void iconTintAppliedBeforeAttachmentIsRestoredInsideCard() {
        QSTileItemIconView icon = new QSTileItemIconView();
        ArrayList<Point> nativeColors = new ArrayList<>(List.of(new Point(0xff112233, 3)));
        Object[] tinted = ControlCenterMaterials.nativeArguments(icon,
                "setMiBackgroundBlendColors", new Object[] {nativeColors},
                config(ControlCenterSurface.DARK), false);
        icon.setMiBackgroundBlendColors((ArrayList<Point>) tinted[0]);
        assertEquals(ControlCenterSurface.DARK.color, icon.colors.get(0).x);

        QSCardItemView card = new QSCardItemView();
        card.addView(icon);
        ControlCenterMaterials.registerRoot(card, config(ControlCenterSurface.DARK));
        assertEquals(0xff112233, icon.colors.get(0).x);
    }

    @Test public void refreshedIconTintIsRestoredAfterCardAttachment() {
        QSTileItemIconView icon = new QSTileItemIconView();
        ArrayList<Point> nativeColors = new ArrayList<>(List.of(new Point(0xff112233, 3)));
        var original = config(ControlCenterSurface.DEFAULT);
        Object[] request = ControlCenterMaterials.nativeArguments(icon,
                "setMiBackgroundBlendColors", new Object[] {nativeColors}, original, false);
        icon.setMiBackgroundBlendColors((ArrayList<Point>) request[0]);
        ControlCenterMaterials.refresh(config(ControlCenterSurface.DARK));
        assertEquals(ControlCenterSurface.DARK.color, icon.colors.get(0).x);

        QSCardItemView card = new QSCardItemView();
        card.addView(icon);
        ControlCenterMaterials.registerRoot(card, config(ControlCenterSurface.DARK));
        assertEquals(0xff112233, icon.colors.get(0).x);
    }

    public static class QSCardItemView extends FrameLayout {
        QSCardItemView() { super(RuntimeEnvironment.getApplication()); }
    }
    public static class QSTileItemView extends FrameLayout {
        QSTileItemView() { super(RuntimeEnvironment.getApplication()); }
    }
    public static class ToggleSliderView extends FrameLayout {
        ToggleSliderView() { super(RuntimeEnvironment.getApplication()); }
    }
    public static class NativeProgressView extends View {
        int viewMode = 1, backgroundMode = 2, radius = 90;
        ArrayList<Point> colors = new ArrayList<>();
        NativeProgressView() { super(RuntimeEnvironment.getApplication()); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = value; }
    }
    public static class QSTileItemIconView extends FrameLayout {
        int viewMode = 1, backgroundMode = 2, radius = 90;
        ArrayList<Point> colors = new ArrayList<>();
        QSTileItemIconView() { super(RuntimeEnvironment.getApplication()); }
        public int getMiBackgroundBlurMode() { return backgroundMode; }
        public int getMiBackgroundBlurRadius() { return radius; }
        public void setMiBackgroundBlurMode(int value) { backgroundMode = value; }
        public void setMiBackgroundBlurRadius(int value) { radius = value; }
        public void setMiViewBlurMode(int value) { viewMode = value; }
        public void setMiBackgroundBlendColors(ArrayList<Point> value) { colors = value; }
    }
}

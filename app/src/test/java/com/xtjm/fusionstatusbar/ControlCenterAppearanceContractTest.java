package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.After;
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
public class ControlCenterAppearanceContractTest {
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void independentRadiusZeroFollowsTheConfiguredDefaultIncludingSquare() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        for (int radius : new int[] {0, 28, 32}) {
            var grid = new ControlCenterRuntimeGrid.GridView(activity, 4, 14, radius, 0, 1);
            var card = new QSCardItemView(activity);
            card.setBackgroundColor(Color.WHITE);
            grid.addTile(card, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1), 0);
            grid.applyMaterials(ControlCenterConfig.defaults().withEnabled(true).withCornerRadius(radius));
            layout(grid, 400, 200);
            draw(grid).recycle();
            Outline outline = new Outline();
            card.getOutlineProvider().getOutline(card, outline);
            assertEquals("The later material pass must not replace the global radius with 18%", radius,
                    outline.getRadius(), 0.01f);
        }
    }

    @Test public void circleSurfaceUsesDistinctStateColorsAndTheConfiguredOpacity() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        for (int state : new int[] {1, 2}) for (int strength : new int[] {0, 25, 100}) {
            QSTileItemView tile = new QSTileItemView(activity);
            tile.state.state = state;
            ImageView image = new ImageView(activity);
            LayerDrawable drawable = new LayerDrawable(new Drawable[] {
                    new ColorDrawable(Color.WHITE), new ColorDrawable(Color.BLUE)});
            drawable.setLayerInset(1, 40, 40, 40, 40);
            image.setImageDrawable(drawable);
            tile.addView(image, new FrameLayout.LayoutParams(100, 100));
            layout(tile, 100, 100);
            var settings = ControlCenterConfig.defaults().withEnabled(true).withSurface(ControlCenterSurface.DARK)
                    .withBlur(100, 100, strength);
            ControlCenterMaterials.registerGridTileShape(tile, ControlCenterLayoutPlan.Item.tile("bt", 0, 0, 1, 1)
                    .withShape(ControlCenterLayoutPlan.Shape.CIRCLE, 0));
            ControlCenterMaterials.syncTileView(tile, settings);
            Bitmap pixels = draw(image);
            assertEquals("Selected material opacity must agree with the editor and ordinary tiles",
                    Math.round((state == 2 ? 255f : 200f) * strength / 100), Color.alpha(pixels.getPixel(50, 20)));
            if (strength > 0) assertEquals("State color stays distinct after opacity scaling", state == 2,
                    Color.red(pixels.getPixel(50, 20)) > 200);
            assertEquals(Color.BLUE, pixels.getPixel(50, 50));
            pixels.recycle();
        }
    }

    @Test public void legacyGlobalRadiusHookCannotOverrideAnExplicitCanvasShape() throws Exception {
        Field config = SystemUiHooks.class.getDeclaredField("currentConfig"); config.setAccessible(true);
        Object previous = config.get(null);
        try {
            config.set(null, FusionConfig.defaults().withControlCenter(
                    ControlCenterImportedConfigRegressionTest.loadedSettings()));
            View view = new View(org.robolectric.RuntimeEnvironment.getApplication());
            Object chain = Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {io.github.libxposed.api.XposedInterface.Chain.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getThisObject")) return view;
                        if (method.getName().equals("proceed")) return 50f;
                        if (method.getName().equals("getArgs")) return List.of();
                        return null;
                    });
            Method hook = SystemUiHooks.class.getDeclaredMethod("controlCenterPluginCornerRadius",
                    io.github.libxposed.api.XposedInterface.Chain.class);
            hook.setAccessible(true);
            assertEquals(50f, (Float) hook.invoke(null, chain), 0f);
        } finally { config.set(null, previous); }
    }

    @Test public void cardAndNativeBlurUseTheSameStateColorsAndReadableText() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        QSCardItemView card = new QSCardItemView(activity);
        card.setBackgroundColor(Color.GRAY);
        android.widget.TextView text = new android.widget.TextView(activity);
        text.setTextColor(Color.WHITE);
        card.addView(text);
        var settings = ControlCenterConfig.defaults().withEnabled(true).withSurface(ControlCenterSurface.SOFT_GLASS);
        for (int state : new int[] {1, 2, 1}) {
            card.state.state = state;
            ControlCenterMaterials.registerRoot(card, settings);
            int expected = ControlCenterSurface.SOFT_GLASS.stateColor(state == 2);
            assertEquals(expected, ((ColorDrawable) card.getBackground()).getColor());
            var original = new java.util.ArrayList<>(List.of(new android.graphics.Point(Color.GRAY, 3)));
            Object[] args = ControlCenterMaterials.nativeArguments(card, "setMiBackgroundBlendColors",
                    new Object[] {original}, settings, false);
            assertEquals(expected, ((android.graphics.Point) ((java.util.ArrayList<?>) args[0]).get(0)).x);
            assertEquals(state == 2, Color.red(text.getCurrentTextColor()) < 80);
        }
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }
    private static Bitmap draw(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
    static final class QSCardItemView extends FrameLayout {
        final State state = new State();
        QSCardItemView(Activity activity) { super(activity); }
    }
    static final class QSTileItemView extends FrameLayout {
        final State state = new State();
        QSTileItemView(Activity activity) { super(activity); }
    }
    static final class State { int state; }
}

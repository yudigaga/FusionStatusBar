package com.xtjm.fusionstatusbar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
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
public class ControlCenterCanvasIconTest {
    @Test public void capturedAndApplicationArtworkHaveEqualVisibleBounds() {
        Bitmap captured = coloredGlyph(128, 128, new Rect(32, 48, 96, 80));
        Bitmap application = coloredGlyph(96, 96, new Rect(0, 24, 96, 72));
        Rect capturedBounds = visibleGlyphBounds(render(card("custom(com.example/.Tile)", captured, false, false), 0, 112, 112));
        Rect applicationBounds = visibleGlyphBounds(render(card("custom(com.example/.Tile)", application, false, false), 0, 112, 112));
        assertTrue("Captured and application glyphs need equal visible width: " + capturedBounds + " / " + applicationBounds,
                Math.abs(capturedBounds.width() - applicationBounds.width()) <= 1);
        assertTrue("Source-edge clipping must not change visible height",
                Math.abs(capturedBounds.height() - applicationBounds.height()) <= 1);
        assertTrue("Normalized artwork must fill the standard icon box", capturedBounds.width() >= 30);
    }

    @Test public void capturedArtworkPreservesAspectRatioAndSourcePixels() {
        Bitmap source = coloredGlyph(64, 32, new Rect(0, 0, 64, 32));
        Bitmap original = source.copy(Bitmap.Config.ARGB_8888, false);
        ControlCenterCardView tile = card("custom(com.example/.Tile)", source, false, false);
        Bitmap result = render(tile, 0, 112, 112);
        Rect bounds = visibleGlyphBounds(result);
        assertEquals("Non-square artwork must keep its visible aspect ratio", 2f,
                bounds.width() / (float) bounds.height(), 0.15f);
        assertEquals(Color.RED, result.getPixel(bounds.left + bounds.width() / 4, bounds.centerY()));
        assertEquals(Color.BLUE, result.getPixel(bounds.left + bounds.width() * 3 / 4, bounds.centerY()));
        render(tile, 100, 112, 112);
        assertFalse(source.isRecycled());
        assertTrue("Preview normalization must preserve shared source pixels", original.sameAs(source));
    }

    @Test public void packagedAndCapturedGlyphsUseTheSameVisibleBox() {
        Bitmap captured = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Drawable icon = ControlCenterIcons.load(RuntimeEnvironment.getApplication(), R.drawable.cc_icon_nfc, Color.WHITE);
        icon.setBounds(24, 24, 104, 104);
        icon.draw(new Canvas(captured));
        Rect local = visibleGlyphBounds(render(card("nfc", null, false, false), 0, 112, 112));
        Rect nativeBounds = visibleGlyphBounds(render(card("nfc", captured, false, false), 0, 112, 112));
        assertTrue("Packaged and captured foregrounds need one visible size: " + local + " / " + nativeBounds,
                Math.abs(local.width() - nativeBounds.width()) <= 1
                        && Math.abs(local.height() - nativeBounds.height()) <= 1);
    }

    @Test public void knownCatalogSpecsNeverRenderTheMorePlaceholder() {
        for (String spec : new String[] {"controls", "satellite", "autobrightness", "screenlock",
                "night", "sync", "vibrate", "edit", "settings", "wallet"}) {
            Rect bounds = visibleGlyphBounds(render(card(spec, null, false, false), 0, 112, 112));
            assertTrue(spec + " must render a recognizable full-height symbol, not three dots: " + bounds, bounds.height() >= 18);
            assertNotEquals(spec, R.drawable.cc_icon_more_horiz, ControlCenterIcons.resource(spec));
        }
    }

    @Test public void unknownApplicationTileUsesAServicePlaceholder() {
        String spec = "custom(com.example.unavailable/.Tile)";
        Rect bounds = visibleGlyphBounds(render(card(spec, null, false, false), 0, 112, 112));
        assertTrue("Unavailable service artwork needs a generic app symbol, not more dots", bounds.height() >= 24);
        assertNotEquals(R.drawable.cc_icon_more_horiz, ControlCenterIcons.resource(spec));
    }

    @Test public void cachedWhiteBackgroundIsNotRenderedAsTheTileGlyph() {
        Bitmap invalid = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        new Canvas(invalid).drawRoundRect(1, 1, 127, 127, 24, 24, paint);
        Bitmap actual = render(card("nfc", invalid, false, false), 0, 112, 112);
        Bitmap expected = render(card("nfc", null, false, false), 0, 112, 112);
        assertTrue("A captured backing plate must fall back to an identifiable glyph", expected.sameAs(actual));
        assertFalse(invalid.isRecycled());
    }

    @Test public void themedServiceWhitePlateFallsBackToRecognizableArtwork() throws Exception {
        Bitmap source;
        try (var input = getClass().getResourceAsStream("/control-center-icons/miui-themed-empty-service-icon.png")) {
            assertNotNull("Device-exported themed service icon fixture is required", input);
            source = android.graphics.BitmapFactory.decodeStream(input);
        }
        assertNotNull(source);
        Bitmap original = source.copy(Bitmap.Config.ARGB_8888, false);
        for (String spec : new String[] {
                "custom(com.miui.mishare.connectivity/.tile.MiShareTileService)",
                "custom(com.miui.screenrecorder/.service.QuickService)",
                "custom(com.milink.service/com.milink.ui.service.MiLinkTileService)"}) {
            Bitmap actual = render(card(spec, source, false, false), 100, 112, 112);
            Bitmap fallback = render(card(spec, null, false, false), 100, 112, 112);
            assertTrue("The real themed icon contains only a backing plate and shadow, not " + spec + " artwork",
                    fallback.sameAs(actual));
        }
        assertEquals(ControlCenterIcons.ArtworkKind.INVALID, ControlCenterIcons.artworkKind(source));
        assertFalse(source.isRecycled());
        assertTrue(original.sameAs(source));
    }

    @Test public void blackGlyphFromAnAddedTileIsVisibleOnTheDarkSurface() {
        Bitmap black = glyph("bt", Color.BLACK);
        Bitmap actual = render(card("bt", black, false, false), 100, 112, 112);
        assertTrue("Monochrome native/app artwork needs readable foreground contrast", brightPixels(actual) > 60);
        assertEquals(Color.BLACK, black.getPixel(64, 64));
    }

    @Test public void neutralMultiToneArtworkKeepsItsLogoOnCanvas() {
        // A transparent margin makes a white app plate with a gray logo sparse,
        // but its neutral colors are still artwork, not a single tintable mask.
        Bitmap source = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(source);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(20, 20, 108, 108, 20, 20, paint);
        int logoColor = Color.rgb(96, 96, 96);
        paint.setColor(logoColor);
        canvas.drawCircle(64, 64, 18, paint);
        Bitmap original = source.copy(Bitmap.Config.ARGB_8888, false);
        for (boolean active : new boolean[] {false, true}) {
            Bitmap result = render(card("custom(com.example/.Tile)", source, active, false), 100, 112, 112);
            assertEquals("A neutral app logo must not be flattened into its rounded backing plate",
                    logoColor, result.getPixel(56, 56));
            assertEquals("Artwork must retain the plate as well as its contrasting logo",
                    Color.WHITE, result.getPixel(44, 56));
        }
        assertFalse(source.isRecycled());
        assertTrue(original.sameAs(source));
    }

    @Test public void activeTitleKeepsContrastWhenItsBackingIsTransparent() {
        ControlCenterCardView card = card("wifi", null, true, true);
        render(card, 0, 224, 112);
        TextView title = title(card, "WLAN");
        assertNotNull(title);
        assertTrue(Color.luminance(title.getCurrentTextColor()) > 0.7f);
        render(card, 100, 224, 112);
        assertTrue(Color.luminance(title.getCurrentTextColor()) < 0.1f);
    }

    @Test public void wrappedNativeArtworkExcludesBackgroundAndPreservesLiveBounds() {
        Drawable glyph = ControlCenterIcons.load(RuntimeEnvironment.getApplication(), R.drawable.cc_icon_nfc, Color.WHITE);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {new ColorDrawable(Color.MAGENTA), glyph});
        layers.setLayerSize(1, 128, 128);
        layers.setLayerGravity(1, Gravity.CENTER);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[0], new InsetDrawable(layers, 8));
        ImageView image = new ImageView(RuntimeEnvironment.getApplication());
        image.setImageDrawable(states);
        states.setBounds(0, 0, 200, 200);
        Rect original = new Rect(glyph.getBounds());
        Bitmap bitmap = ControlCenterStyleSampler.read(image, "nfc").icon;
        assertNotNull(bitmap);
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            assertNotEquals("Only the foreground glyph may be captured", Color.MAGENTA, bitmap.getPixel(x, y));
        }
        assertEquals(original, glyph.getBounds());
        assertEquals(new Rect(0, 0, 200, 200), states.getBounds());
    }

    @Test public void coloredAndDetailedOpaqueApplicationArtworkKeepsItsPixels() {
        Bitmap artwork = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(artwork);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint();
        paint.setColor(Color.RED); canvas.drawRect(20, 20, 64, 108, paint);
        paint.setColor(Color.BLUE); canvas.drawRect(64, 20, 108, 108, paint);
        Bitmap original = artwork.copy(Bitmap.Config.ARGB_8888, false);
        assertEquals(ControlCenterIcons.ArtworkKind.COLOR, ControlCenterIcons.artworkKind(artwork));
        ControlCenterCardView tile = card("custom(com.example/.Tile)", artwork, true, false);
        for (int strength : new int[] {0, 50, 100, 0}) {
            Bitmap result = render(tile, strength, 112, 112);
            assertEquals(Color.RED, result.getPixel(50, 56));
            assertEquals(Color.BLUE, result.getPixel(62, 56));
        }
        assertTrue(original.sameAs(artwork));
        paint.setColor(Color.BLACK); canvas.drawRect(20, 20, 108, 108, paint);
        assertEquals(ControlCenterIcons.ArtworkKind.COLOR, ControlCenterIcons.artworkKind(artwork));
    }

    @Test public void oldInvalidCaptureCanUseTheApplicationIconWithoutLosingStateOrLabels() throws Exception {
        Bitmap plate = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        plate.eraseColor(Color.WHITE);
        var old = new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.CIRCLE, 18, 2,
                "Application tile", "Enabled", -1, plate);
        var decoded = ControlCenterCapturedStyle.parse(old.toJson());
        Bitmap appIcon = glyph("nfc", Color.BLUE);
        var merged = decoded.withFallbackIcon(appIcon);
        assertSame(appIcon, merged.icon);
        assertEquals(decoded.label, merged.label);
        assertEquals(decoded.subtitle, merged.subtitle);
        assertEquals(decoded.state, merged.state);
        assertSame(merged, merged.withFallbackIcon(glyph("wifi", Color.RED)));
        assertSame(decoded, decoded.withFallbackIcon(null));
    }

    @Test public void pairsAndGroupMembersUseTheSameReadableGlyphPath() {
        var context = RuntimeEnvironment.getApplication();
        var style = new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.CIRCLE, 24, 1,
                "Bluetooth", "Off", -1, glyph("bt", Color.BLACK));
        ControlCenterCardView pair = new ControlCenterCardView(context);
        pair.bind(new ControlCenterCardView.Model("bt", "nfc", "Bluetooth", "NFC", true, false, false, true,
                ControlCenterLayoutPlan.Shape.RECTANGLE, 24, 100, style, style));
        assertTrue(brightPixels(render(pair, 0, 224, 112)) > 120);
        var data = new ControlCenterGroupData(ControlCenterGroupData.Arrangement.CUSTOM,
                ControlCenterGroupData.Surface.CLEAR, false, java.util.List.of(
                new ControlCenterGroupData.Member("bt", 0, 0, 12, 12, false)));
        var item = ControlCenterLayoutPlan.Item.group("group:contrast", data, 0, 0, 2, 2);
        ControlCenterCardView group = new ControlCenterCardView(context);
        group.bindGroup(item, java.util.Map.of("bt", style), java.util.Map.of());
        assertTrue(brightPixels(render(group, 0, 160, 160)) > 60);
    }

    @Test public void repairedArtworkRendersAtNarrowAndWideCanvasWidths() throws Exception {
        var context = RuntimeEnvironment.getApplication();
        Bitmap whitePlate = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        whitePlate.eraseColor(Color.WHITE);
        String[] specs = {"wifi", "cell", "bt", "nfc", "screenrecord", "cast", "search", "mute"};
        java.util.ArrayList<ControlCenterGridEditor.Cell> cells = new java.util.ArrayList<>();
        for (int i = 0; i < specs.length; i++) {
            int row = i < 2 ? 0 : 1 + (i - 2) / 4;
            int column = i < 2 ? i * 2 : (i - 2) % 4;
            Bitmap icon = i == 2 ? glyph("bt", Color.BLACK) : i >= 3 && i <= 5 ? whitePlate : null;
            var style = new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.CIRCLE, 24, i == 0 ? 2 : 1,
                    i == 0 ? "WLAN" : "Mobile", i == 0 ? "Connected" : "Off", -1, icon);
            cells.add(new ControlCenterGridEditor.Cell(specs[i], specs[i], "", style.label, null, column, row,
                    i < 2 ? 2 : 1, 1, false, null, null, i < 2 ? ControlCenterLayoutPlan.Shape.RECTANGLE
                    : ControlCenterLayoutPlan.Shape.CIRCLE, 24, false, false, 0, 100).withCapturedStyles(style, null));
        }
        java.io.File directory = new java.io.File("build/reports/control-center-icons");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        for (int width : new int[] {360, 440}) for (int strength : new int[] {0, 100}) {
            ControlCenterGridEditor editor = new ControlCenterGridEditor(context, new ControlCenterGridEditor.Listener() {
                @Override public void onItemClicked(String id) { }
                @Override public void onItemMoved(String id, int x, int y) { fail("Rendering must not move tiles"); }
            });
            editor.setGeometry(16, 24);
            editor.setCells(4, cells);
            editor.setMaterialStrength(strength, strength);
            editor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            editor.layout(0, 0, width, editor.getMeasuredHeight());
            Bitmap image = Bitmap.createBitmap(width, editor.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image); canvas.drawColor(0xff080808); editor.draw(canvas);
            assertTrue(brightPixels(image) > 400);
            try (var out = new java.io.FileOutputStream(new java.io.File(directory, "canvas-" + width + "-" + strength + ".png"))) {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
            }
            image.recycle();
        }
    }

    private Bitmap coloredGlyph(int width, int height, Rect bounds) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        canvas.drawRect(bounds.left, bounds.top, bounds.centerX(), bounds.bottom, paint);
        paint.setColor(Color.BLUE);
        canvas.drawRect(bounds.centerX(), bounds.top, bounds.right, bounds.bottom, paint);
        return bitmap;
    }

    private Rect visibleGlyphBounds(Bitmap bitmap) {
        Rect result = new Rect(bitmap.getWidth(), bitmap.getHeight(), 0, 0);
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            int pixel = bitmap.getPixel(x, y);
            if (Color.alpha(pixel) < 32 || Math.max(Color.red(pixel), Math.max(Color.green(pixel), Color.blue(pixel))) < 80) continue;
            result.left = Math.min(result.left, x); result.top = Math.min(result.top, y);
            result.right = Math.max(result.right, x + 1); result.bottom = Math.max(result.bottom, y + 1);
        }
        return result;
    }

    private ControlCenterCardView card(String spec, Bitmap icon, boolean active, boolean large) {
        var view = new ControlCenterCardView(RuntimeEnvironment.getApplication());
        var style = new ControlCenterCapturedStyle(ControlCenterLayoutPlan.Shape.CIRCLE, 24, active ? 2 : 1,
                "WLAN", "Connected", -1, icon);
        view.bind(new ControlCenterCardView.Model(spec, "", "WLAN", null, false, active, false, large,
                large ? ControlCenterLayoutPlan.Shape.RECTANGLE : ControlCenterLayoutPlan.Shape.CIRCLE, 24, 100, style, null));
        return view;
    }

    private Bitmap glyph(String spec, int color) {
        Bitmap bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Drawable drawable = ControlCenterIcons.load(RuntimeEnvironment.getApplication(), ControlCenterIcons.resource(spec), color);
        drawable.setBounds(0, 0, 128, 128);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    private Bitmap render(ControlCenterCardView view, int strength, int width, int height) {
        view.setMaterialStrength(strength, strength);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xff080808);
        view.draw(canvas);
        return bitmap;
    }

    private int brightPixels(Bitmap bitmap) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            if (Color.luminance(bitmap.getPixel(x, y)) > 0.7f) count++;
        }
        return count;
    }

    private TextView title(View view, String expected) {
        if (view instanceof TextView text && expected.contentEquals(text.getText())) return text;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = title(group.getChildAt(i), expected);
            if (found != null) return found;
        }
        return null;
    }
}

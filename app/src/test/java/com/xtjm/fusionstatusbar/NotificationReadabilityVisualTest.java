package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.NotificationBackgroundView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Host-fixture Canvas evidence only; this does not simulate an OEM compositor. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationReadabilityVisualTest {
    private static final int COLUMNS = 6, ROWS = 8;
    private static final int TILE_WIDTH = 360, TILE_HEIGHT = 304, HEADER_HEIGHT = 68;
    private static final int SCENE_WIDTH = 336, SCENE_HEIGHT = 236;
    private static final int ROW_LEFT = 18, ROW_TOP = 20, ROW_WIDTH = 300, MAX_ROW_HEIGHT = 192;
    private static final String MATRIX = "docs/2026-11-16-notification-visual-matrix.csv";
    private static final String PHOTO = "notification-readability/high-frequency-photo.jpg";
    // The fixture README records the original source, permission and pinned asset hash.
    private static final String PHOTO_SHA256 = "47462f6e61177317dccfa9561280fe179544a1a3d0c3434cc517a822db547700";

    @Test public void nativeFallbackPreservesHostContentAndMeetsKnownHostContrastInAll48CsvScenes()
            throws Exception {
        File app = appDirectory();
        File matrix = new File(app.getParentFile(), MATRIX);
        List<Scene> scenes = readMatrix(matrix);
        byte[] photoBytes;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(PHOTO)) {
            assertNotNull("Missing licensed photograph: " + PHOTO + "; photo coverage cannot run", input);
            photoBytes = input.readAllBytes();
        }
        assertEquals("Photograph must match the reviewed resource", PHOTO_SHA256, sha256(photoBytes));
        Bitmap photo = BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.length);
        assertNotNull("Photograph must decode; a synthetic substitute is not photo coverage", photo);
        Bitmap sheet = Bitmap.createBitmap(COLUMNS * TILE_WIDTH, ROWS * TILE_HEIGHT,
                Bitmap.Config.ARGB_8888);
        List<String> failures = new ArrayList<>();
        JSONObject report = new JSONObject();
        JSONArray cells = new JSONArray();
        try {
            Canvas contact = new Canvas(sheet);
            contact.drawColor(0xff16191e);
            for (int index = 0; index < scenes.size(); index++) {
                Scene scene = scenes.get(index);
                try (Fixture fixture = new Fixture(scene, true, false)) {
                    Bitmap backgroundOnly = fixture.render(photo, false);
                    Bitmap complete = null;
                    try {
                        double body = minimumContrast(backgroundOnly, fixture.body,
                                fixture.body.getCurrentTextColor());
                        double large = minimumContrast(backgroundOnly, fixture.title,
                                fixture.title.getCurrentTextColor());
                        double action = minimumContrast(backgroundOnly, fixture.action,
                                fixture.action.getCurrentTextColor());
                        check(failures, scene.id + " body contrast=" + body, body >= 4.5);
                        check(failures, scene.id + " large contrast=" + large, large >= 3.0);
                        check(failures, scene.id + " action contrast=" + action, action >= 4.5);
                        fixture.assertHostContent();
                        assertEquals(scene.id + " must draw the original native color", fixture.hostBackground,
                                backgroundOnly.getPixel(ROW_LEFT + ROW_WIDTH / 2, ROW_TOP + 8));
                        assertEquals(scene.id + " must respect live actualHeight", backdropPixel(scene, photo,
                                ROW_LEFT + ROW_WIDTH / 2, ROW_TOP + scene.height + 2),
                                backgroundOnly.getPixel(ROW_LEFT + ROW_WIDTH / 2, ROW_TOP + scene.height + 2));
                        complete = fixture.render(photo, true);
                        drawCell(contact, complete, scene, index, body, large);
                        JSONObject cell = fixture.result(scene.id).put("bodyMinContrast", body)
                                .put("largeMinContrast", large).put("actionMinContrast", action)
                                .put("contrastStatus", body >= 4.5 && large >= 3.0 && action >= 4.5
                                        ? "pass" : "fail")
                                .put("sheetColumn", index % COLUMNS).put("sheetRow", index / COLUMNS)
                                .put("bodyTextRegion", rectJson(textRegion(fixture.body)))
                                .put("largeTextRegion", rectJson(textRegion(fixture.title)));
                        cells.put(cell);
                    } finally {
                        backgroundOnly.recycle();
                        if (complete != null) complete.recycle();
                    }
                }
            }
            JSONArray additional = additionalCases(photo);
            report.put("schemaVersion", 1).put("sdk", 34).put("density", "mdpi")
                    .put("renderer", "Robolectric NATIVE Canvas only")
                    .put("matrixSource", MATRIX).put("matrixSha256", sha256(Files.readAllBytes(matrix.toPath())))
                    .put("photoResource", PHOTO).put("photoSha256", sha256(photoBytes))
                    .put("columns", COLUMNS).put("rows", ROWS).put("cellCount", cells.length())
                    .put("bodyThreshold", 4.5).put("largeThreshold", 3.0)
                    .put("contrastMethod", "minimum WCAG sRGB ratio over background-only TextView regions; text antialiasing excluded")
                    .put("oemAvailability", "unknown").put("oemBlurVerified", false)
                    .put("targetRomStatus", "pending").put("cells", cells).put("additionalCases", additional)
                    .put("failures", new JSONArray(failures))
                    .put("limitations", new JSONArray(Arrays.asList(
                            "Known opaque host colors are fixtures, not sampled ROM backgrounds.",
                            "Blur conditions are scene labels; both use genuine native fallback with OEM availability unknown.",
                            "No device, RemoteViews inflation, LSPosed callbacks, compositor blur or 60/120Hz performance acceptance.")));
            File directory = new File(app, "build/reports/notification-readability");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(directory, "contact-sheet.png"))) {
                assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            Files.write(new File(directory, "contrast-report.json").toPath(),
                    (report.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
            assertEquals("All CSV scenes must be rendered", 48, cells.length());
            assertTrue("Known host contrast failures: " + failures, failures.isEmpty());
        } finally {
            sheet.recycle();
            photo.recycle();
        }
    }

    private static JSONArray additionalCases(Bitmap photo) throws Exception {
        JSONArray result = new JSONArray();
        Scene scene = new Scene("negative_application_colors", "light", "solid_light", "ineffective", "collapsed");
        try (Fixture fixture = new Fixture(scene, true, true)) {
            Bitmap background = fixture.render(photo, false);
            try {
                double contrast = minimumContrast(background, fixture.body, fixture.body.getCurrentTextColor());
                assertTrue("The application-owned negative control must actually be low contrast", contrast < 4.5);
                fixture.assertHostContent();
                result.put(fixture.result(scene.id).put("contrastStatus", "expected_low_contrast")
                        .put("bodyMinContrast", contrast).put("hostColorsPreserved", true)
                        .put("reason", "Application supplied low-contrast colors are recorded without recoloring."));
            } finally { background.recycle(); }
        }
        Scene unknown = new Scene("unknown_host_background", "light", "high_frequency_photo", "ineffective", "collapsed");
        try (Fixture fixture = new Fixture(unknown, false, false)) {
            Bitmap background = fixture.render(photo, false);
            try {
                fixture.assertHostContent();
                result.put(fixture.result(unknown.id).put("contrastStatus", "not_evaluated")
                        .put("bodyMinContrast", JSONObject.NULL).put("largeMinContrast", JSONObject.NULL)
                        .put("reason", "Custom host onDraw background has no reviewed color contract."));
            } finally { background.recycle(); }
        }
        return result;
    }

    private static final class Fixture implements AutoCloseable {
        final Scene scene;
        final boolean knownBackground;
        final ExpandableNotificationRow row;
        final NotificationBackgroundView background;
        final FrameLayout content;
        final TextView title, body, action;
        final ImageView icon;
        final ColorStateList hostColors, iconColors;
        final Drawable iconDrawable;
        final ColorDrawable original;
        final FusionConfig settings;
        final AtomicInteger clicks = new AtomicInteger();
        final int hostBackground;

        Fixture(Scene scene, boolean knownBackground, boolean lowContrast) {
            this.scene = scene;
            this.knownBackground = knownBackground;
            Context app = RuntimeEnvironment.getApplication();
            Configuration mode = new Configuration(app.getResources().getConfiguration());
            mode.uiMode = (mode.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (scene.dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
            mode.fontScale = 1f;
            Context context = app.createConfigurationContext(mode);
            assertEquals(1f, context.getResources().getDisplayMetrics().density, 0.001f);
            hostBackground = scene.dark ? 0xff353840 : 0xffeef2f6;
            int foreground = lowContrast ? 0xffc6cbd1 : scene.dark ? 0xfff1f3f4 : 0xff202124;
            hostColors = new ColorStateList(new int[][] {{android.R.attr.state_enabled}, {}},
                    new int[] {foreground, scene.dark ? 0xffcbd0d7 : 0xff505862});
            iconColors = ColorStateList.valueOf(scene.dark ? 0xff9fc8ff : 0xff245b99);
            background = new NotificationBackgroundView(context);
            original = knownBackground ? new ColorDrawable(hostBackground) : null;
            background.mBackground = original;
            background.setBackground(original);
            row = new ExpandableNotificationRow(context, background);
            row.setHeadsUp(scene.headsUp);
            row.setActualHeight(scene.height, false);
            background.setActualWidth(ROW_WIDTH);
            content = new FrameLayout(context);
            row.addView(content, new FrameLayout.LayoutParams(ROW_WIDTH, MAX_ROW_HEIGHT));
            title = label(context, "Notification", 24f, 44, 12, 240, 36);
            body = label(context, "Host text stays readable.", 14f, 16, 58, 268, 24);
            action = label(context, "OPEN", 14f, 16, scene.height - 29, 100, 24);
            action.setOnClickListener(ignored -> clicks.incrementAndGet());
            icon = new ImageView(context);
            iconDrawable = new ColorDrawable(0xff2878c8);
            icon.setImageDrawable(iconDrawable);
            icon.setImageTintList(iconColors);
            place(content, icon, 16, 21, 18, 18);
            row.measure(View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(MAX_ROW_HEIGHT, View.MeasureSpec.EXACTLY));
            row.layout(0, 0, ROW_WIDTH, MAX_ROW_HEIGHT);
            settings = FusionConfig.defaults().withControlCenter(ControlCenterConfig.defaults().withEnabled(true)
                    .withSurface(ControlCenterSurface.HIGHLIGHT_GLASS).withBlur(100, 100, 100)
                    .withCornerRadius(12).withGlobalBorder(false, 1)
                    .withSystemSurfaces(SystemSurfaceConfig.defaults().withNotifications(true).withHeadsUp(true)));
            NotificationSurfaceHooks.applyRow(row, settings);
            assertEquals(scene.headsUp, NotificationSurfaceHooks.isHeadsUp(row));
            assertEquals(scene.height, background.mActualHeight);
            if (knownBackground) {
                assertTrue("Real applyRow must install its notification material", SystemSurfaceStyle.isApplied(background));
                assertTrue("Real applyRow must mount its bounds wrapper",
                        background.getBackground() instanceof NotificationSurfaceHooks.BoundedBackground);
                assertTrue("Real fallback must draw the native original", SystemSurfaceStyle.usesNativeBackground(
                        ((NotificationSurfaceHooks.BoundedBackground) background.getBackground()).content));
            } else {
                assertFalse("Unknown onDraw must remain host-owned", SystemSurfaceStyle.isApplied(background));
            }
        }

        private TextView label(Context context, String text, float size, int left, int top, int width, int height) {
            TextView view = new TextView(context);
            view.setText(text);
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
            view.setTextColor(hostColors);
            view.setIncludeFontPadding(false);
            view.setSingleLine(true);
            view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            place(content, view, left, top, width, height);
            return view;
        }

        Bitmap render(Bitmap photo, boolean withText) {
            Bitmap pixels = Bitmap.createBitmap(SCENE_WIDTH, SCENE_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(pixels);
            drawBackdrop(canvas, scene, photo);
            content.setVisibility(withText ? View.VISIBLE : View.INVISIBLE);
            int saved = canvas.save();
            canvas.translate(ROW_LEFT, ROW_TOP);
            row.draw(canvas);
            canvas.restoreToCount(saved);
            content.setVisibility(View.VISIBLE);
            if (original != null) assertEquals("Drawing must preserve the host drawable alpha", 255, original.getAlpha());
            return pixels;
        }

        void assertHostContent() {
            assertSame("Body ColorStateList belongs to the host", hostColors, body.getTextColors());
            assertSame("Large text ColorStateList belongs to the host", hostColors, title.getTextColors());
            assertSame("Action ColorStateList belongs to the host", hostColors, action.getTextColors());
            assertSame(iconDrawable, icon.getDrawable());
            assertSame(iconColors, icon.getImageTintList());
            assertEquals(14f, body.getTextSize(), 0.001f);
            assertEquals(24f, title.getTextSize(), 0.001f);
            int before = clicks.get();
            assertTrue("Original action must still be callable", action.performClick());
            assertEquals(before + 1, clicks.get());
        }

        JSONObject result(String id) throws Exception {
            return new JSONObject().put("id", id).put("theme", scene.dark ? "dark" : "light")
                    .put("background", scene.backdrop).put("blurConditionLabel", scene.blur)
                    .put("notificationState", scene.state).put("actualHeight", background.mActualHeight)
                    .put("headsUp", row.mIsHeadsUp).put("hostBackgroundKnown", knownBackground)
                    .put("hostBackgroundArgb", knownBackground ? hex(hostBackground) : JSONObject.NULL)
                    .put("bodyForegroundArgb", hex(body.getCurrentTextColor()))
                    .put("largeForegroundArgb", hex(title.getCurrentTextColor()))
                    .put("hostDrawableAlpha", original == null ? JSONObject.NULL : original.getAlpha())
                    .put("hostColorsPreserved", true).put("hostIconPreserved", true).put("hostActionPreserved", true)
                    .put("localCanvasStatus", "executed").put("canvasOnly", true)
                    .put("oemAvailability", "unknown").put("oemBlurVerified", false)
                    .put("targetRomStatus", "pending")
                    .put("materialPath", knownBackground ? "native_fallback" : "host_custom_onDraw");
        }

        @Override public void close() {
            NotificationSurfaceHooks.applyRow(row, settings.withControlCenter(settings.controlCenter
                    .withSystemSurfaces(settings.controlCenter.systemSurfaces.withNotifications(false).withHeadsUp(false))));
            assertSame("Disabling both notification switches restores the original", original, background.getBackground());
            if (original != null) assertEquals(255, original.getAlpha());
            assertHostContent();
        }
    }

    private static void place(FrameLayout parent, View view, int left, int top, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        parent.addView(view, params);
    }

    private static double minimumContrast(Bitmap background, TextView text, int foreground) {
        Rect region = textRegion(text);
        assertTrue("The measured text region must be nonempty", region.width() > 0 && region.height() > 0);
        double minimum = Double.POSITIVE_INFINITY;
        for (int y = region.top; y < region.bottom; y++) for (int x = region.left; x < region.right; x++) {
            int pixel = background.getPixel(x, y);
            assertEquals("Contrast requires an opaque measured background", 255, Color.alpha(pixel));
            double a = luminance(foreground), b = luminance(pixel);
            minimum = Math.min(minimum, (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05));
        }
        return minimum;
    }

    private static Rect textRegion(TextView text) {
        // Background is drawn with content INVISIBLE; glyph edge antialiasing cannot bias the ratio.
        return new Rect(ROW_LEFT + text.getLeft() + text.getCompoundPaddingLeft(),
                ROW_TOP + text.getTop() + text.getCompoundPaddingTop(),
                ROW_LEFT + text.getRight() - text.getCompoundPaddingRight(),
                ROW_TOP + text.getBottom() - text.getCompoundPaddingBottom());
    }

    private static double luminance(int color) {
        return 0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color))
                + 0.0722 * channel(Color.blue(color));
    }

    private static double channel(int value) {
        double srgb = value / 255.0;
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }

    private static void drawBackdrop(Canvas canvas, Scene scene, Bitmap photo) {
        switch (scene.backdrop) {
            case "solid_light" -> canvas.drawColor(0xfffafafa);
            case "solid_dark" -> canvas.drawColor(0xff08090b);
            case "high_frequency_photo" -> canvas.drawBitmap(photo, null,
                    new Rect(0, 0, SCENE_WIDTH, SCENE_HEIGHT), new Paint(Paint.FILTER_BITMAP_FLAG));
            case "high_saturation" -> {
                Paint paint = new Paint();
                int[] colors = {0xffff0066, 0xff00ddff, 0xff5a00ff, 0xffffff00};
                for (int index = 0; index < colors.length; index++) {
                    paint.setColor(colors[index]);
                    canvas.drawRect(index * SCENE_WIDTH / 4f, 0, (index + 1) * SCENE_WIDTH / 4f, SCENE_HEIGHT, paint);
                }
            }
            default -> throw new AssertionError("Unsupported CSV backdrop: " + scene.backdrop);
        }
    }

    private static int backdropPixel(Scene scene, Bitmap photo, int x, int y) {
        Bitmap backdrop = Bitmap.createBitmap(SCENE_WIDTH, SCENE_HEIGHT, Bitmap.Config.ARGB_8888);
        try { drawBackdrop(new Canvas(backdrop), scene, photo); return backdrop.getPixel(x, y); }
        finally { backdrop.recycle(); }
    }

    private static void drawCell(Canvas canvas, Bitmap bitmap, Scene scene, int index, double body, double large) {
        int left = index % COLUMNS * TILE_WIDTH, top = index / COLUMNS * TILE_HEIGHT;
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(13f);
        canvas.drawText(scene.id + " " + (scene.dark ? "dark" : "light") + " / " + scene.state, left + 12, top + 18, text);
        canvas.drawText(scene.backdrop + " / blur label: " + scene.blur, left + 12, top + 36, text);
        text.setTextSize(11f);
        String annotation = String.format(Locale.ROOT,
                "Canvas only / OEM unverified / ROM pending / %.1f/%.1f", body, large);
        float availableWidth = TILE_WIDTH - 24f;
        if (text.measureText(annotation) > availableWidth)
            text.setTextSize(text.getTextSize() * availableWidth / text.measureText(annotation));
        canvas.drawText(annotation, left + 12, top + 54, text);
        canvas.drawBitmap(bitmap, left + 12, top + HEADER_HEIGHT, null);
    }

    private static List<Scene> readMatrix(File source) throws Exception {
        List<String> lines = Files.readAllLines(source.toPath(), StandardCharsets.UTF_8);
        assertFalse("Matrix CSV must exist and contain its header", lines.isEmpty());
        assertEquals("id,theme,background,blur_condition,notification_state,local_canvas_status,target_rom_status,acceptance",
                lines.get(0).replace("\ufeff", ""));
        List<Scene> scenes = new ArrayList<>();
        Set<String> ids = new HashSet<>(), combinations = new HashSet<>();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            String[] values = lines.get(index).split(",", -1);
            assertEquals("Unexpected CSV field count at line " + (index + 1), 8, values.length);
            Scene scene = new Scene(values[0], values[1], values[2], values[3], values[4]);
            assertTrue("Duplicate matrix id " + scene.id, ids.add(scene.id));
            assertTrue("Duplicate matrix combination", combinations.add(String.join("/", values[1], values[2], values[3], values[4])));
            scenes.add(scene);
        }
        assertEquals("The original CSV must supply exactly 48 unique combinations", 48, scenes.size());
        return scenes;
    }

    private static final class Scene {
        final String id, backdrop, blur, state;
        final boolean dark, headsUp;
        final int height;
        Scene(String id, String theme, String backdrop, String blur, String state) {
            assertTrue("Unknown theme " + theme, theme.equals("light") || theme.equals("dark"));
            assertTrue("Unknown blur scene label " + blur, blur.equals("effective") || blur.equals("ineffective"));
            assertTrue("Unknown background " + backdrop, Arrays.asList("solid_light", "solid_dark", "high_frequency_photo", "high_saturation").contains(backdrop));
            this.id = id;
            this.dark = theme.equals("dark");
            this.backdrop = backdrop;
            this.blur = blur;
            this.state = state;
            this.headsUp = state.equals("heads_up");
            this.height = switch (state) {
                case "collapsed" -> 112;
                case "expanded" -> 192;
                case "heads_up" -> 144;
                default -> throw new AssertionError("Unknown notification state " + state);
            };
        }
    }

    private static File appDirectory() throws Exception {
        File base = new File(System.getProperty("user.dir")).getCanonicalFile();
        File app = base.getName().equals("app") ? base : new File(base, "app");
        assertTrue("Run from this work root or its app directory; reports must stay in the project", new File(app, "src/test/java").isDirectory());
        return app;
    }

    private static JSONObject rectJson(Rect rect) throws Exception {
        return new JSONObject().put("left", rect.left).put("top", rect.top)
                .put("right", rect.right).put("bottom", rect.bottom);
    }

    private static void check(List<String> failures, String message, boolean condition) {
        if (!condition) failures.add(message);
    }

    private static String hex(int value) { return String.format(Locale.ROOT, "#%08X", value); }

    private static String sha256(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
            result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}

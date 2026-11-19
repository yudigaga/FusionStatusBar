package com.xtjm.fusionstatusbar;

import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.NotificationBackgroundView;
import java.util.IdentityHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Daytime Canvas contracts; no OEM compositor or screenshot attribution is implied. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DaytimeNotificationBackgroundReadabilityRegressionTest {
    private static final int LIGHT_CARD = 0xffeef2f6;
    private static final int SIZE = 320;
    private static final int HEIGHT = 180;

    @Test public void automaticZeroKeepsTheHostLightCardBehindBlackTextOnEitherBackdrop() {
        for (int backdrop : new int[] {Color.BLACK, 0xfffafafa}) {
            try (Fixture fixture = new Fixture("custom", LIGHT_CARD)) {
                fixture.apply(ControlCenterSurface.HIGHLIGHT_GLASS, 0);
                int pixel = fixture.backgroundPixel(backdrop);
                assertEquals("Automatic glass cannot erase the host card at its zero endpoint",
                        LIGHT_CARD, pixel);
                assertTrue("The light host card keeps its original black title readable",
                        contrast(Color.BLACK, pixel) >= 7d);
                fixture.assertContentUnchanged();
            }
        }
    }

    @Test public void everyAutomaticStrengthKeepsTheSameHostBodyWithoutRecoloringTemplates() {
        for (String template : new String[] {"ordinary", "messaging", "media", "custom"}) {
            try (Fixture fixture = new Fixture(template, LIGHT_CARD)) {
                for (int strength : new int[] {0, 1, 30, 100, 0}) {
                    fixture.apply(ControlCenterSurface.HIGHLIGHT_GLASS, strength);
                    assertEquals(template + " on a black backdrop at strength " + strength,
                            LIGHT_CARD, fixture.backgroundPixel(Color.BLACK));
                    fixture.assertContentUnchanged();
                }
                fixture.disable();
                fixture.assertContentUnchanged();
            }
        }
    }

    @Test public void anAlreadyTransparentHostBackgroundIsNotReplacedWithAnInventedCard() {
        try (Fixture fixture = new Fixture("custom", Color.TRANSPARENT)) {
            for (int strength : new int[] {0, 100}) {
                fixture.apply(ControlCenterSurface.HIGHLIGHT_GLASS, strength);
                assertEquals("Preserving the host also preserves a genuinely transparent original",
                        Color.BLACK, fixture.backgroundPixel(Color.BLACK));
                fixture.assertContentUnchanged();
            }
        }
    }

    @Test public void zeroAutomaticStrengthFollowsLiveNativeReplacementAndRestoresThatDrawable() {
        try (Fixture fixture = new Fixture("custom", 0xffdddde0)) {
            fixture.apply(ControlCenterSurface.HIGHLIGHT_GLASS, 0);
            ColorDrawable replacement = new ColorDrawable(LIGHT_CARD);
            replacement.setBounds(4, 5, 6, 7);
            fixture.background.mBackground = replacement;

            assertEquals("The current native field can change without another module apply",
                    LIGHT_CARD, fixture.backgroundPixel(Color.BLACK));
            assertEquals(new android.graphics.Rect(4, 5, 6, 7), replacement.getBounds());
            assertEquals(255, replacement.getAlpha());
            fixture.disable();
            assertSame(replacement, fixture.background.getBackground());
            fixture.assertContentUnchanged();
        }
    }

    @Test public void explicitClearRetainsItsTransparencyAndDisablingRestoresTheHost() {
        try (Fixture fixture = new Fixture("custom", LIGHT_CARD)) {
            fixture.apply(ControlCenterSurface.CLEAR, 100);
            assertEquals(Color.BLACK, fixture.backgroundPixel(Color.BLACK));
            fixture.assertContentUnchanged();

            fixture.disable();
            assertSame(fixture.original, fixture.background.getBackground());
            assertEquals(LIGHT_CARD, fixture.backgroundPixel(Color.BLACK));
            fixture.assertContentUnchanged();
        }
    }

    @Test public void defaultKeepsItsExistingStrengthSemanticsAndDoesNotAlterSemanticColors() {
        try (Fixture fixture = new Fixture("custom", Color.WHITE)) {
            fixture.apply(ControlCenterSurface.DEFAULT, 100);
            assertEquals(Color.WHITE, fixture.backgroundPixel(Color.BLACK));
            fixture.apply(ControlCenterSurface.DEFAULT, 0);
            assertEquals("The automatic safety change does not redefine explicit DEFAULT zero",
                    Color.BLACK, fixture.backgroundPixel(Color.BLACK));
            fixture.assertContentUnchanged();
        }
    }

    @Test public void disablingAutomaticZeroRestoresNativeBlurAndSemanticColorStateLists() {
        try (Fixture fixture = new Fixture("custom", LIGHT_CARD)) {
            int radius = fixture.background.getMiBackgroundBlurRadius();
            int mode = fixture.background.getMiBackgroundBlurMode();
            fixture.apply(ControlCenterSurface.HIGHLIGHT_GLASS, 0);
            assertEquals("Automatic fallback cannot disable an unverified host blur path", radius,
                    fixture.background.getMiBackgroundBlurRadius());
            assertEquals(mode, fixture.background.getMiBackgroundBlurMode());
            fixture.disable();
            assertEquals(radius, fixture.background.getMiBackgroundBlurRadius());
            assertEquals(mode, fixture.background.getMiBackgroundBlurMode());
            fixture.assertContentUnchanged();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final NotificationBackgroundView background;
        final ExpandableNotificationRow row;
        final ColorDrawable original;
        final View content;
        final Map<TextView, ColorStateList> colors = new IdentityHashMap<>();
        final Map<TextView, Float> sizes = new IdentityHashMap<>();
        final FusionConfig settings;
        FusionConfig applied;

        Fixture(String template, int nativeColor) {
            Context context = RuntimeEnvironment.getApplication();
            background = new NotificationBackgroundView(context);
            original = new ColorDrawable(nativeColor);
            background.setCustomBackground(original);
            background.setActualHeight(HEIGHT);
            background.setActualWidth(SIZE);
            row = new ExpandableNotificationRow(context, background);
            content = template.equals("custom") ? customContent(context)
                    : notificationContent(context, row, template);
            row.addView(content, new FrameLayout.LayoutParams(SIZE, HEIGHT));
            snapshot(content);
            assertFalse("Each real template must contain text to protect", colors.isEmpty());
            row.measure(View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
            row.layout(0, 0, SIZE, HEIGHT);
            settings = FusionConfig.defaults().withControlCenter(ControlCenterConfig.defaults()
                    .withEnabled(true).withCornerRadius(0).withGlobalBorder(false, 1)
                    .withSystemSurfaces(SystemSurfaceConfig.defaults()
                            .withNotifications(true).withHeadsUp(true)));
        }

        void apply(ControlCenterSurface surface, int strength) {
            applied = settings.withControlCenter(settings.controlCenter.withSurface(surface)
                    .withBlur(100, strength, 100));
            NotificationSurfaceHooks.applyRow(row, applied);
        }

        int backgroundPixel(int backdrop) {
            Bitmap bitmap = Bitmap.createBitmap(SIZE, HEIGHT, Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(backdrop);
                // Exclude template-owned drawing and glyph antialiasing from the material sample.
                background.draw(canvas);
                return bitmap.getPixel(SIZE / 2, HEIGHT / 2);
            } finally { bitmap.recycle(); }
        }

        void assertContentUnchanged() {
            assertSame(row, content.getParent());
            for (Map.Entry<TextView, ColorStateList> entry : colors.entrySet()) {
                TextView text = entry.getKey();
                assertSame("A notification retains the host's complete semantic color list",
                        entry.getValue(), text.getTextColors());
                assertEquals(sizes.get(text), text.getTextSize(), 0.001f);
            }
        }

        void snapshot(View view) {
            if (view instanceof TextView text) {
                colors.put(text, text.getTextColors());
                sizes.put(text, text.getTextSize());
            }
            if (view instanceof ViewGroup group) {
                for (int i = 0; i < group.getChildCount(); i++) snapshot(group.getChildAt(i));
            }
        }

        void disable() {
            FusionConfig value = applied == null ? settings : applied;
            NotificationSurfaceHooks.applyRow(row, value.withControlCenter(value.controlCenter
                    .withSystemSurfaces(value.controlCenter.systemSurfaces
                            .withNotifications(false).withHeadsUp(false))));
        }

        @Override public void close() { disable(); }
    }

    private static View notificationContent(Context context, ViewGroup row, String template) {
        Notification.Builder builder = new Notification.Builder(context, "fixture")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Host title").setContentText("Host notification body");
        if (template.equals("messaging")) {
            Person self = new Person.Builder().setName("Me").build();
            Person sender = new Person.Builder().setName("Contact").build();
            builder.setStyle(new Notification.MessagingStyle(self)
                    .addMessage("Host message", 1L, sender));
        } else if (template.equals("media")) {
            builder.addAction(new Notification.Action.Builder(
                    android.R.drawable.ic_media_play, "Play", null).build());
            builder.setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0));
        }
        RemoteViews remote = builder.createContentView();
        assertNotNull("Android must produce the selected host template", remote);
        return remote.apply(context, row);
    }

    private static View customContent(Context context) {
        FrameLayout content = new FrameLayout(context);
        int[] foregrounds = {Color.BLACK, 0xff1565c0, 0xffb71c1c, 0xff1b5e20};
        for (int i = 0; i < foregrounds.length; i++) {
            TextView text = new TextView(context);
            text.setText(i == 0 ? "Host black title" : "Host semantic action");
            text.setTextColor(new ColorStateList(new int[][] {{-android.R.attr.state_enabled}, {}},
                    new int[] {0xff777777, foregrounds[i]}));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(280, 32);
            params.topMargin = i * 34;
            content.addView(text, params);
        }
        return content;
    }

    private static double contrast(int foreground, int background) {
        double first = luminance(foreground), second = luminance(background);
        return (Math.max(first, second) + 0.05d) / (Math.min(first, second) + 0.05d);
    }

    private static double luminance(int color) {
        return 0.2126d * channel(Color.red(color)) + 0.7152d * channel(Color.green(color))
                + 0.0722d * channel(Color.blue(color));
    }

    private static double channel(int value) {
        double normalized = value / 255d;
        return normalized <= 0.04045d ? normalized / 12.92d
                : Math.pow((normalized + 0.055d) / 1.055d, 2.4d);
    }
}

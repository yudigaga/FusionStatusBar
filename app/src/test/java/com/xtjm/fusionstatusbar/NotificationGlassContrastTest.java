package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.NotificationBackgroundView;
import com.android.systemui.statusbar.notification.row.NotificationContentView;
import com.android.systemui.statusbar.notification.row.wrapper.MiuiNotificationTemplateViewWrapper;
import com.android.systemui.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper;
import com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer;
import io.github.libxposed.api.XposedInterface;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationGlassContrastTest {
    private static final int OEM_BLUR_BACKGROUND = 0x7f080821;
    private static final int INTENTIONALLY_TRANSPARENT_BACKGROUND = 0x7f080822;
    private static final int SIZE = 160;

    @Test public void stockTextOverOemGlassRemainsReadableAgainstBlackAndWhite() throws Throwable {
        try (ContrastPreview preview = ContrastPreview.fromEnvironment()) {
            for (boolean night : new boolean[] {false, true}) {
                try (Fixture fixture = new Fixture(night)) {
                    if (preview != null) {
                        fixture.installBackground(INTENTIONALLY_TRANSPARENT_BACKGROUND);
                        preview.draw(fixture, night, false);
                    }
                    fixture.installBackground(OEM_BLUR_BACKGROUND);
                    fixture.assertReadable(fixture.privateTemplate, night ? "night shade" : "day shade");
                    if (preview != null) preview.draw(fixture, night, true);
                    fixture.call(fixture.row, "setHeadsUp", true);
                    fixture.assertReadable(fixture.privateTemplate, night ? "night heads-up" : "day heads-up");
                    assertSame(fixture.privateTemplate.actionColors, fixture.privateTemplate.action.getTextColors());
                    assertTrue(fixture.privateTemplate.action.performClick());
                    assertEquals(1, fixture.privateTemplate.actionInvocations);
                    fixture.publicTemplate.assertHostColors();
                    assertEquals(View.GONE, fixture.row.mPublicLayout.getVisibility());
                }
            }
            if (preview != null) preview.write();
        }
    }

    @Test public void explicitTransparentHostBackgroundRetainsHostColorsAndTransparency() throws Throwable {
        try (Fixture fixture = new Fixture(false)) {
            fixture.installBackground(OEM_BLUR_BACKGROUND);
            fixture.installBackground(INTENTIONALLY_TRANSPARENT_BACKGROUND);
            fixture.privateTemplate.assertHostColors();
            fixture.publicTemplate.assertHostColors();
            assertEquals(Color.BLACK, fixture.backgroundPixel(Color.BLACK));
            assertEquals(Color.WHITE, fixture.backgroundPixel(Color.WHITE));
        }
    }

    @Test public void switchToPublicContentLeavesPrivateContentAtItsLatestHostColors() throws Throwable {
        try (Fixture fixture = new Fixture(false)) {
            fixture.installBackground(OEM_BLUR_BACKGROUND);
            fixture.assertReadable(fixture.privateTemplate, "private");

            ColorStateList rebound = ColorStateList.valueOf(0x78000000);
            TextView time = fixture.privateTemplate.wrapper.mTime;
            time.setTextColor(rebound);
            fixture.call(fixture.row, "onNotificationUpdated");
            assertNotSame(rebound, time.getTextColors());

            fixture.call(fixture.row, "setHideSensitive", true, false, 0L, 0L);
            assertSame(rebound, time.getTextColors());
            assertEquals(View.GONE, fixture.row.mPrivateLayout.getVisibility());
            assertEquals(View.VISIBLE, fixture.row.mPublicLayout.getVisibility());
            fixture.assertReadable(fixture.publicTemplate, "public on lockscreen");

            fixture.disable();
            fixture.publicTemplate.assertHostColors();
            assertSame(rebound, time.getTextColors());
        }
    }

    @Test public void themeRefreshAdaptsCurrentRowAndDisableRestoresReboundHostColors() throws Throwable {
        try (Fixture fixture = new Fixture(false)) {
            fixture.installBackground(OEM_BLUR_BACKGROUND);
            fixture.assertReadable(fixture.privateTemplate, "day before theme change");
            int lightBody = fixture.backgroundPixel(Color.BLACK);

            fixture.resources.setNight(true);
            fixture.privateTemplate.setHostPalette(true);
            fixture.call(fixture.row, "onNotificationUpdated");
            NotificationSurfaceHooks.refresh();
            fixture.assertReadable(fixture.privateTemplate, "night after theme change and rebind");

            assertTrue("The active theme changes the backing color",
                    ColorUtils.calculateLuminance(fixture.backgroundPixel(Color.WHITE))
                            < ColorUtils.calculateLuminance(lightBody));
            fixture.disable();
            fixture.privateTemplate.assertHostColors();
        }
    }

    @Test public void groupedSummaryKeepsItsAppNameAndSummaryReadable() throws Throwable {
        try (Fixture fixture = new Fixture(false)) {
            NotificationChildrenContainer group = fixture.attachGroup();
            FrameLayout header = new FrameLayout(group.getContext());
            TextView appName = Template.addLabel(header, "Calendar", 0);
            TextView summary = Template.addLabel(header, "3 updates", 26);
            ColorStateList appNameColor = ColorStateList.valueOf(0xe6000000);
            ColorStateList summaryColor = ColorStateList.valueOf(0x68000000);
            appName.setTextColor(appNameColor);
            summary.setTextColor(summaryColor);
            group.addView(header, new FrameLayout.LayoutParams(-1, -1));
            group.setVisibleWrapper(new NotificationHeaderViewWrapper(header, appName, summary));
            fixture.installBackground(OEM_BLUR_BACKGROUND);
            fixture.assertReadableLabels(List.of(appName, summary), "group summary");
            assertNotEquals(appName.getCurrentTextColor(), summary.getCurrentTextColor());
            fixture.privateTemplate.assertHostColors();
            fixture.disable();
            assertSame(appNameColor, appName.getTextColors());
            assertSame(summaryColor, summary.getTextColors());
        }
    }

    @Test public void anUnhookedBackgroundReplacementReleasesColorsBeforeTheNextDraw() throws Throwable {
        for (boolean external : new boolean[] {false, true}) {
            try (Fixture fixture = new Fixture(false)) {
                fixture.installBackground(OEM_BLUR_BACKGROUND);
                fixture.assertReadable(fixture.privateTemplate, "before replacement");
                int newBody = 0xffe9eef4;
                Drawable replacement = new ColorDrawable(newBody);
                if (external) fixture.background.setBackground(replacement);
                else fixture.background.mBackground = replacement;

                fixture.background.getViewTreeObserver().dispatchOnPreDraw();
                fixture.privateTemplate.assertHostColors();
                assertEquals(newBody, fixture.backgroundPixel(Color.BLACK));
            }
        }
    }

    @Test public void switchingVisibleTemplateRestoresPreviousStockTextImmediately() throws Throwable {
        try (Fixture fixture = new Fixture(false)) {
            NotificationContentView content = fixture.row.mPrivateLayout;
            Template contracted = fixture.privateTemplate;
            Template expanded = new Template(content, false, true);
            fixture.installBackground(OEM_BLUR_BACKGROUND);
            fixture.assertReadable(contracted, "contracted");
            expanded.assertHostColors();
            assertEquals(0, content.mVisibleType);
            assertEquals(View.VISIBLE, contracted.wrapper.mView.getVisibility());
            assertEquals(View.INVISIBLE, expanded.wrapper.mView.getVisibility());

            ColorStateList rebound = ColorStateList.valueOf(0x78000000);
            TextView previouslyVisible = contracted.wrapper.mTime;
            previouslyVisible.setTextColor(rebound);
            fixture.call(fixture.row, "onNotificationUpdated");
            assertNotSame(rebound, previouslyVisible.getTextColors());

            content.setExpanded(true);
            fixture.call(content, "selectLayout", false, true);

            assertEquals(1, content.mVisibleType);
            assertEquals(View.INVISIBLE, contracted.wrapper.mView.getVisibility());
            assertEquals(View.VISIBLE, expanded.wrapper.mView.getVisibility());
            assertSame(rebound, previouslyVisible.getTextColors());
            fixture.assertReadable(expanded, "expanded");
        }
    }

    @Test public void recognizedCarrierFadesBodyOnceAndClipsTheWholeMaterial() throws Throwable {
        try (Fixture fixture = new Fixture(false)) {
            fixture.installBackground(OEM_BLUR_BACKGROUND);
            Drawable carrier = fixture.background.mBackground;
            assertEquals(255, carrier.getAlpha());
            int fullBody = Color.alpha(fixture.backgroundPixel(Color.TRANSPARENT));
            assertTrue("The center contains a translucent body", fullBody > 0 && fullBody < 255);
            assertTrue("The visible glass also draws a brighter edge", fixture.peakBackgroundAlpha() > fullBody);

            carrier.setAlpha(128);
            int expectedHalf = Math.round(fullBody * 128f / 255f);
            int halfBody = Color.alpha(fixture.backgroundPixel(Color.TRANSPARENT));
            assertEquals("The body uses native alpha exactly once", expectedHalf, halfBody, 1);
            assertEquals(128, carrier.getAlpha());

            carrier.setAlpha(0);
            assertEquals("Native alpha zero hides the tint and high-gloss edge", 0, fixture.peakBackgroundAlpha());

            carrier.setAlpha(255);
            fixture.call(fixture.background, "setClipTopAmount", SIZE / 2);
            fixture.call(fixture.background, "setClipBottomAmount", SIZE / 2);
            assertEquals("Full native clipping hides the tint and high-gloss edge", 0, fixture.peakBackgroundAlpha());
        }
    }

    @Test public void visibleMiuiGroupUsesItsOwnSummaryAndLeavesHiddenGoogleHeaderAlone() throws Throwable {
        try (Fixture fixture = new Fixture(false)) {
            NotificationChildrenContainer group = fixture.attachGroup();
            group.mChildrenExpanded = true;
            fixture.row.mChildrenExpanded = true;
            FrameLayout miuiHeader = new FrameLayout(group.getContext());
            TextView groupInfo = Template.addLabel(miuiHeader, "Calendar (3)", 0);
            ColorStateList summaryColor = ColorStateList.valueOf(0x68000000);
            groupInfo.setTextColor(summaryColor);
            TextView collapse = Template.addLabel(miuiHeader, "Collapse", 26);
            ColorStateList collapseColor = ColorStateList.valueOf(0xff134db0);
            collapse.setTextColor(collapseColor);
            int[] clicks = {0};
            collapse.setOnClickListener(view -> clicks[0]++);
            group.addView(miuiHeader, new FrameLayout.LayoutParams(-1, -1));
            group.mInjector.mMiuiGroupHeader = miuiHeader;
            group.mInjector.mGroupInfo = groupInfo;

            FrameLayout googleHeader = new FrameLayout(group.getContext());
            TextView hiddenName = Template.addLabel(googleHeader, "Hidden app name", 0);
            TextView hiddenSummary = Template.addLabel(googleHeader, "Hidden summary", 26);
            ColorStateList hiddenColor = ColorStateList.valueOf(0x60000000);
            hiddenName.setTextColor(hiddenColor);
            hiddenSummary.setTextColor(hiddenColor);
            googleHeader.setVisibility(View.GONE);
            group.addView(googleHeader, new FrameLayout.LayoutParams(-1, -1));
            group.setVisibleWrapper(new NotificationHeaderViewWrapper(googleHeader, hiddenName, hiddenSummary));

            fixture.installBackground(OEM_BLUR_BACKGROUND);

            fixture.assertReadableLabels(List.of(groupInfo), "MIUI group summary");
            assertEquals(View.VISIBLE, miuiHeader.getVisibility());
            assertEquals(View.GONE, googleHeader.getVisibility());
            assertSame(hiddenColor, hiddenName.getTextColors());
            assertSame(hiddenColor, hiddenSummary.getTextColors());
            assertSame(collapseColor, collapse.getTextColors());
            assertTrue(collapse.performClick());
            assertEquals(1, clicks[0]);

            fixture.disable();
            assertSame(summaryColor, groupInfo.getTextColors());
        }
    }

    @Test public void collapsedMiuiGroupStylesOnlyTheVisibleChildWithoutItsOwnBacking() throws Throwable {
        try (Fixture fixture = new Fixture(false)) {
            NotificationChildrenContainer group = fixture.attachGroup();
            FrameLayout miuiHeader = new FrameLayout(group.getContext());
            TextView groupInfo = Template.addLabel(miuiHeader, "Calendar (3)", 0);
            ColorStateList summaryColor = ColorStateList.valueOf(0x68000000);
            groupInfo.setTextColor(summaryColor);
            miuiHeader.setAlpha(0f);
            group.addView(miuiHeader, new FrameLayout.LayoutParams(-1, -1));
            group.mInjector.mMiuiGroupHeader = miuiHeader;
            group.mInjector.mGroupInfo = groupInfo;

            Template visible = fixture.attachChild(group, true, false);
            Template withBacking = fixture.attachChild(group, true, true);
            Template hidden = fixture.attachChild(group, false, false);
            fixture.installBackground(OEM_BLUR_BACKGROUND);

            fixture.assertReadable(visible, "visible collapsed-group child");
            withBacking.assertHostColors();
            hidden.assertHostColors();
            assertSame(summaryColor, groupInfo.getTextColors());
            assertEquals(0f, miuiHeader.getAlpha(), 0f);

            fixture.disable();
            visible.assertHostColors();
        }
    }

    @Test public void groupChildKeepsReadableColorsWhenItGainsItsOwnGlassBacking() throws Throwable {
        try (Fixture fixture = new Fixture(false)) {
            NotificationChildrenContainer group = fixture.attachGroup();
            Template visible = fixture.attachChild(group, true, false);
            fixture.installBackground(OEM_BLUR_BACKGROUND);
            TextView time = visible.wrapper.mTime;
            ColorStateList corrected = time.getTextColors();
            assertNotSame(visible.hostColors[2], corrected);

            ExpandableNotificationRow child = group.getAttachedChildren().get(0);
            child.mBackgroundNormal.setVisibility(View.VISIBLE);
            fixture.call(child.mBackgroundNormal, "setCustomBackground", OEM_BLUR_BACKGROUND);
            fixture.call(fixture.row, "onNotificationUpdated");
            assertSame("The old group owner must not restore colors still needed by the child",
                    corrected, time.getTextColors());

            fixture.disable();
            visible.assertHostColors();
        }
    }

    private static final class Template {
        final MiuiNotificationTemplateViewWrapper wrapper;
        final List<TextView> labels;
        final TextView action;
        final ColorStateList actionColors;
        final ColorStateList[] hostColors = new ColorStateList[4];
        int actionInvocations;

        Template(NotificationContentView content, boolean night) {
            this(content, night, false);
        }

        Template(NotificationContentView content, boolean night, boolean expanded) {
            Context context = content.getContext();
            FrameLayout template = new FrameLayout(context);
            TextView title = addLabel(template, "News update", 0);
            TextView text = addLabel(template, "The meeting time changed", 26);
            TextView time = addLabel(template, "10:20", 52);
            TextView subText = addLabel(template, "Calendar", 78);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextSize(16);
            text.setTextSize(12);
            time.setTextSize(11);
            subText.setTextSize(11);
            action = addLabel(template, "Open", 104);
            actionColors = new ColorStateList(new int[][] {{-android.R.attr.state_enabled}, {}},
                    new int[] {0xff909090, night ? 0xff8cc1ff : 0xff1355cf});
            action.setTextColor(actionColors);
            action.setOnClickListener(view -> actionInvocations++);
            wrapper = new MiuiNotificationTemplateViewWrapper(template, title, text, time, subText);
            labels = List.of(title, text, time, subText);
            setHostPalette(night);
            content.addView(template, new FrameLayout.LayoutParams(-1, -1));
            if (expanded) content.setExpandedWrapper(wrapper, template);
            else content.setContractedWrapper(wrapper, template);
        }

        void setHostPalette(boolean night) {
            int[] colors = night
                    ? new int[] {0xe6ffffff, 0xa6ffffff, 0x68ffffff, 0x78ffffff}
                    : new int[] {0xe6000000, 0xa6000000, 0x68000000, 0x78000000};
            for (int index = 0; index < labels.size(); index++) {
                hostColors[index] = ColorStateList.valueOf(colors[index]);
                labels.get(index).setTextColor(hostColors[index]);
            }
        }

        void assertHostColors() {
            for (int index = 0; index < labels.size(); index++) {
                assertSame(hostColors[index], labels.get(index).getTextColors());
            }
            assertSame(actionColors, action.getTextColors());
        }

        private static TextView addLabel(FrameLayout owner, String text, int top) {
            TextView label = new TextView(owner.getContext());
            label.setText(text);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, 24);
            params.topMargin = top;
            owner.addView(label, params);
            return label;
        }
    }

    private static final class Fixture implements AutoCloseable, NotificationSurfaceHooks.Registrar {
        final Map<Method, XposedInterface.Hooker> hooks = new HashMap<>();
        final HostResources resources;
        final NotificationBackgroundView background;
        final ExpandableNotificationRow row;
        final Template privateTemplate;
        final Template publicTemplate;
        FusionConfig config;

        Fixture(boolean night) throws Throwable {
            Context app = RuntimeEnvironment.getApplication();
            resources = new HostResources(app.getResources(), night);
            Context context = new ContextWrapper(app) {
                @Override public Resources getResources() { return resources; }
            };
            background = new NotificationBackgroundView(context);
            background.setActualHeight(SIZE);
            background.setActualWidth(SIZE);
            row = new ExpandableNotificationRow(context, background);
            row.mPrivateLayout = new NotificationContentView(context);
            row.mPublicLayout = new NotificationContentView(context);
            row.mPrivateLayout.mContainingNotification = row;
            row.mPublicLayout.mContainingNotification = row;
            privateTemplate = new Template(row.mPrivateLayout, night);
            publicTemplate = new Template(row.mPublicLayout, night);
            row.addView(row.mPrivateLayout, new FrameLayout.LayoutParams(-1, -1));
            row.addView(row.mPublicLayout, new FrameLayout.LayoutParams(-1, -1));
            row.mPublicLayout.setVisibility(View.GONE);
            row.measure(View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY));
            row.layout(0, 0, SIZE, SIZE);
            config = FusionConfig.defaults().withControlCenter(ControlCenterConfig.defaults()
                    .withEnabled(true).withSurface(ControlCenterSurface.HIGHLIGHT_GLASS)
                    .withBlur(100, 100, 100).withGlobalBorder(false, 1)
                    .withSystemSurfaces(SystemSurfaceConfig.defaults()
                            .withNotifications(true).withHeadsUp(true)));
            assertTrue(NotificationSurfaceHooks.install(getClass().getClassLoader(),
                    this, () -> config, ignored -> { }) > 0);
            call(row, "onFinishInflate");
        }

        void installBackground(int id) throws Throwable {
            call(background, "setCustomBackground", id);
        }

        NotificationChildrenContainer attachGroup() {
            NotificationChildrenContainer group = new NotificationChildrenContainer(row.getContext());
            row.mChildrenContainer = group;
            row.mIsSummaryWithChildren = true;
            row.mPrivateLayout.setVisibility(View.GONE);
            row.addView(group, new FrameLayout.LayoutParams(-1, -1));
            return group;
        }

        Template attachChild(NotificationChildrenContainer group, boolean visible, boolean ownBacking) {
            Context context = group.getContext();
            NotificationBackgroundView childBackground = new NotificationBackgroundView(context);
            ExpandableNotificationRow child = new ExpandableNotificationRow(context, childBackground);
            child.mNotificationParent = row;
            child.mPrivateLayout = new NotificationContentView(context);
            child.mPrivateLayout.mContainingNotification = child;
            Template content = new Template(child.mPrivateLayout, false);
            child.addView(child.mPrivateLayout, new FrameLayout.LayoutParams(-1, -1));
            if (ownBacking) childBackground.setCustomBackground(new ColorDrawable(Color.WHITE));
            else childBackground.setVisibility(View.GONE);
            if (!visible) child.setVisibility(View.INVISIBLE);
            group.addNotification(child);
            return content;
        }

        int backgroundPixel(int backdrop) {
            Bitmap bitmap = drawBackground(backdrop);
            try { return bitmap.getPixel(SIZE / 2, SIZE / 2); }
            finally { bitmap.recycle(); }
        }

        int peakBackgroundAlpha() {
            Bitmap bitmap = drawBackground(Color.TRANSPARENT);
            try {
                int[] pixels = new int[SIZE * SIZE];
                bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
                int peak = 0;
                for (int pixel : pixels) peak = Math.max(peak, Color.alpha(pixel));
                return peak;
            } finally { bitmap.recycle(); }
        }

        private Bitmap drawBackground(int backdrop) {
            Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(backdrop);
            background.draw(canvas);
            return bitmap;
        }

        void assertReadable(Template template, String surface) {
            assertReadableLabels(template.labels, surface);
            assertNotEquals("Title and body retain distinct visual hierarchy",
                    template.wrapper.mTitle.getCurrentTextColor(), template.wrapper.mText.getCurrentTextColor());
            assertNotEquals("Body and metadata retain distinct visual hierarchy",
                    template.wrapper.mText.getCurrentTextColor(), template.wrapper.mTime.getCurrentTextColor());
        }

        void assertReadableLabels(List<TextView> labels, String surface) {
            for (int backdrop : new int[] {Color.BLACK, Color.WHITE}) {
                int materialPixel = backgroundPixel(backdrop);
                for (TextView label : labels) {
                    int foreground = label.getCurrentTextColor();
                    double contrast = ColorUtils.calculateContrast(foreground, materialPixel);
                    assertTrue(surface + " " + label.getText() + " contrast against "
                            + Integer.toHexString(backdrop) + ": " + contrast, contrast >= 4.5);
                }
            }
        }

        @Override public boolean install(String id, Method method, XposedInterface.Hooker hooker) {
            hooks.put(method, hooker);
            return true;
        }

        Object call(Object target, String name, Object... arguments) throws Throwable {
            Method method = Arrays.stream(target.getClass().getMethods())
                    .filter(candidate -> candidate.getName().equals(name)
                            && candidate.getParameterCount() == arguments.length
                            && (!name.equals("setCustomBackground") || candidate.getParameterTypes()[0] == int.class))
                    .findFirst().orElseThrow();
            XposedInterface.Hooker hook = hooks.get(method);
            if (hook == null) {
                assertFalse("Expected a registered host hook for " + name,
                        name.equals("setCustomBackground") || name.equals("selectLayout"));
                return invoke(target, method, arguments);
            }
            XposedInterface.Chain chain = (XposedInterface.Chain) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] {XposedInterface.Chain.class},
                    (proxy, called, args) -> switch (called.getName()) {
                        case "getThisObject" -> target;
                        case "getArgs" -> Arrays.asList(arguments);
                        case "getArg" -> arguments[(int) args[0]];
                        case "proceed" -> invoke(target, method, args == null || args.length == 0
                                ? arguments : (Object[]) args[0]);
                        default -> throw new UnsupportedOperationException(called.getName());
                    });
            return hook.intercept(chain);
        }

        private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
            try { return method.invoke(target, arguments); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        }

        void disable() {
            config = config.withControlCenter(config.controlCenter.withSystemSurfaces(
                    config.controlCenter.systemSurfaces.withNotifications(false).withHeadsUp(false)));
            NotificationSurfaceHooks.refresh();
        }

        @Override public void close() { disable(); }
    }

    private static final class ContrastPreview implements AutoCloseable {
        private static final int SURFACE_COLOR = 0xfff0f2f5;
        private final Path target;
        private final Bitmap image = Bitmap.createBitmap(736, 450, Bitmap.Config.ARGB_8888);
        private final Canvas canvas = new Canvas(image);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        static ContrastPreview fromEnvironment() {
            String output = System.getenv("FUSION_NOTIFICATION_CONTRAST_PREVIEW");
            return output == null || output.isBlank() ? null : new ContrastPreview(Path.of(output));
        }

        ContrastPreview(Path target) {
            this.target = target;
            canvas.drawColor(SURFACE_COLOR);
            caption("Notification contrast over a transparent OEM background", 24, 34, 23);
            caption("Before / dark", 24, 64, 16);
            caption("After / dark", 200, 64, 16);
            caption("Before / light", 376, 64, 16);
            caption("After / light", 552, 64, 16);
        }

        void draw(Fixture fixture, boolean night, boolean after) {
            int x = after ? 200 : 24;
            int y = night ? 274 : 86;
            if (!after) caption(night ? "Dark theme" : "Light theme", x, y - 5, 12);
            tile(fixture, x, y, Color.BLACK);
            tile(fixture, x + 352, y, Color.WHITE);
        }

        private void tile(Fixture fixture, int left, int top, int color) {
            int save = canvas.save();
            canvas.translate(left, top);
            canvas.clipRect(0, 0, SIZE, SIZE);
            canvas.drawColor(color);
            fixture.row.draw(canvas);
            canvas.restoreToCount(save);
        }

        private void caption(String label, int x, int y, int size) {
            paint.setColor(0xff1e2430);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(size);
            canvas.drawText(label, x, y, paint);
        }

        void write() throws IOException {
            Path absolute = target.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            try (OutputStream output = Files.newOutputStream(absolute)) {
                assertTrue("PNG compression", image.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
        }

        @Override public void close() { image.recycle(); }
    }

    private static final class HostResources extends Resources {
        private final Configuration configuration;

        @SuppressWarnings("deprecation")
        HostResources(Resources base, boolean night) {
            super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
            configuration = new Configuration(base.getConfiguration());
            setNight(night);
        }

        void setNight(boolean night) {
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        }

        @Override public Configuration getConfiguration() {
            return configuration == null ? super.getConfiguration() : configuration;
        }

        @Override public String getResourceName(int id) {
            return switch (id) {
                case OEM_BLUR_BACKGROUND -> "com.android.systemui:drawable/notification_heads_up_transparent_bg";
                case INTENTIONALLY_TRANSPARENT_BACKGROUND ->
                        "com.android.systemui:drawable/optimized_transparent_heads_up_notification_bg";
                default -> super.getResourceName(id);
            };
        }

        @Override public Drawable getDrawable(int id, Theme theme) {
            if (id != OEM_BLUR_BACKGROUND && id != INTENTIONALLY_TRANSPARENT_BACKGROUND) {
                return super.getDrawable(id, theme);
            }
            GradientDrawable transparent = new GradientDrawable();
            transparent.setColor(Color.TRANSPARENT);
            return transparent;
        }
    }
}

package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.LevelListDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

/** Rebinding uses View.setBackground; no test writes module state or its drawable links. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationBackgroundCycleRegressionTest {
    private static final int SIZE = 160;

    @Test public void twoRowsDoNotAdoptEachOthersModuleBackgroundAsTheHostOriginal() throws Exception {
        try (Fixture first = new Fixture(0xffd62e2e); Fixture second = new Fixture(0xff2969c9)) {
            first.apply();
            second.apply();
            Drawable firstMounted = first.background.getBackground();
            Drawable secondMounted = second.background.getBackground();

            // A host cache can hand an already styled background back to another row.
            second.background.setBackground(firstMounted);
            second.apply();
            first.background.setBackground(secondMounted);
            first.apply();

            assertAcyclic(first.background.getBackground());
            assertAcyclic(second.background.getBackground());
            assertEquals(first.color, first.pixel(80, 30));
            assertEquals(second.color, second.pixel(80, 30));
            first.disable();
            second.disable();
            assertSame("Cross-row module rebinding must not replace the last real host original",
                    first.original, first.background.getBackground());
            assertSame(second.original, second.background.getBackground());
        }
    }

    @Test public void aStandardWrapperAroundTheMountedBackgroundCannotBecomeItsOwnOriginal() throws Exception {
        try (Fixture fixture = new Fixture(0xffd62e2e)) {
            fixture.apply();
            Drawable hostWrapper = wrapper(fixture.background.getBackground());
            fixture.background.setBackground(hostWrapper);
            fixture.apply();

            assertAcyclic(fixture.background.getBackground());
            assertEquals(fixture.color, fixture.pixel(80, 30));
            fixture.disable();
            assertSame("A wrapper containing module output is not a new host baseline",
                    fixture.original, fixture.background.getBackground());
        }
    }

    @Test public void repeatedExpandCollapseAndReapplyKeepTheHostOriginalDrawable() throws Exception {
        try (Fixture fixture = new Fixture(0xffd62e2e)) {
            fixture.apply();
            for (int height : new int[] {160, 72, 160, 72, 160, 72}) {
                fixture.background.setActualHeight(height);
                fixture.background.setBackground(wrapper(fixture.background.getBackground()));
                fixture.apply();
                fixture.apply();

                assertAcyclic(fixture.background.getBackground());
                assertEquals(fixture.color, fixture.pixel(80, 30));
                assertEquals("Expanded and collapsed draws still use current native geometry",
                        height == SIZE ? fixture.color : 0, fixture.pixel(80, 120));
            }
            fixture.disable();
            assertSame(fixture.original, fixture.background.getBackground());
        }
    }

    @Test public void anOrdinaryHostWrapperReplacementStillWinsOnDisable() throws Exception {
        try (Fixture fixture = new Fixture(0xffd62e2e)) {
            fixture.apply();
            Drawable replacement = wrapper(new ColorDrawable(0xff2bb34b));
            fixture.background.setBackground(replacement);
            fixture.disable();

            assertSame("Real host wrapper replacements retain their identity on restore",
                    replacement, fixture.background.getBackground());
            assertAcyclic(replacement);
            assertEquals(0xff2bb34b, fixture.pixel(80, 30));
        }
    }

    @Test public void aSynchronousDisableAndReopenCannotLeaveTwoBoundsWrappersInADrawCycle() throws Exception {
        try (Fixture fixture = new Fixture(0xffd62e2e)) {
            fixture.background.afterBackgroundSet = () -> {
                fixture.disable();
                fixture.apply();
            };
            fixture.apply();
            fixture.apply();

            assertAcyclic(fixture.background.getBackground());
            assertEquals(fixture.color, fixture.pixel(80, 30));
            fixture.disable();
            assertSame(fixture.original, fixture.background.getBackground());
        }
    }

    @Test public void aReopenDuringRestoreKeepsTheNewMaterialAndCanLaterRestoreNormally() throws Exception {
        try (Fixture fixture = new Fixture(0xffd62e2e)) {
            fixture.apply();
            fixture.background.whenBackground = fixture.original;
            fixture.background.afterBackgroundSet = () -> fixture.apply(fixture.config.withControlCenter(
                    fixture.config.controlCenter.withBlur(100, 30, 100)));
            fixture.disable();

            assertTrue("Reentrant reopen must remain applied after the older restore returns",
                    SystemSurfaceStyle.isApplied(fixture.background));
            assertTrue(fixture.background.getBackground() instanceof NotificationSurfaceHooks.BoundedBackground);
            assertEquals("The old restore must not replay native requests over the reopened surface",
                    54, fixture.background.getMiBackgroundBlurRadius());
            Drawable reopened = fixture.background.getBackground();
            fixture.apply();
            assertSame("Reapplying must retain the reopened background owner", reopened,
                    fixture.background.getBackground());
            assertAcyclic(fixture.background.getBackground());
            assertEquals(fixture.color, fixture.pixel(80, 30));
            fixture.disable();
            assertFalse(SystemSurfaceStyle.isApplied(fixture.background));
            assertSame(fixture.original, fixture.background.getBackground());
        }
    }

    @Test public void aMutableHostWrapperIsRecheckedBeforeDrawingWithoutAnotherApply() throws Exception {
        try (Fixture fixture = new Fixture(0xffd62e2e)) {
            SingleEntryWrapper hostWrapper = new SingleEntryWrapper(fixture.original);
            fixture.background.setBackground(hostWrapper);
            fixture.apply();
            assertEquals(fixture.color, fixture.pixel(80, 30));
            hostWrapper.setDrawable(fixture.background.getBackground());

            fixture.pixel(80, 30);
            assertNull("A corrupted host container with no safe baseline hands drawing back to the host",
                    fixture.background.getBackground());
            assertFalse(SystemSurfaceStyle.isApplied(fixture.background));
            fixture.disable();
            assertNull(fixture.background.getBackground());
        }
    }

    @Test public void moduleOutputHiddenInFrameworkContainersCannotReplaceTheHostOriginal() throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            try (Fixture fixture = new Fixture(0xffd62e2e)) {
                fixture.apply();
                fixture.background.setBackground(container(kind, fixture.background.getBackground()));
                fixture.apply();

                assertAcyclic(fixture.background.getBackground());
                assertEquals(fixture.color, fixture.pixel(80, 30));
                fixture.disable();
                assertSame(fixture.original, fixture.background.getBackground());
            }
        }
    }

    @Test public void anImportedModuleBackgroundWithoutAKnownHostOriginalReleasesOwnership() throws Exception {
        try (Fixture donor = new Fixture(0xff2969c9); Fixture receiver = new Fixture(0xffd62e2e)) {
            donor.apply();
            receiver.background.setBackground(donor.background.getBackground());
            receiver.apply();

            assertFalse(SystemSurfaceStyle.isApplied(receiver.background));
            assertNull("A foreign module background is not invented as this row's host original",
                    receiver.background.getBackground());
            assertEquals(donor.color, donor.pixel(80, 30));
        }
    }

    @Test public void sharedOrdinaryLayerChildrenDoNotLookLikeACycle() throws Exception {
        try (Fixture fixture = new Fixture(0xffd62e2e)) {
            fixture.apply();
            Drawable shared = new ColorDrawable(0xff2bb34b);
            Drawable replacement = new LayerDrawable(new Drawable[] {shared, shared});
            fixture.background.setBackground(replacement);
            fixture.disable();

            assertSame(replacement, fixture.background.getBackground());
            assertEquals(0xff2bb34b, fixture.pixel(80, 30));
        }
    }

    @Test public void anUninspectableExternalHostContainerIsPreservedWithoutTakingOwnership() throws Exception {
        try (Fixture fixture = new Fixture(0xffd62e2e)) {
            LevelListDrawable host = new LevelListDrawable();
            host.addLevel(0, 0, new ColorDrawable(0xff2bb34b) {
                @Override public Drawable.ConstantState getConstantState() { return null; }
            });
            fixture.background.setBackground(host);
            fixture.apply();

            assertFalse("An unknown container stays host-owned", SystemSurfaceStyle.isApplied(fixture.background));
            assertSame("Declining to inspect a host container must not erase its background", host,
                    fixture.background.getBackground());
            assertEquals(0xff2bb34b, fixture.pixel(80, 30));
            fixture.disable();
            assertSame(host, fixture.background.getBackground());
        }
    }

    @Test public void anUninspectableMenuContainerRetainsItsMaterialAndRestoresTheHostObject() throws Exception {
        View menu = new View(RuntimeEnvironment.getApplication());
        LevelListDrawable host = new LevelListDrawable();
        host.addLevel(0, 0, new ColorDrawable(0xff2bb34b) {
            @Override public Drawable.ConstantState getConstantState() { return null; }
        });
        menu.setBackground(host);
        menu.layout(0, 0, SIZE, SIZE);
        ControlCenterConfig settings = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.DEFAULT).withBlur(100, 100, 100)
                .withCornerRadius(0).withGlobalBorder(false, 0);
        try {
            SystemSurfaceStyle.apply(menu, settings, true);
            assertTrue("Opaque menu containers retain the established material path", SystemSurfaceStyle.isApplied(menu));
            Bitmap image = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            try {
                menu.draw(new Canvas(image));
                assertEquals(0xff2bb34b, image.getPixel(80, 30));
            } finally { image.recycle(); }
            assertTrue(SystemSurfaceStyle.isApplied(menu));
            SystemSurfaceStyle.restore(menu);
            assertSame(host, menu.getBackground());
        } finally { SystemSurfaceStyle.restore(menu); }
    }

    private static Drawable container(int kind, Drawable module) {
        Drawable ordinary = new ColorDrawable(0xff2bb34b);
        if (kind == 0) return new LayerDrawable(new Drawable[] {ordinary, module});
        if (kind == 1) {
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[] {android.R.attr.state_pressed}, module);
            states.addState(new int[0], ordinary);
            return states;
        }
        AnimationDrawable frames = new AnimationDrawable();
        frames.addFrame(ordinary, 100);
        frames.addFrame(module, 100);
        return frames;
    }

    private static Drawable wrapper(Drawable child) {
        return new DrawableWrapper(child) { };
    }

    /** Fail on the second entry instead of letting a RED probe overflow its runner's stack. */
    private static final class SingleEntryWrapper extends DrawableWrapper {
        boolean drawing;

        SingleEntryWrapper(Drawable drawable) { super(drawable); }

        @Override public void draw(Canvas canvas) {
            assertFalse("A mutable host container must never be entered recursively", drawing);
            drawing = true;
            try { super.draw(canvas); }
            finally { drawing = false; }
        }
    }

    /** Fail before a cyclic draw consumes the test runner's stack. This only reads links. */
    private static void assertAcyclic(Drawable drawable) throws Exception {
        assertAcyclic(drawable, Collections.newSetFromMap(new IdentityHashMap<>()),
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void assertAcyclic(Drawable drawable, Set<Drawable> path, Set<Drawable> done)
            throws Exception {
        if (drawable == null || done.contains(drawable)) return;
        assertTrue("A module background was adopted as an original and formed a draw cycle at "
                + drawable.getClass().getSimpleName(), path.add(drawable));
        if (drawable instanceof NotificationSurfaceHooks.BoundedBackground bounded) {
            assertAcyclic(bounded.content, path, done);
        } else if (drawable instanceof SystemSurfaceStyle.SurfaceDrawable) {
            Field original = SystemSurfaceStyle.SurfaceDrawable.class.getDeclaredField("original");
            original.setAccessible(true);
            assertAcyclic((Drawable) original.get(drawable), path, done);
        } else if (drawable instanceof DrawableWrapper hostWrapper) {
            assertAcyclic(hostWrapper.getDrawable(), path, done);
        } else if (drawable instanceof LayerDrawable layers) {
            for (int index = 0; index < layers.getNumberOfLayers(); index++)
                assertAcyclic(layers.getDrawable(index), path, done);
        } else if (drawable instanceof StateListDrawable states) {
            for (int index = 0; index < states.getStateCount(); index++)
                assertAcyclic(states.getStateDrawable(index), path, done);
        } else if (drawable instanceof AnimationDrawable frames) {
            for (int index = 0; index < frames.getNumberOfFrames(); index++)
                assertAcyclic(frames.getFrame(index), path, done);
        }
        path.remove(drawable);
        done.add(drawable);
    }

    private static final class Fixture implements AutoCloseable {
        final NotificationRow row;
        final NotificationBackgroundView background;
        final ColorDrawable original;
        final int color;
        final FusionConfig config = FusionConfig.defaults().withControlCenter(
                ControlCenterConfig.defaults().withEnabled(true).withSurface(ControlCenterSurface.DEFAULT)
                        .withBlur(100, 100, 100).withCornerRadius(0).withGlobalBorder(false, 0));

        Fixture(int color) throws Exception {
            this.color = color;
            Context context = RuntimeEnvironment.getApplication();
            row = new NotificationRow(context);
            background = row.mBackgroundNormal;
            original = new ColorDrawable(color);
            background.setBackground(original);
            row.measure(View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(SIZE, View.MeasureSpec.EXACTLY));
            row.layout(0, 0, SIZE, SIZE);
            // This fixture deliberately has no independent OEM mBackground shadow field.
            assertSame(View.class, RuntimeReflection.field(background.getClass(), "mBackground")
                    .getDeclaringClass());
        }

        void apply() { apply(config); }

        void apply(FusionConfig chosen) { NotificationSurfaceHooks.applyRow(row, chosen); }

        int pixel(int x, int y) {
            Bitmap image = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            try {
                background.draw(new Canvas(image));
                return image.getPixel(x, y);
            } finally { image.recycle(); }
        }

        void disable() {
            NotificationSurfaceHooks.applyRow(row, config.withControlCenter(config.controlCenter
                    .withSystemSurfaces(config.controlCenter.systemSurfaces
                            .withNotifications(false).withHeadsUp(false))));
        }

        @Override public void close() { disable(); }
    }

    public static final class NotificationRow extends FrameLayout {
        final NotificationBackgroundView mBackgroundNormal;

        NotificationRow(Context context) {
            super(context);
            mBackgroundNormal = new NotificationBackgroundView(context);
            addView(mBackgroundNormal, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    public static final class NotificationBackgroundView extends View {
        int mActualHeight = 72;
        int mActualWidth = -1;
        Drawable whenBackground;
        Runnable afterBackgroundSet;
        int blurRadius = 180;

        NotificationBackgroundView(Context context) { super(context); }

        @Override public void setBackground(Drawable background) {
            super.setBackground(background);
            Runnable callback = afterBackgroundSet;
            if (callback != null && (whenBackground == null || whenBackground == background)) {
                afterBackgroundSet = null;
                callback.run();
            }
        }

        public void setActualHeight(int height) {
            mActualHeight = height;
            invalidate();
        }

        public int getMiBackgroundBlurRadius() { return blurRadius; }
        public void setMiBackgroundBlurRadius(int radius) { blurRadius = radius; }
    }
}

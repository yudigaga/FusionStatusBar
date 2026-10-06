package com.xtjm.fusionstatusbar;

import android.graphics.drawable.ColorDrawable;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterMaterialSessionTest {
    @Before public void resetBeforeTest() { ControlCenterMaterials.clearForTest(); }
    @After public void clear() { ControlCenterMaterials.clearForTest(); }

    @Test public void componentBackgroundAndShapeClipShareOneReversibleSession() throws Exception {
        MediaPlayerPanel panel = new MediaPlayerPanel();
        ColorDrawable nativeBackground = new ColorDrawable(0xff808080);
        ViewOutlineProvider nativeOutline = new ViewOutlineProvider() {
            @Override public void getOutline(android.view.View view, Outline outline) { }
        };
        panel.setBackground(nativeBackground);
        panel.setOutlineProvider(nativeOutline);
        panel.setClipToOutline(true);
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.component(
                ControlCenterComponentSpec.MEDIA, 0, 0, 2, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 18);
        ControlCenterConfig config = ControlCenterConfig.defaults().withEnabled(true)
                .withSurface(ControlCenterSurface.DARK);

        assertTrue(ControlCenterMaterials.syncComponentView(panel, item, config));
        Map<?, ?> sessions = sessions();
        assertEqualsOne(sessions);
        ControlCenterMaterialSession session = (ControlCenterMaterialSession) sessions.get(panel);
        assertNotNull(session);
        assertNotNull(session.componentBackground);
        assertNotNull(session.shapeClip);
        assertSame(ViewOutlineProvider.class, session.shapeClip.applied.getClass().getSuperclass());
        assertSame(session.componentBackground, nativeBackground.getCallback());

        ControlCenterMaterials.releaseGridSurface(panel, config.withEnabled(false));
        assertTrue("release removes the single ownership record", !sessions().containsKey(panel));
        assertSame(nativeBackground, panel.getBackground());
        assertSame("View background callback is restored with the native drawable", panel,
                nativeBackground.getCallback());
        assertTrue(panel.getClipToOutline());
        assertSame(nativeOutline, panel.getOutlineProvider());
        ControlCenterMaterials.releaseGridSurface(panel, config.withEnabled(false));
        assertSame(nativeBackground, panel.getBackground());
        assertSame(nativeOutline, panel.getOutlineProvider());
    }

    @Test public void cardBackgroundAndShapeClipShareOneReversibleSession() throws Exception {
        MediaPlayerPanel panel = new MediaPlayerPanel();
        ColorDrawable nativeBackground = new ColorDrawable(0xff808080);
        ViewOutlineProvider nativeOutline = new ViewOutlineProvider() {
            @Override public void getOutline(android.view.View view, Outline outline) { }
        };
        panel.setBackground(nativeBackground);
        panel.setOutlineProvider(nativeOutline);
        panel.setClipToOutline(true);
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1)
                .withShape(ControlCenterLayoutPlan.Shape.CAPSULE, 18);

        assertTrue(ControlCenterMaterials.syncCardView(panel, item));
        Map<?, ?> sessions = sessions();
        assertEqualsOne(sessions);
        ControlCenterMaterialSession session = (ControlCenterMaterialSession) sessions.get(panel);
        assertNotNull(session);
        assertNotNull(session.cardBackground);
        assertNotNull(session.shapeClip);
        assertSame(nativeBackground, session.cardBackground.nativeDrawable);
        assertSame(session.cardBackground, nativeBackground.getCallback());

        ControlCenterMaterials.releaseGridSurface(panel, ControlCenterConfig.defaults());
        assertTrue("release removes the single ownership record", !sessions().containsKey(panel));
        assertSame(nativeBackground, panel.getBackground());
        assertSame(panel, nativeBackground.getCallback());
        assertSame(nativeOutline, panel.getOutlineProvider());
        assertTrue(panel.getClipToOutline());
        ControlCenterMaterials.releaseGridSurface(panel, ControlCenterConfig.defaults());
        assertSame(nativeBackground, panel.getBackground());
        assertSame(nativeOutline, panel.getOutlineProvider());
    }

    @Test public void cardSyncAfterNativeReplacementRestoresCurrentDrawableAndOldCallback() throws Exception {
        MediaPlayerPanel panel = new MediaPlayerPanel();
        ColorDrawable original = new ColorDrawable(0xff808080);
        panel.setBackground(original);
        ControlCenterLayoutPlan.Item item = ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 2, 1);
        ControlCenterMaterials.syncCardView(panel, item);

        ColorDrawable replacement = new ColorDrawable(0xff404040);
        panel.setBackground(replacement);
        ControlCenterMaterials.syncCardView(panel, item);
        ControlCenterMaterialSession session = (ControlCenterMaterialSession) sessions().get(panel);
        assertNotNull(session);
        assertSame(replacement, session.cardBackground.nativeDrawable);
        assertSame("A displaced OEM drawable no longer points at the old wrapper", panel, original.getCallback());

        ControlCenterMaterials.releaseGridSurface(panel, ControlCenterConfig.defaults());
        assertSame(replacement, panel.getBackground());
        assertSame(panel, replacement.getCallback());
        assertSame(panel, original.getCallback());
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> sessions() throws Exception {
        Field field = ControlCenterMaterials.class.getDeclaredField("MATERIAL_SESSIONS");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(null);
    }

    private static void assertEqualsOne(Map<?, ?> values) {
        assertTrue("one view has one material owner", values.size() == 1);
    }

    public static final class MediaPlayerPanel extends FrameLayout {
        MediaPlayerPanel() { super(RuntimeEnvironment.getApplication()); }
    }
}

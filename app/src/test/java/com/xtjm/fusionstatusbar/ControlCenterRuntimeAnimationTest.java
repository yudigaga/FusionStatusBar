package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ControlCenterRuntimeAnimationTest {
    private ActivityController<Activity> activity;
    private TrackingHost host;
    private NativeHolder holder;
    private Object state;
    private ControlCenterRuntimeGrid.GridView grid;
    private final Adapter adapter = new Adapter();
    private View originalChild;

    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().visible();
        host = new TrackingHost(activity.get());
        originalChild = new View(activity.get());
        host.addView(originalChild);
        activity.get().setContentView(host, new FrameLayout.LayoutParams(400, 80));
        holder = new NativeHolder(host);
        Class<?> type = Class.forName(ControlCenterRuntimeGrid.class.getName() + "$GridState");
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        // Empty native sources isolate the actual mount/lifecycle path from OEM record creation.
        state = constructor.newInstance(adapter, new Object(), new Object(), Item.class,
                ControlCenterConfig.defaults(), false, 4, Collections.emptyList(),
                Collections.emptyMap(), Collections.emptyMap(), 0, 4);
        Field outerHolder = type.getDeclaredField("outerHolder");
        outerHolder.setAccessible(true);
        outerHolder.set(state, holder);
        grid = new ControlCenterRuntimeGrid.GridView(activity.get(), 4, 8, 24, 0, 4);
        invoke("mount", new Class<?>[] {android.view.ViewGroup.class, ControlCenterRuntimeGrid.GridView.class,
                ArrayList.class}, host, grid, new ArrayList<>());
    }

    @After public void cleanup() throws Exception {
        if (state != null) invoke("unbind", new Class<?>[0]);
        if (activity != null) activity.pause().stop().destroy();
    }

    @Test public void gridCarrierFollowsNativeExpandAndCollapseFrames() {
        holder.frame(0.45f, 0.85f, 64f);
        assertEquals(0.45f, host.getAlpha(), 0f);
        assertEquals(0.85f, host.getScaleX(), 0f);
        assertEquals(64f, host.getTranslationY(), 0f);
        holder.frame(0f, 0.65f, 180f);
        assertEquals("The grid must fade with the native holder, not wait for window removal", 0f, host.getAlpha(), 0f);
        holder.frame(1f, 1f, 0f);
        assertEquals(1f, host.getAlpha(), 0f);
        assertEquals(0f, host.getTranslationY(), 0f);
    }

    @Test public void delayedMountWorkCannotResurrectOrResetACollapsingPanel() {
        host.setAlpha(0.2f);
        host.setScaleX(0.75f);
        host.setScaleY(0.75f);
        host.setTranslationY(120f);
        host.setVisibility(View.INVISIBLE);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        assertEquals(View.INVISIBLE, host.getVisibility());
        assertEquals(0.2f, host.getAlpha(), 0f);
        assertEquals(0.75f, host.getScaleX(), 0f);
        assertEquals(120f, host.getTranslationY(), 0f);
    }

    @Test public void unchangedHeightDoesNotRequestAnotherLayout() throws Exception {
        host.requests = 0;
        invoke("enforceOuterHeight", new Class<?>[] {android.view.ViewGroup.class,
                ControlCenterRuntimeGrid.GridView.class}, host, grid);
        assertEquals(0, host.requests);
    }

    @Test public void unbindRestoresHierarchyWithoutReplayingOldAnimationValues() throws Exception {
        holder.frame(0.15f, 0.7f, 92f);
        invoke("unbind", new Class<?>[0]);
        assertEquals(1, host.getChildCount());
        assertSame(originalChild, host.getChildAt(0));
        assertEquals(80, host.getLayoutParams().height);
        assertEquals(0.15f, host.getAlpha(), 0f);
        assertEquals(0.7f, host.getScaleX(), 0f);
        assertEquals(92f, host.getTranslationY(), 0f);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        assertEquals(80, host.getLayoutParams().height);
        assertEquals(0.15f, host.getAlpha(), 0f);
    }

    @Test public void attachFreezesOnlyNestedHoldersAndRestoresThemOnUnbind() throws Exception {
        NativeHolder nested = new NativeHolder(new FrameLayout(activity.get()));
        Class<?> bindingType = Class.forName(ControlCenterRuntimeGrid.class.getName() + "$TileBinding");
        Constructor<?> constructor = bindingType.getDeclaredConstructor(Object.class, Object.class);
        constructor.setAccessible(true);
        Object binding = constructor.newInstance(new Record(), nested);
        Field field = state.getClass().getDeclaredField("bindings");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") ArrayList<Object> bindings = (ArrayList<Object>) field.get(state);
        bindings.add(binding);
        grid.addTile(nested.itemView, ControlCenterLayoutPlan.Item.tile("wifi", 0, 0, 1, 1), 0);
        holder.frame(0.4f, 0.8f, 50f);
        invoke("attach", new Class<?>[0]);
        assertTrue(nested.attached);
        assertTrue(nested.getIgnoreHolderAlpha());
        assertTrue(nested.getIgnoreHolderScale());
        assertFalse(holder.getIgnoreHolderAlpha());
        assertFalse(holder.getIgnoreHolderScale());
        assertFalse(holder.getIgnoreHolderTranslation());
        assertEquals(0.4f, host.getAlpha(), 0f);
        assertEquals(50f, host.getTranslationY(), 0f);
        nested.frame(0f, 0.65f, 180f);
        assertEquals(1f, nested.itemView.getAlpha(), 0f);
        assertEquals(0f, nested.itemView.getTranslationY(), 0f);
        invoke("unbind", new Class<?>[0]);
        assertFalse(nested.attached);
        assertFalse(nested.getIgnoreHolderAlpha());
        assertFalse(nested.getIgnoreHolderScale());
        assertFalse(nested.getIgnoreHolderTranslation());
    }

    @Test public void restoreFailureDoesNotSkipRecordsAppTilesOrRemainingViewState() throws Exception {
        ThrowingRecord record = new ThrowingRecord();
        Field recordsField = state.getClass().getDeclaredField("records");
        recordsField.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<String, Object> records =
                (java.util.Map<String, Object>) recordsField.get(state);
        records.put("wifi", record);
        Field appField = state.getClass().getDeclaredField("appTiles");
        appField.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<String, ControlCenterAppTileSource.Lease> leases =
                (java.util.Map<String, ControlCenterAppTileSource.Lease>) appField.get(state);
        int[] released = {0};
        leases.put("broken", new ControlCenterAppTileSource.Lease(new Object(),
                () -> { throw new IllegalStateException("OEM destroy failed"); }));
        leases.put("healthy", new ControlCenterAppTileSource.Lease(new Object(), () -> released[0]++));
        host.failBackground = true;
        invoke("dispose", new Class<?>[0]);
        assertSame(originalChild, host.getChildAt(0));
        assertEquals(80, host.getLayoutParams().height);
        assertEquals(1, record.removedCallbacks);
        assertEquals(1, released[0]);
        assertTrue(records.isEmpty());
        assertTrue(leases.isEmpty());
        invoke("dispose", new Class<?>[0]);
        assertEquals(1, record.removedCallbacks);
        assertEquals(1, released[0]);
        host.failBackground = false;
    }

    public static final class ThrowingRecord {
        int removedCallbacks;
        public void setListening(boolean value) { throw new IllegalStateException("OEM listener failed"); }
        public void removeCallback() { removedCallbacks++; }
    }

    private void invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = state.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(state, args);
    }

    public interface Item { int getType(); }

    public static final class Adapter { public final Object frameCallback = new Object(); }
    public static final class Record {
        public void onViewAttachedToWindow() { }
        public void onViewDetachedFromWindow() { }
        public void setListening(boolean value) { }
        public void onUnbindViewHolder() { }
        public void setHolder(Object value) { }
    }

    static final class TrackingHost extends FrameLayout {
        int requests;
        boolean failBackground;
        TrackingHost(Activity activity) { super(activity); }
        @Override public void requestLayout() { requests++; super.requestLayout(); }
        @Override public void setBackground(android.graphics.drawable.Drawable background) {
            if (failBackground) throw new IllegalStateException("OEM background restore failed");
            super.setBackground(background);
        }
    }

    public static final class NativeHolder {
        public final FrameLayout itemView;
        private boolean alpha, scale, translation;
        boolean attached;
        NativeHolder(FrameLayout view) { itemView = view; }
        public boolean getIgnoreHolderAlpha() { return alpha; }
        public boolean getIgnoreHolderScale() { return scale; }
        public boolean getIgnoreHolderTranslation() { return translation; }
        public void setIgnoreHolderAlpha(boolean value) { alpha = value; }
        public void setIgnoreHolderScale(boolean value) { scale = value; }
        public void setIgnoreHolderTranslation(boolean value) { translation = value; }
        public void setAttached$miui_controlcenter_release(boolean value) { attached = value; }
        public void onViewAttachedToWindow(Object adapter, Object callback) { }
        public void onViewDetachedFromWindow() { }
        public void setItem$miui_controlcenter_release(Object value) { }
        public void setOwner(Object value) { }
        public void recycle() { }
        void frame(float a, float s, float y) {
            if (!alpha) itemView.setAlpha(a);
            if (!scale) { itemView.setScaleX(s); itemView.setScaleY(s); }
            if (!translation) itemView.setTranslationY(y);
        }
    }
}

package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControlCenterGroupRuntimeTest {
    @Test public void sevenRealHolderBindingsAttachClickAndReleaseExactlyOnce() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ControlCenterGroupData data = ControlCenterGroupData.empty();
        LinkedHashMap<String, Object> tiles = new LinkedHashMap<>();
        for (String spec : List.of("wifi", "bt", "cell", "rotation", "mute", "nfc", "airplane")) {
            data = data.add(spec); tiles.put(spec, spec);
        }
        var group = ControlCenterLayoutPlan.Item.group("group:test", data.arrange(ControlCenterGroupData.Arrangement.MIXED), 0, 0, 2, 2);
        Owner owner = new Owner(); Factory factory = new Factory(); Adapter adapter = new Adapter();
        Class<?> type = Class.forName(ControlCenterRuntimeGrid.class.getName() + "$GridState");
        Constructor<?> constructor = type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        Object state = constructor.newInstance(adapter, owner, factory, Item.class, ControlCenterConfig.defaults(),
                false, 4, List.of(group), tiles, Collections.emptyMap(), 0, 2);
        Holder carrier = new Holder(new FrameLayout(activity));
        Field field = type.getDeclaredField("outerHolder"); field.setAccessible(true); field.set(state, carrier);
        try {
            invoke(state, "bind");
            assertEquals(1, carrier.itemView.getChildCount());
            ViewGroup grid = (ViewGroup) carrier.itemView.getChildAt(0);
            assertEquals("No duplicate group background", 1, grid.getChildCount());
            ControlCenterGroupView surface = (ControlCenterGroupView) grid.getChildAt(0);
            assertEquals(7, surface.getChildCount());
            assertEquals(7, factory.records.size());
            invoke(state, "attach");
            for (int i = 0; i < 7; i++) {
                Holder holder = owner.holders.get(i);
                assertTrue(holder.attached);
                assertTrue(holder.ignoreAlpha && holder.ignoreScale && holder.ignoreTranslation);
                assertEquals(1f, holder.itemView.getAlpha(), 0f);
                surface.getChildAt(i).performClick();
                surface.getChildAt(i).performLongClick();
                assertEquals(1, holder.clicks);
                assertEquals(1, holder.longClicks);
                assertEquals(1, factory.records.get(i).attaches);
            }
            assertFalse(carrier.ignoreAlpha || carrier.ignoreScale || carrier.ignoreTranslation);
            invoke(state, "unbind");
            for (Holder holder : owner.holders) {
                assertFalse(holder.attached || holder.ignoreAlpha || holder.ignoreScale || holder.ignoreTranslation);
                assertEquals(1, holder.recycles);
            }
            for (QSRecord record : factory.records) { assertNull(record.holder); assertEquals(1, record.unbinds); }
        } finally { invoke(state, "dispose"); }
    }
    private void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
    public interface Item { int getType(); }
    public static class Adapter { public Object frameCallback = new Object(); }
    public static class Factory {
        final ArrayList<QSRecord> records = new ArrayList<>();
        public QSRecord create(Object tile, boolean card) { assertFalse(card); QSRecord record = new QSRecord(); records.add(record); return record; }
    }
    public static class QSRecord {
        Object holder; int attaches, unbinds;
        public void setAdded(boolean value) { }
        public int getType() { return 8453; }
        public void setHolder(Object value) { holder = value; }
        public void onViewAttachedToWindow() { attaches++; }
        public void onViewDetachedFromWindow() { }
        public void onUnbindViewHolder() { unbinds++; }
        public void setListening(boolean value) { }
        public void removeCallback() { }
    }
    public static class Owner {
        final ArrayList<Holder> holders = new ArrayList<>();
        public Holder createViewHolder(ViewGroup parent, int type) { Holder holder = new Holder(new FrameLayout(parent.getContext())); holders.add(holder); return holder; }
        public void onBindViewHolder(Holder holder, QSRecord record) {
            holder.itemView.setAlpha(0);
            holder.itemView.setOnClickListener(v -> holder.clicks++);
            holder.itemView.setOnLongClickListener(v -> { holder.longClicks++; return true; });
        }
    }
    public static class Holder {
        public final FrameLayout itemView;
        int clicks, longClicks, recycles;
        boolean attached, ignoreAlpha, ignoreScale, ignoreTranslation;
        Holder(FrameLayout view) { itemView = view; }
        public void init() { }
        public void setOwner(Object value) { }
        public void setItem$miui_controlcenter_release(Object value) { }
        public boolean getAttached$miui_controlcenter_release() { return attached; }
        public void setAttached$miui_controlcenter_release(boolean value) { attached = value; }
        public void onViewAttachedToWindow(Object adapter, Object callback) { }
        public void onViewDetachedFromWindow() { }
        public boolean getIgnoreHolderAlpha() { return ignoreAlpha; }
        public boolean getIgnoreHolderScale() { return ignoreScale; }
        public boolean getIgnoreHolderTranslation() { return ignoreTranslation; }
        public void setIgnoreHolderAlpha(boolean value) { ignoreAlpha = value; }
        public void setIgnoreHolderScale(boolean value) { ignoreScale = value; }
        public void setIgnoreHolderTranslation(boolean value) { ignoreTranslation = value; }
        public void recycle() { recycles++; }
    }
}

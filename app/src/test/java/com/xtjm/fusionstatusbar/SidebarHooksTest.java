package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SidebarHooksTest {
    @Test public void leftAndRightHandlesReceiveOppositeNativeDragSequences() {
        RecordingImage handle = new RecordingImage(RuntimeEnvironment.getApplication());
        handle.layout(0, 0, 12, 100);
        ColorDrawable nativeDrawable = new ColorDrawable(0xff123456);
        handle.setImageDrawable(nativeDrawable);
        SidebarHooks.show(handle, 0);
        assertEquals(List.of(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP),
                handle.actions);
        assertTrue(handle.x.get(1) > handle.x.get(0));
        assertEquals(handle.x.get(1), handle.x.get(2));
        assertSame(nativeDrawable, handle.getDrawable());
        handle.actions.clear();
        handle.x.clear();
        SidebarHooks.show(handle, 1);
        assertEquals(List.of(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP),
                handle.actions);
        assertTrue(handle.x.get(1) < handle.x.get(0));
        assertSame(nativeDrawable, handle.getDrawable());
    }

    @Test public void samplingImagesMustBelongToTheSidebarPackageAndHaveHandleGeometry() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout parent = new FrameLayout(activity);
        activity.setContentView(parent);
        ImageView unrelated = new ImageView(activity);
        parent.addView(unrelated);
        unrelated.layout(0, 0, 12, 100);
        assertFalse(SidebarHooks.isSidebarHandle(unrelated));

        Context sidebarContext = new ContextWrapper(activity) {
            @Override public String getPackageName() { return "com.miui.securitycenter"; }
        };
        ImageView handle = new ImageView(sidebarContext);
        parent.addView(handle);
        handle.layout(0, 0, 12, 100);
        assertTrue(SidebarHooks.isSidebarHandle(handle));
        handle.layout(0, 0, 200, 200);
        assertFalse(SidebarHooks.isSidebarHandle(handle));
        parent.removeView(handle);
        assertFalse(SidebarHooks.isSidebarHandle(handle));
    }

    private static final class RecordingImage extends ImageView {
        final List<Integer> actions = new ArrayList<>();
        final List<Float> x = new ArrayList<>();

        RecordingImage(Context context) { super(context); }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            actions.add(event.getActionMasked());
            x.add(event.getX());
            return true;
        }
    }
}

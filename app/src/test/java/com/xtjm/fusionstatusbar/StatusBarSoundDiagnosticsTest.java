package com.xtjm.fusionstatusbar;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class StatusBarSoundDiagnosticsTest {
    private static final int STATE_TAG = 0x7f0b0020;

    private final List<String> messages = new ArrayList<>();
    private ActivityController<Activity> activity;
    private Context hostContext;
    private FrameLayout root;
    private StatusBarSoundDiagnostics diagnostics;

    @Before @SuppressWarnings("deprecation") public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup();
        Activity context = activity.get();
        Resources base = context.getResources();
        Resources hostResources = new Resources(
                base.getAssets(), base.getDisplayMetrics(), base.getConfiguration()) {
            @Override public int getIdentifier(String name, String type, String pkg) {
                return "status_bar_view_state_tag".equals(name) && "id".equals(type)
                        && "com.android.systemui".equals(pkg)
                        ? STATE_TAG : super.getIdentifier(name, type, pkg);
            }
        };
        hostContext = new ContextWrapper(context) {
            @Override public Resources getResources() {
                return hostResources;
            }
        };
        root = new FrameLayout(context);
        context.setContentView(root);
        diagnostics = new StatusBarSoundDiagnostics(
                new Handler(Looper.getMainLooper()), messages::add);
    }

    @After public void tearDown() {
        activity.pause().stop().destroy();
    }

    @Test public void capturesLatestPolicyAndBothPhoneCopiesInOneBurstWithoutChangingViews() {
        HostContainer home = container();
        HostContainer app = container();
        HostIcon shadowed = icon(home, "volume");
        shadowed.setAlpha(0.25f);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(16, 16);
        position.leftMargin = 4;
        position.topMargin = 2;
        shadowed.setLayoutParams(position);
        shadowed.setTag(STATE_TAG, new HostLayoutState());
        home.ignoredSlots.add("volume");
        HostIcon visible = icon(app, "volume");
        visible.blocked = false;
        visible.visibleState = 0;
        HostPolicy policy = new HostPolicy();

        diagnostics.track(home, "phone-home", 40L);
        diagnostics.track(app, "phone-app", 40L);
        diagnostics.policyUpdated(policy, 41L);
        policy.mRingerModeTracker.ringerModeInternal.value = 0;
        diagnostics.policyUpdated(policy, 42L);
        assertTrue(messages.isEmpty());
        settle();

        assertEquals(2, messages.size());
        String homeMessage = surface("phone-home");
        assertTrue(homeMessage.contains("revision=42"));
        assertTrue(homeMessage.contains("ringerInternal=0"));
        assertTrue(homeMessage.contains("zenMode=1"));
        assertTrue(homeMessage.contains("policyVolume=true"));
        assertTrue(homeMessage.contains("slot=volume"));
        assertTrue(homeMessage.contains("layoutFrom=0"));
        assertTrue(homeMessage.contains("ignored=true"));
        assertTrue(homeMessage.contains("blocked=true"));
        assertTrue(homeMessage.contains("bounds=4,2,20,18"));
        assertTrue(homeMessage.contains("alpha=0.25"));
        assertTrue(homeMessage.contains("hiddenBySpace=true"));
        assertTrue(homeMessage.contains("inIslandState=10"));
        assertTrue(surface("phone-app").contains("blocked=false"));
        assertEquals(List.of("volume"), home.ignoredSlots);
        assertEquals(0.25f, shadowed.getAlpha(), 0f);
        assertEquals(View.VISIBLE, shadowed.getVisibility());
        assertEquals(4, shadowed.getLeft());
        assertEquals(20, shadowed.getRight());
        assertSame(home, shadowed.getParent());
        assertTrue(shadowed.blocked);
        assertEquals(2, shadowed.visibleState);

        diagnostics.policyUpdated(policy, 42L);
        settle();
        assertEquals(2, messages.size());
    }

    @Test public void retainsRawSlotsAndPrioritizesPolicySlotsInBoundedOutput() {
        HostContainer container = container();
        icon(container, "vendor_custom_scene");
        for (int i = 0; i < 24; i++) icon(container, "oem_extra_" + i);
        icon(container, "volume");
        icon(container, "oem_zen");
        diagnostics.track(container, "keyguard", 7L);
        diagnostics.policyUpdated(new HostPolicy(), 7L);
        settle();

        String message = surface("keyguard");
        assertTrue(message.contains("slot=vendor_custom_scene"));
        assertTrue(message.contains("slot=volume"));
        assertTrue(message.contains("slot=oem_zen"));
        assertFalse(message.contains("slot=oem_extra_23"));
        assertTrue(message.contains("omitted="));
        assertTrue(message.length() < 3800);
        assertEquals(27, container.getChildCount());
    }

    @Test public void detachedAndExpiredContainersDoNotPolluteLaterSnapshots() {
        for (int i = 0; i < 10; i++) {
            HostContainer container = container();
            icon(container, "slot_" + i);
            diagnostics.track(container, "test-" + i, 7L);
        }
        HostContainer detached = container();
        icon(detached, "detached_slot");
        diagnostics.track(detached, "detached", 7L);
        root.removeView(detached);
        settle();

        assertTrue(messages.size() <= 6);
        assertFalse(messages.stream().anyMatch(l -> l.contains("surface=test-0 ")));
        assertFalse(messages.stream().anyMatch(l -> l.contains("surface=detached")));
        String recent = surface("test-9");
        assertTrue(recent.contains("ringerInternal=?"));
        assertTrue(recent.contains("hiddenBySpace=?"));
        assertTrue(recent.contains("slot=slot_9"));
    }

    private HostContainer container() {
        HostContainer view = new HostContainer(hostContext);
        root.addView(view);
        assertTrue(view.isAttachedToWindow());
        return view;
    }

    private HostIcon icon(HostContainer parent, String slot) {
        HostIcon icon = new HostIcon(hostContext, slot);
        parent.addView(icon);
        return icon;
    }

    private void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
    }

    private String surface(String name) {
        return messages.stream().filter(s -> s.contains("surface=" + name + " "))
                .findFirst().orElseThrow(() -> new AssertionError("Missing " + name + " in " + messages));
    }

    private static final class HostContainer extends FrameLayout {
        final List<String> ignoredSlots = new ArrayList<>();

        HostContainer(Context context) {
            super(context);
        }

        int getLayoutFrom() {
            return 0;
        }
    }

    private static final class HostIcon extends View {
        private final String slot;
        boolean blocked = true;
        int visibleState = 2;

        HostIcon(Context context, String slot) {
            super(context);
            this.slot = slot;
        }

        String getSlot() { return slot; }
        boolean isIconVisible() { return true; }
        boolean isIconBlocked() { return blocked; }
        int getVisibleState() { return visibleState; }
    }

    private static final class HostLayoutState {
        final boolean hiddenBySpace = true;
        final int visibleState = 2;
        final int inIslandState = 10;
    }

    private static final class HostPolicy {
        final String mSlotMute = "volume";
        final String mSlotZen = "oem_zen";
        final boolean mMuteVisible = true;
        final boolean mZenVisible = false;
        final boolean mVibrateWhenSilent = true;
        final HostRingerModeTracker mRingerModeTracker = new HostRingerModeTracker();
        final HostZenController mZenController = new HostZenController();
    }

    private static final class HostRingerModeTracker {
        final HostLiveData ringerModeInternal = new HostLiveData();
    }

    private static final class HostLiveData {
        int value = 1;
        Integer getValue() { return value; }
    }

    private static final class HostZenController {
        final int mZenMode = 1;
    }
}

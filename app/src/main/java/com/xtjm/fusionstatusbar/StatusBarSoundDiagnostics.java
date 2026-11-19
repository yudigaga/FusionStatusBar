package com.xtjm.fusionstatusbar;

import android.os.Handler;
import android.os.SystemClock;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Read-only status icon snapshots following mount or sound/icon policy updates. */
final class StatusBarSoundDiagnostics {
    private static final int MAX_CONTAINERS = 6;
    private static final int MAX_SCANNED_CHILDREN = 64;
    private static final int MAX_REPORTED_SLOTS = 10;
    private static final int MAX_ANCESTORS = 12;
    private static final long SETTLE_MS = 250L;
    private static final Class<?>[] NO_ARGS = new Class<?>[0];

    private record Tracked(WeakReference<ViewGroup> view, String surface) { }
    private record SlotChild(View view, String slot) { }

    private final Handler handler;
    private final Consumer<String> logger;
    private final Runnable captureTask = this::capture;
    private final List<Tracked> containers = new ArrayList<>();
    private WeakReference<Object> policy = new WeakReference<>(null);
    private boolean pending;
    private boolean policyTriggered;
    private Integer pendingIconMask;
    private long revision;
    private List<String> lastReport = List.of();

    StatusBarSoundDiagnostics(Handler handler, Consumer<String> logger) {
        this.handler = handler;
        this.logger = logger;
    }

    synchronized void track(ViewGroup container, String surface, long revision) {
        if (container == null) return;
        containers.removeIf(entry -> entry.view.get() == null);
        for (int i = 0; i < containers.size(); i++) {
            Tracked tracked = containers.get(i);
            if (tracked.view.get() != container) continue;
            if (!tracked.surface.equals(surface)) {
                containers.set(i, new Tracked(tracked.view, token(surface)));
            }
            requestCapture(revision);
            return;
        }
        if (containers.size() >= MAX_CONTAINERS) containers.remove(0);
        containers.add(new Tracked(new WeakReference<>(container), token(surface)));
        requestCapture(revision);
    }

    synchronized void policyUpdated(Object policy, long revision) {
        this.policy = new WeakReference<>(policy);
        policyTriggered = true;
        requestCapture(revision);
    }

    synchronized void iconMaskChanged(int mask, long revision, boolean spacingChanged) {
        if (!spacingChanged && pendingIconMask == null) return;
        pendingIconMask = mask;
        handler.removeCallbacks(captureTask);
        pending = false;
        requestCapture(revision);
    }

    private void requestCapture(long revision) {
        this.revision = revision;
        if (pending) return;
        pending = handler.postDelayed(captureTask, SETTLE_MS);
    }

    private void capture() {
        List<Tracked> tracked;
        Object currentPolicy;
        String trigger;
        Integer iconMask;
        long capturedRevision;
        synchronized (this) {
            pending = false;
            tracked = List.copyOf(containers);
            currentPolicy = policy.get();
            capturedRevision = revision;
            iconMask = pendingIconMask;
            trigger = iconMask != null ? "icon-mask" : policyTriggered ? "policy" : "mount";
            pendingIconMask = null;
            policyTriggered = false;
        }

        String volumeSlot = string(field(currentPolicy, "mSlotMute"));
        String zenSlot = string(field(currentPolicy, "mSlotZen"));
        Object tracker = field(currentPolicy, "mRingerModeTracker");
        String policySummary = " revision=" + capturedRevision
                + " ringerInternal=" + scalar(call(field(tracker, "ringerModeInternal"), "getValue"))
                + " vibrateWhenSilent=" + scalar(field(currentPolicy, "mVibrateWhenSilent"))
                + " zenMode=" + scalar(field(field(currentPolicy, "mZenController"), "mZenMode"))
                + " volumeSlot=" + token(volumeSlot)
                + " zenSlot=" + token(zenSlot)
                + " policyVolume=" + scalar(field(currentPolicy, "mMuteVisible"))
                + " policyZen=" + scalar(field(currentPolicy, "mZenVisible"));
        if (iconMask != null) policySummary += " iconMask=0x" + Integer.toHexString(iconMask);
        List<String> report = new ArrayList<>();
        for (Tracked entry : tracked) {
            ViewGroup container = entry.view.get();
            if (container == null || !container.isAttachedToWindow()) continue;
            String surface = " surface=" + entry.surface;
            try {
                report.add(policySummary + surface
                        + describe(container, volumeSlot, zenSlot, iconMask != null));
            } catch (RuntimeException | LinkageError ignored) {
                report.add(policySummary + surface + " container=" + identity(container)
                        + " snapshot=unavailable");
            }
        }
        if (report.isEmpty()) report.add(policySummary + " surface=none");
        if (report.equals(lastReport)) return;
        lastReport = report;
        long uptime = SystemClock.uptimeMillis();
        for (String line : report) {
            logger.accept("status-bar sound uptime=" + uptime + " trigger=" + trigger + line);
        }
    }

    private static String describe(ViewGroup group, String volumeSlot, String zenSlot,
            boolean captureSpacing) {
        Object ignoredValue = field(group, "ignoredSlots");
        if (!(ignoredValue instanceof List<?>)) ignoredValue = field(group, "mIgnoredSlots");
        List<?> ignored = ignoredValue instanceof List<?> list ? list : null;
        int stateTag = group.getResources().getIdentifier(
                "status_bar_view_state_tag", "id", "com.android.systemui");
        int count = group.getChildCount();
        int scanned = Math.min(count, MAX_SCANNED_CHILDREN);
        int unreadable = 0;
        SlotChild volume = null;
        SlotChild zen = null;
        SlotChild alarm = null;
        SlotChild speed = null;
        List<SlotChild> other = new ArrayList<>();
        for (int i = 0; i < scanned; i++) {
            View child = group.getChildAt(i);
            String slot = string(call(child, "getSlot"));
            if (slot == null) {
                unreadable++;
                continue;
            }
            if (volume == null && slot.equals(volumeSlot == null ? "volume" : volumeSlot)) {
                volume = new SlotChild(child, slot);
            } else if (zen == null && slot.equals(zenSlot == null ? "zen" : zenSlot)) {
                zen = new SlotChild(child, slot);
            } else if (captureSpacing && alarm == null
                    && ("alarm_clock".equals(slot) || "alarm".equals(slot))) {
                alarm = new SlotChild(child, slot);
            } else if (captureSpacing && speed == null && "network_speed".equals(slot)) {
                speed = new SlotChild(child, slot);
            } else if (other.size() < MAX_REPORTED_SLOTS) {
                other.add(new SlotChild(child, slot));
            }
        }

        ViewParent parent = group.getParent();
        Display display = group.getDisplay();
        StringBuilder result = new StringBuilder()
                .append(" container=").append(identity(group))
                .append(" parent=").append(parent instanceof View view ? identity(view) : "?")
                .append(" display=").append(display == null ? "?" : display.getDisplayId())
                .append(" homeBar=").append(homeBar(group))
                .append(" layoutFrom=").append(scalar(call(group, "getLayoutFrom")))
                .append(" visibility=").append(group.getVisibility())
                .append(" shown=").append(group.isShown())
                .append(" alpha=").append(group.getAlpha())
                .append(" bounds=").append(bounds(group))
                .append(" measuredWidth=").append(group.getMeasuredWidth())
                .append(" limitIcon=").append(scalar(field(group, "limitIcon")))
                .append(" maxSize=").append(scalar(field(group, "maxSize")))
                .append(" islandX=").append(scalar(call(group, "getIslandTranslationX")))
                .append(" children=").append(count)
                .append(" scanned=").append(scanned)
                .append(" unreadableSlots=").append(unreadable)
                .append(" volumeChild=").append(presence(volume, scanned == count, unreadable))
                .append(" zenChild=").append(presence(zen, scanned == count, unreadable));
        if (captureSpacing) {
            result.append(" alarmChild=").append(presence(alarm, scanned == count, unreadable))
                    .append(" speedChild=").append(presence(speed, scanned == count, unreadable))
                    .append(" groupWindow=").append(windowFrame(group))
                    .append(" readoutWindow=").append(windowFrame(networkReadout(group)));
            if (parent instanceof View host) {
                result.append(" wrapperWindow=").append(windowFrame(host))
                        .append(" wrapperMeasured=").append(host.getMeasuredWidth())
                        .append(" wrapperPadding=").append(host.getPaddingLeft())
                        .append(',').append(host.getPaddingRight())
                        .append(" fusionWindow=").append(windowFrame(fusionIcon(host)));
            }
        }
        int reported = 0;
        if (volume != null) {
            appendSlot(result, volume, ignored, stateTag);
            reported++;
        }
        if (zen != null) {
            appendSlot(result, zen, ignored, stateTag);
            reported++;
        }
        if (alarm != null) {
            appendSlot(result, alarm, ignored, stateTag);
            reported++;
        }
        if (speed != null) {
            appendSlot(result, speed, ignored, stateTag);
            reported++;
        }
        for (SlotChild child : other) {
            if (reported == MAX_REPORTED_SLOTS) break;
            appendSlot(result, child, ignored, stateTag);
            reported++;
        }
        return result.append(" omitted=").append(count - reported).toString();
    }

    private static void appendSlot(StringBuilder result, SlotChild slot, List<?> ignored, int stateTag) {
        View child = slot.view;
        Object state = stateTag == 0 ? null : child.getTag(stateTag);
        result.append(" [slot=").append(token(slot.slot))
                .append(" ignored=").append(ignored == null ? "?" : ignored.contains(slot.slot))
                .append(" iconVisible=").append(scalar(call(child, "isIconVisible")))
                .append(" blocked=").append(scalar(call(child, "isIconBlocked")))
                .append(" visibility=").append(child.getVisibility())
                .append(" alpha=").append(child.getAlpha())
                .append(" bounds=").append(bounds(child))
                .append(" tx=").append(child.getTranslationX())
                .append(" visibleState=").append(scalar(call(child, "getVisibleState")))
                .append(" layoutState=").append(scalar(field(state, "visibleState")))
                .append(" hiddenBySpace=").append(scalar(field(state, "hiddenBySpace")))
                .append(" inIslandState=").append(scalar(field(state, "inIslandState")))
                .append(']');
    }

    private static String presence(SlotChild child, boolean complete, int unreadable) {
        return child != null ? "present" : complete && unreadable == 0 ? "absent" : "?";
    }

    private static String homeBar(ViewGroup group) {
        int visited = 0;
        for (ViewParent parent = group.getParent();
                parent instanceof View view && visited < MAX_ANCESTORS;
                parent = view.getParent(), visited++) {
            Object value = field(view, "isHomeBar");
            if (value instanceof Boolean) return scalar(value);
        }
        return "?";
    }

    private static String identity(View view) {
        return token(view.getClass().getSimpleName()) + '@'
                + Integer.toHexString(System.identityHashCode(view)) + "/id:"
                + Integer.toHexString(view.getId());
    }

    private static String bounds(View view) {
        return view.getLeft() + "," + view.getTop() + "," + view.getRight() + "," + view.getBottom();
    }

    private static String windowFrame(View view) {
        if (view == null) return "?";
        int[] location = new int[2];
        view.getLocationInWindow(location);
        return location[0] + "," + location[1] + "," + view.getWidth() + "x" + view.getHeight();
    }

    private static View networkReadout(ViewGroup group) {
        View current = group;
        for (int i = 0; current != null && i < MAX_ANCESTORS; i++) {
            View readout = DualRowStatusBarLayout.networkReadout(current);
            if (readout != null) return readout;
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return null;
    }

    private static View fusionIcon(View wrapper) {
        if (!(field(wrapper, "mBattery") instanceof ViewGroup battery)) return null;
        for (int i = 0; i < battery.getChildCount(); i++) {
            if (battery.getChildAt(i) instanceof FusionIconView icon) return icon;
        }
        return null;
    }

    private static String string(Object value) {
        return value instanceof String text && !text.isEmpty() ? text : null;
    }

    private static String token(String text) {
        if (text == null || text.isEmpty()) return "?";
        String bounded = text.length() <= 48 ? text : text.substring(0, 48) + "...";
        return bounded.replace('\n', '_').replace('\r', '_').replace('\t', '_').replace(' ', '_');
    }

    private static String scalar(Object value) {
        return value instanceof Boolean || value instanceof Number ? value.toString() : "?";
    }

    private static Object field(Object target, String name) {
        if (target == null) return null;
        try {
            Field field = RuntimeReflection.field(target.getClass(), name);
            return field == null ? null : field.get(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Object call(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = RuntimeReflection.exact(target.getClass(), name, NO_ARGS, true);
            return method == null ? null : method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}

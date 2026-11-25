package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Builds the two-column/two-row structure used by the HyperOS 3 status bar. */
final class DualRowStatusBarLayout {
    private static final long SLOW_LAYOUT_MS = 16L;
    private static final long SLOW_LAYOUT_LOG_INTERVAL_MS = 5_000L;
    private static final Map<ViewGroup, WeakReference<State>> STATES = new WeakHashMap<>();
    private static final String NATIVE_ICON_RAIL =
            "com.android.systemui.statusbar.views.MiuiStatusIconContainer";
    // OEM icon MOVE can finish after the privacy controller's final layout.
    private static final long PRIVACY_EXIT_OBSERVE_MS = 1_000L;
    private static final int NO_NATIVE_EDGE = Integer.MIN_VALUE;
    private static final Class<?>[] NO_ARGS = new Class<?>[0];

    private DualRowStatusBarLayout() {
    }

    static int systemChildRow(boolean span, boolean batteryContainer, int selectedRow) {
        return span && batteryContainer ? 0 : selectedRow;
    }

    static int telemetryRow(boolean doubleRow, int position) {
        return doubleRow ? TelemetryConfig.row(position) : 0;
    }

    static int spanningInsetForRow(boolean span, int iconRow, int targetRow, int iconWidth) {
        return span && iconRow != targetRow ? Math.max(0, iconWidth) : 0;
    }

    static int priorityChildWidth(int measuredWidth, int fallbackWidth) {
        return Math.max(Math.max(0, measuredWidth), Math.max(0, fallbackWidth));
    }

    static int priorityReservationWidth(int measuredWidth, int fallbackWidth, int extraWidth) {
        return priorityChildWidth(measuredWidth, fallbackWidth) + Math.max(0, extraWidth);
    }

    static int oppositeRowFusionWidth(int measuredIconWidth, int expectedIconWidth,
            int inwardOffset) {
        return priorityReservationWidth(measuredIconWidth, expectedIconWidth, inwardOffset);
    }

    static int readoutShiftTowardFusion(int visibleIconLeft, int readoutRight,
            int minimumGap, int maximumShift) {
        return Math.min(Math.max(0, maximumShift),
                Math.max(0, visibleIconLeft - readoutRight - Math.max(0, minimumGap)));
    }

    static int telemetryAvailableWidth(int slotWidth, int nativeWidth, int reservedWidth) {
        return Math.max(0, slotWidth - Math.max(0, nativeWidth) - Math.max(0, reservedWidth));
    }

    static int fusionInwardMarginLeft(int side, int inwardOffset) {
        return side == FusionConfig.SIDE_RIGHT ? Math.max(0, inwardOffset) : 0;
    }

    static int fusionInwardMarginRight(int side, int inwardOffset) {
        return side == FusionConfig.SIDE_LEFT ? Math.max(0, inwardOffset) : 0;
    }

    static void apply(ViewGroup root, FusionConfig config) {
        if (root == null || config == null) {
            return;
        }
        State state = null;
        WeakReference<State> reference = STATES.get(root);
        if (reference != null) {
            state = reference.get();
        }
        if (state == null) {
            Object tagged = root.getTag(R.id.dual_row_layout_state);
            if (tagged instanceof State saved) {
                state = saved;
            }
        }
        if (state == null) {
            if (!config.doubleRow && !config.telemetry.anyEnabled()) {
                return;
            }
            state = install(root);
            if (state == null) {
                return;
            }
            root.setTag(R.id.dual_row_layout_state, state);
        }
        STATES.put(root, new WeakReference<>(state));
        state.apply(config);
    }

    static void updateTelemetry(TelemetryReadings readings) {
        Iterator<Map.Entry<ViewGroup, WeakReference<State>>> iterator =
                STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            WeakReference<State> reference = iterator.next().getValue();
            State state = reference == null ? null : reference.get();
            if (state == null) {
                iterator.remove();
            } else {
                state.updateTelemetryText(readings, true);
            }
        }
    }

    static boolean showsTelemetry(ViewGroup root, int type) {
        if (root == null || type < 0 || type >= TelemetryConfig.COUNT) return false;
        WeakReference<State> reference = STATES.get(root);
        State state = reference == null ? null : reference.get();
        if (state == null || !state.telemetryConfig.enabled(type)) return false;
        View view = state.telemetryViews[type];
        while (view != null && view != root) {
            if (view.getVisibility() != View.VISIBLE) return false;
            view = view.getParent() instanceof View parent ? parent : null;
        }
        return view == root;
    }

    static View networkReadout(View root) {
        if (root.getTag(R.id.dual_row_layout_state) instanceof State state
                && showsTelemetry(state.root, TelemetryConfig.NET_SPEED)) {
            return state.telemetryViews[TelemetryConfig.NET_SPEED];
        }
        return null;
    }

    private static State install(ViewGroup root) {
        View leftGroup = find(root, "phone_status_bar_left_container");
        View systemGroup = find(root, "system_icon_area");
        View batteryContainer = find(root, "system_icons");
        View dripNotifications = find(root, "notification_icon_area");
        View fullscreenNotifications = find(root, "fullscreen_notification_icon_area");
        if (leftGroup == null || systemGroup == null
                || !(leftGroup.getParent() instanceof ViewGroup common)
                || systemGroup.getParent() != common
                || !(common instanceof LinearLayout)
                || !(systemGroup instanceof ViewGroup systemContainer)
                || batteryContainer == null
                || batteryContainer.getParent() != systemContainer
                || !"com.android.systemui.statusbar.views.MiuiStatusBatteryContainer"
                .equals(batteryContainer.getClass().getName())) {
            ModuleLog.w("FusionStatusBar", "HyperOS status-bar hierarchy differs; rows not installed");
            return null;
        }
        int statusIconsId = root.getResources().getIdentifier(
                "statusIcons", "id", "com.android.systemui");
        if (statusIconsId == 0 || batteryContainer.findViewById(statusIconsId) == null) {
            ModuleLog.w("FusionStatusBar", "Native statusIcons missing from battery container; rows not installed");
            return null;
        }

        Context context = root.getContext();
        int leftIndex = common.indexOfChild(leftGroup);
        int systemIndex = common.indexOfChild(systemGroup);
        int insertIndex = Math.min(leftIndex, systemIndex);

        removeFromParent(dripNotifications);
        removeFromParent(fullscreenNotifications);
        List<View> systemChildren = new ArrayList<>();
        for (int i = 0; i < systemContainer.getChildCount(); i++) {
            systemChildren.add(systemContainer.getChildAt(i));
        }
        common.removeView(leftGroup);
        common.removeView(systemGroup);
        for (View child : systemChildren) {
            systemContainer.removeView(child);
        }

        LinearLayout leftLayout = new LinearLayout(context);
        leftLayout.setOrientation(LinearLayout.VERTICAL);
        leftLayout.setGravity(Gravity.CENTER_VERTICAL);
        leftLayout.setClipChildren(false);
        leftLayout.setClipToPadding(false);

        LinearLayout rightLayout = new LinearLayout(context);
        rightLayout.setOrientation(LinearLayout.VERTICAL);
        rightLayout.setGravity(Gravity.CENTER_VERTICAL);
        rightLayout.setClipChildren(false);
        rightLayout.setClipToPadding(false);

        leftLayout.setId(leftGroup.getId());
        rightLayout.setId(systemGroup.getId());
        leftGroup.setId(View.NO_ID);
        systemGroup.setId(View.NO_ID);

        common.addView(leftLayout, insertIndex, columnParams());
        common.addView(rightLayout, columnParams());

        Row[] leftRows = rows(context, leftLayout);
        Row[] rightRows = rows(context, rightLayout);
        State state = new State(root, leftLayout, rightLayout, leftRows, rightRows,
                leftGroup, dripNotifications, fullscreenNotifications, systemChildren);
        setViewField(root, "mStatusBarLeftContainer", leftLayout);
        setViewField(root, "mSystemIconArea", rightLayout);
        ModuleLog.i("FusionStatusBar", "rows installed; native system_icons view retained");
        return state;
    }

    private static Row[] rows(Context context, LinearLayout parent) {
        Row[] rows = new Row[] {new Row(context), new Row(context)};
        for (Row row : rows) {
            parent.addView(row.frame, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        return rows;
    }

    private static LinearLayout.LayoutParams columnParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private static void removeFromParent(View view) {
        if (view != null && view.getParent() instanceof ViewGroup parent) {
            parent.removeView(view);
        }
    }

    private static View find(View root, String name) {
        if (root == null) {
            return null;
        }
        try {
            int id = root.getResources().getIdentifier(name, "id", "com.android.systemui");
            return id == 0 ? null : root.findViewById(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setViewField(Object target, String name, View value) {
        Class<?> type = target == null ? null : target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static final class Row {
        final FrameLayout frame;
        final StatusSlotLayout start;
        final StatusSlotLayout end;

        Row(Context context) {
            frame = new FrameLayout(context);
            frame.setClipChildren(false);
            frame.setClipToPadding(false);
            start = slot(context, Gravity.START);
            end = slot(context, Gravity.END);
            frame.addView(start, frameParams(Gravity.START));
            frame.addView(end, frameParams(Gravity.END));
        }

        private static StatusSlotLayout slot(Context context, int gravity) {
            return new StatusSlotLayout(context, gravity);
        }

        private static FrameLayout.LayoutParams frameParams(int gravity) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            params.gravity = gravity | Gravity.CENTER_VERTICAL;
            return params;
        }
    }

    /** Gives native status elements priority and fits optional readouts into the remainder. */
    private static final class StatusSlotLayout extends LinearLayout {
        private final boolean reserveAtStart;
        private View priorityChild;
        private int priorityFallbackWidth;
        private int priorityExtraWidth;
        private View externalPriorityChild;
        private int externalPriorityFallbackWidth;
        private int externalPriorityExtraWidth;
        private int lastWidthMeasureSpec;
        private int lastHeightMeasureSpec;
        private boolean gapLogged;
        private final StatusPairView[] readoutBuffer =
                new StatusPairView[TelemetryConfig.COUNT];
        private final int[] preferredWidths = new int[TelemetryConfig.COUNT];
        private final int[] allocatedWidths = new int[TelemetryConfig.COUNT];
        private final long[] allocationRemainders = new long[TelemetryConfig.COUNT];
        private final int[] iconLocation = new int[2];
        private final int[] slotLocation = new int[2];
        private final Matrix visualMatrix = new Matrix();
        private final RectF mappedBounds = new RectF();
        private final RectF nativeFrameBounds = new RectF();
        private final RectF nativeChildBounds = new RectF();
        private final RectF nativeBounds = new RectF();
        private final ViewTreeObserver.OnPreDrawListener alarmGapPass = this::updateReadoutBeforeDraw;
        private ViewTreeObserver alarmGapObserver;
        private StatusPairView gapReadout;
        private ViewGroup gapRail;
        private View gapAlarm;
        private Method gapAlarmVisibleState;
        private Method gapAlarmAppearAmount;
        private int gapStateId;
        private int gapRailChildren = -1;
        private Field privacyState;
        private boolean privacyWasActive;
        private long observePrivacyExitUntil;
        private int readoutNativeEdge = NO_NATIVE_EDGE;

        StatusSlotLayout(Context context, int gravity) {
            super(context);
            reserveAtStart = gravity == Gravity.START;
            setOrientation(HORIZONTAL);
            setGravity(gravity | Gravity.CENTER_VERTICAL);
            setClipChildren(false);
            setClipToPadding(false);
        }

        void clearFusionReservation() {
            setAlarmGapReadout(null);
            priorityChild = null;
            priorityFallbackWidth = 0;
            priorityExtraWidth = 0;
            externalPriorityChild = null;
            externalPriorityFallbackWidth = 0;
            externalPriorityExtraWidth = 0;
            requestLayout();
        }

        void setAlarmGapReadout(StatusPairView readout) {
            if (gapReadout == readout) return;
            if (gapReadout != null && gapReadout.getParent() == this) gapReadout.setTranslationX(0);
            stopAlarmGapPass();
            gapReadout = readout;
            gapRail = readout == null || priorityChild == null ? null
                    : find(priorityChild, "statusIcons") instanceof ViewGroup rail
                        && rail.getParent() == priorityChild
                        && NATIVE_ICON_RAIL.equals(rail.getClass().getName()) ? rail : null;
            gapAlarm = null;
            gapAlarmVisibleState = null;
            gapAlarmAppearAmount = null;
            gapStateId = gapRail == null ? 0 : getResources().getIdentifier(
                    "status_bar_view_state_tag", "id", "com.android.systemui");
            privacyState = gapRail == null ? null : RuntimeReflection.field(
                    priorityChild.getClass(), "mPrivacyState");
            gapRailChildren = -1;
            resetPrivacyExit();
            startAlarmGapPass();
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            startAlarmGapPass();
        }

        @Override protected void onDetachedFromWindow() {
            stopAlarmGapPass();
            resetPrivacyExit();
            if (gapReadout != null && gapReadout.getParent() == this) gapReadout.setTranslationX(0);
            super.onDetachedFromWindow();
        }

        private void startAlarmGapPass() {
            if (gapRail == null || gapStateId == 0 && privacyState == null
                    || alarmGapObserver != null || !isAttachedToWindow()) return;
            alarmGapObserver = getViewTreeObserver();
            alarmGapObserver.addOnPreDrawListener(alarmGapPass);
        }

        private void stopAlarmGapPass() {
            if (alarmGapObserver != null && alarmGapObserver.isAlive()) {
                alarmGapObserver.removeOnPreDrawListener(alarmGapPass);
            }
            alarmGapObserver = null;
        }

        private boolean updateReadoutBeforeDraw() {
            if (gapReadout != null && gapReadout.getParent() == this) {
                updatePrivacyExitReservation();
                int offset = gapStateId == 0 ? 0 : settledAlarmGap();
                if (gapReadout.getTranslationX() != offset) gapReadout.setTranslationX(offset);
            }
            return true;
        }

        private void resetPrivacyExit() {
            privacyWasActive = false;
            observePrivacyExitUntil = 0;
        }

        private void updatePrivacyExitReservation() {
            if (privacyState == null || gapRail.getParent() != priorityChild
                    || priorityChild.getParent() != this || !gapRail.isShown()
                    || gapReadout.getVisibility() != VISIBLE
                    || getWindowVisibility() != VISIBLE
                    || !(readField(priorityChild, privacyState) instanceof Enum<?> state)) {
                resetPrivacyExit();
                return;
            }
            if (!"COMPLETE_HIDE_PRIVACY".equals(state.name())) {
                privacyWasActive = true;
                observePrivacyExitUntil = 0;
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (privacyWasActive) {
                privacyWasActive = false;
                observePrivacyExitUntil = now + PRIVACY_EXIT_OBSERVE_MS;
            }
            if (observePrivacyExitUntil <= now || readoutNativeEdge == NO_NATIVE_EDGE
                    || isLayoutRequested() || priorityChild.isLayoutRequested() || gapRail.isLayoutRequested()
                    || !isPlainHostFrame((ViewGroup) priorityChild) || !isPlainHostFrame(gapRail)) {
                return;
            }

            boolean nativeAtLeft = reserveAtStart != (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
            collectNativeBounds(nativeAtLeft);
            if (nativeBounds.isEmpty()) return;
            int edge = nativeAtLeft ? (int) Math.ceil(nativeBounds.right)
                    : (int) Math.floor(nativeBounds.left);
            if (edge == readoutNativeEdge) return;
            // A completed traversal already placed the native children. Only repack the readouts.
            layoutReadouts(collectReadouts(), nativeAtLeft, edge);
        }

        private int settledAlarmGap() {
            if (gapReadout.getVisibility() != VISIBLE || gapReadout.getWidth() <= 0
                    || gapRail.getParent() != priorityChild || priorityChild.getParent() != this
                    || !gapRail.isShown() || gapRail.getAlpha() != 1f
                    || priorityChild.getAlpha() != 1f) return 0;
            int count = gapRail.getChildCount();
            if (count != gapRailChildren || gapAlarm != null && gapAlarm.getParent() != gapRail) {
                gapAlarm = null;
                gapAlarmVisibleState = null;
                gapAlarmAppearAmount = null;
                gapRailChildren = count;
                for (int index = 0; index < count; index++) {
                    View icon = gapRail.getChildAt(index);
                    if ("alarm_clock".equals(readMethod(icon, "getSlot"))) {
                        gapAlarm = icon;
                        gapAlarmVisibleState = findMethod(icon, "getVisibleState");
                        gapAlarmAppearAmount = findMethod(icon, "getIconAppearAmount");
                        break;
                    }
                }
            }
            if (gapAlarm == null || gapAlarm.getWidth() <= 0
                    || !Integer.valueOf(2).equals(invoke(gapAlarm, gapAlarmVisibleState))
                    || !Float.valueOf(0f).equals(invoke(gapAlarm, gapAlarmAppearAmount))) {
                return 0;
            }
            if (!Boolean.TRUE.equals(readField(gapAlarm.getTag(gapStateId), "hiddenBySpace"))
                    || !Boolean.TRUE.equals(readMethod(gapAlarm, "isIconVisible"))
                    || !Boolean.FALSE.equals(readMethod(gapAlarm, "isIconBlocked"))
                    || !(readMethod(gapRail, "getIslandTranslationX") instanceof Number island)
                    || island.intValue() > 0
                    || !(readField(priorityChild, privacyState) instanceof Enum<?> privacy)
                    || !"COMPLETE_HIDE_PRIVACY".equals(privacy.name())
                    || !isPlainHostFrame((ViewGroup) priorityChild) || !isPlainHostFrame(gapRail)) {
                return 0;
            }

            boolean nativeAtLeft = reserveAtStart != (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
            int edge = nativeAtLeft ? gapReadout.getLeft() : gapReadout.getRight();
            int clearance = nativeAtLeft ? edge - getPaddingLeft()
                    : getWidth() - getPaddingRight() - edge;
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child != gapReadout && child != priorityChild) {
                    clearance = Math.min(clearance, clearanceTo(child, edge, nativeAtLeft));
                }
            }
            ViewGroup wrapper = (ViewGroup) priorityChild;
            for (int index = 0; index < wrapper.getChildCount(); index++) {
                View child = wrapper.getChildAt(index);
                if (child != gapRail) {
                    clearance = Math.min(clearance, clearanceTo(child, edge, nativeAtLeft));
                }
            }
            for (int index = 0; index < count; index++) {
                View icon = gapRail.getChildAt(index);
                if (icon != gapAlarm) {
                    clearance = Math.min(clearance, clearanceTo(icon, edge, nativeAtLeft));
                }
            }
            mapBounds(gapAlarm);
            int margin = nativeAtLeft ? gapReadout.preferredLeftMargin()
                    : gapReadout.preferredRightMargin();
            int amount = Math.max(0, Math.min((int) mappedBounds.width(), clearance - margin));
            return nativeAtLeft ? -amount : amount;
        }

        private int clearanceTo(View view, int edge, boolean nativeAtLeft) {
            if (view.getVisibility() != VISIBLE || view.getAlpha() == 0f
                    || view.getWidth() <= 0 || view.getHeight() <= 0) return Integer.MAX_VALUE;
            mapBounds(view);
            boolean behindReadout = nativeAtLeft ? mappedBounds.left >= gapReadout.getRight()
                    : mappedBounds.right <= gapReadout.getLeft();
            int distance = behindReadout ? Integer.MAX_VALUE : (int) Math.floor(nativeAtLeft
                    ? edge - mappedBounds.right : mappedBounds.left - edge);
            if (view instanceof ViewGroup group && !group.getClipChildren()) {
                for (int index = 0; index < group.getChildCount(); index++) {
                    distance = Math.min(distance, clearanceTo(group.getChildAt(index), edge, nativeAtLeft));
                }
            }
            return distance;
        }

        private static boolean isPlainHostFrame(ViewGroup group) {
            // The OEM retains removed icons as transient children until their animation ends.
            return group.getBackground() == null && group.getForeground() == null
                    && group.getAnimation() == null && !group.hasTransientState()
                    && Integer.valueOf(0).equals(readMethod(group, "getTransientViewCount"));
        }

        void setLocalFusionReservation(View child, int fallbackWidth, int extraWidth) {
            priorityChild = child;
            priorityFallbackWidth = Math.max(0, fallbackWidth);
            priorityExtraWidth = Math.max(0, extraWidth);
            requestLayout();
        }

        void setExternalFusionReservation(View child, int fallbackWidth, int extraWidth) {
            externalPriorityChild = child;
            externalPriorityFallbackWidth = Math.max(0, fallbackWidth);
            externalPriorityExtraWidth = Math.max(0, extraWidth);
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            lastWidthMeasureSpec = widthMeasureSpec;
            lastHeightMeasureSpec = heightMeasureSpec;
            int externalReservation = externalReservation();
            int startPadding = reserveAtStart ? externalReservation : 0;
            int endPadding = reserveAtStart ? 0 : externalReservation;
            if (getPaddingStart() != startPadding || getPaddingEnd() != endPadding) {
                setPaddingRelative(startPadding, getPaddingTop(), endPadding, getPaddingBottom());
            }
            int readoutCount = collectReadouts();
            for (int index = 0; index < readoutCount; index++) {
                readoutBuffer[index].getLayoutParams().width = 0;
            }

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (readoutCount == 0) {
                return;
            }

            int nativeWidth = getPaddingLeft() + getPaddingRight();
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child.getVisibility() == GONE || child instanceof StatusPairView
                        || !hasVisibleContent(child)) {
                    continue;
                }
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                int childWidth = child.getMeasuredWidth();
                if (child == priorityChild) {
                    childWidth = priorityReservationWidth(
                            childWidth, priorityFallbackWidth, priorityExtraWidth);
                }
                nativeWidth += childWidth + params.leftMargin + params.rightMargin;
            }

            allocateReadouts(readoutCount,
                    telemetryAvailableWidth(getMeasuredWidth(), nativeWidth, 0));
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        private int externalReservation() {
            return externalPriorityChild == null && externalPriorityFallbackWidth == 0 ? 0
                    : oppositeRowFusionWidth(externalPriorityChild == null ? 0
                            : externalPriorityChild.getMeasuredWidth(),
                            externalPriorityFallbackWidth, externalPriorityExtraWidth);
        }

        void updateLiveSpeed(StatusPairView readout, String text, boolean autoWidth) {
            if (!canRelayoutFixedSlot()) {
                readout.setLines(text);
                if (autoWidth) readout.setPreferredWidth(readout.naturalWidth());
                return;
            }
            readout.setLinesForLocalLayout(text);
            boolean widthChanged = autoWidth && readout.updatePreferredWidth(readout.naturalWidth());
            if (!widthChanged && !readout.isLayoutRequested()) return;

            forceLayout();
            measure(lastWidthMeasureSpec, lastHeightMeasureSpec);
            layout(getLeft(), getTop(), getRight(), getBottom());
            invalidate();
        }

        private boolean canRelayoutFixedSlot() {
            int width = getWidth();
            int height = getHeight();
            if (!isAttachedToWindow() || getWindowVisibility() != VISIBLE || !isLaidOut()
                    || isInLayout() || width <= 0 || height <= 0
                    || lastWidthMeasureSpec != MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
                    || lastHeightMeasureSpec != MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                    || externalReservation() != (reserveAtStart ? getPaddingStart() : getPaddingEnd())) {
                return false;
            }
            for (View view = this; view != null;
                    view = view.getParent() instanceof View parent ? parent : null) {
                if (view.isLayoutRequested() || view.getVisibility() != VISIBLE) return false;
            }
            return true;
        }

        private void allocateReadouts(int readoutCount, int availableWidth) {
            for (int index = 0; index < readoutCount; index++) {
                StatusPairView readout = readoutBuffer[index];
                preferredWidths[index] = readout.preferredWidth()
                        + readout.preferredLeftMargin() + readout.preferredRightMargin();
            }
            StatusBarWidthAllocator.allocateInto(
                    availableWidth,
                    preferredWidths, readoutCount, allocatedWidths, allocationRemainders);
            for (int index = 0; index < readoutCount; index++) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                        readoutBuffer[index].getLayoutParams();
                int allocated = allocatedWidths[index];
                StatusPairView readout = readoutBuffer[index];
                int leftMargin = Math.min(readout.preferredLeftMargin(), allocated);
                int rightMargin = Math.min(readout.preferredRightMargin(),
                        Math.max(0, allocated - leftMargin));
                params.leftMargin = leftMargin;
                params.rightMargin = rightMargin;
                params.width = Math.max(0, allocated - leftMargin - rightMargin);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            readoutNativeEdge = NO_NATIVE_EDGE;
            // Child replacement can keep the same count when the previous scan found no alarm.
            if (gapAlarm == null) gapRailChildren = -1;
            int readoutCount = collectReadouts();
            if (readoutCount == 0) return;
            layoutReadoutsBesideNativeContent(readoutCount);
            if (reserveAtStart || externalPriorityChild == null || !isAttachedToWindow()
                    || !externalPriorityChild.isAttachedToWindow()
                    || externalPriorityChild.getWidth() <= 0) {
                return;
            }
            int readoutRight = -1;
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child instanceof StatusPairView) {
                    if (child.getVisibility() == VISIBLE && child.getWidth() > 0) {
                        readoutRight = Math.max(readoutRight, child.getRight());
                    }
                } else if (hasVisibleContent(child)) {
                    return;
                }
            }
            if (readoutRight < 0) {
                return;
            }
            externalPriorityChild.getLocationInWindow(iconLocation);
            getLocationInWindow(slotLocation);
            int iconVisibleLeft = iconLocation[0]
                    + Math.round(externalPriorityChild.getWidth() * 0.08f);
            int offset = readoutShiftTowardFusion(iconVisibleLeft,
                    slotLocation[0] + readoutRight, dp(4), dp(32));
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child instanceof StatusPairView && child.getTranslationX() != offset) {
                    child.setTranslationX(offset);
                }
            }
            if (ModuleLog.enabled() && !gapLogged) {
                gapLogged = true;
                ModuleLog.i("FusionStatusBar", "opposite row gap: iconLeft=" + iconVisibleLeft
                        + " readoutRight=" + (slotLocation[0] + readoutRight)
                        + " offset=" + offset + " inset=" + getPaddingEnd());
            }
        }

        private int collectReadouts() {
            int readoutCount = 0;
            for (int index = 0; index < getChildCount() && readoutCount < readoutBuffer.length; index++) {
                View child = getChildAt(index);
                if (child instanceof StatusPairView readout && child.getVisibility() != GONE) {
                    readoutBuffer[readoutCount++] = readout;
                }
            }
            return readoutCount;
        }

        private void layoutReadoutsBesideNativeContent(int readoutCount) {
            boolean nativeAtLeft = reserveAtStart != (getLayoutDirection() == LAYOUT_DIRECTION_RTL);
            collectNativeBounds(nativeAtLeft);
            if (nativeBounds.isEmpty()) return;

            // Host layout and property transforms can change after measurement.
            int nativeLeft = (int) Math.floor(nativeBounds.left);
            int nativeRight = (int) Math.ceil(nativeBounds.right);
            int shift = nativeAtLeft ? getPaddingLeft() - nativeLeft
                    : getWidth() - getPaddingRight() - nativeRight;
            if (shift != 0) {
                for (int index = 0; index < getChildCount(); index++) {
                    View child = getChildAt(index);
                    if (!(child instanceof StatusPairView) && hasVisibleContent(child)) {
                        child.offsetLeftAndRight(shift);
                    }
                }
            }
            layoutReadouts(readoutCount, nativeAtLeft, (nativeAtLeft ? nativeRight : nativeLeft) + shift);
        }

        private void collectNativeBounds(boolean nativeAtLeft) {
            nativeBounds.setEmpty();
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child instanceof StatusPairView || !hasVisibleContent(child)) continue;
                nativeChildBounds.setEmpty();
                nativeFrameBounds.setEmpty();
                includeNativeBounds(child, true);
                if (child == priorityChild) {
                    int minimumWidth = priorityReservationWidth(child.getMeasuredWidth(),
                            priorityFallbackWidth, priorityExtraWidth);
                    if (nativeChildBounds.isEmpty() && minimumWidth > 0) {
                        nativeChildBounds.set(child.getLeft(), 0f,
                                child.getLeft() + minimumWidth, 1f);
                    } else if (nativeChildBounds.width() < minimumWidth) {
                        if (nativeAtLeft) nativeChildBounds.right = nativeChildBounds.left + minimumWidth;
                        else nativeChildBounds.left = nativeChildBounds.right - minimumWidth;
                    }
                }
                if (nativeChildBounds.isEmpty()) continue;
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                if (!nativeFrameBounds.isEmpty()) {
                    // A physical margin can already cover translated pixels; union it
                    // with the envelope instead of counting the same overhang twice.
                    nativeChildBounds.union(nativeFrameBounds.left - params.leftMargin,
                            nativeFrameBounds.top, nativeFrameBounds.right + params.rightMargin,
                            nativeFrameBounds.bottom);
                } else {
                    nativeChildBounds.left -= params.leftMargin;
                    nativeChildBounds.right += params.rightMargin;
                }
                nativeBounds.union(nativeChildBounds);
            }
        }

        private void layoutReadouts(int readoutCount, boolean nativeAtLeft, int nativeEdge) {
            readoutNativeEdge = nativeEdge;
            int contentLeft = getPaddingLeft();
            int contentRight = getWidth() - getPaddingRight();
            allocateReadouts(readoutCount,
                    Math.max(0, Math.min(contentRight - contentLeft,
                            nativeAtLeft ? contentRight - nativeEdge : nativeEdge - contentLeft)));
            int readoutWidth = 0;
            for (int index = 0; index < readoutCount; index++) {
                readoutWidth += allocatedWidths[index];
            }
            int cursor = nativeAtLeft ? nativeEdge : nativeEdge - readoutWidth;
            cursor = Math.max(contentLeft, Math.min(contentRight, cursor));
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            for (int position = 0; position < readoutCount; position++) {
                StatusPairView readout = readoutBuffer[rtl ? readoutCount - position - 1 : position];
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) readout.getLayoutParams();
                int height = readout.getMeasuredHeight();
                int childTop = readout.getTop();
                readout.measure(MeasureSpec.makeMeasureSpec(params.width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                cursor += params.leftMargin;
                readout.layout(cursor, childTop, cursor + params.width, childTop + height);
                if (readout.getTranslationX() != 0f) readout.setTranslationX(0f);
                cursor += params.width + params.rightMargin;
            }
        }

        private void includeNativeBounds(View view, boolean directChild) {
            if (view.getVisibility() != VISIBLE || view.getAlpha() <= 0.01f) return;
            if (view.getWidth() > 0 && view.getHeight() > 0) {
                mapBounds(view);
                nativeChildBounds.union(mappedBounds);
                if (directChild) nativeFrameBounds.set(mappedBounds);
            }
            // A clipping parent already bounds every descendant by its retained frame.
            if (view instanceof ViewGroup group && !group.getClipChildren()) {
                for (int index = 0; index < group.getChildCount(); index++) {
                    includeNativeBounds(group.getChildAt(index), false);
                }
            }
        }

        private void mapBounds(View view) {
            visualMatrix.reset();
            view.transformMatrixToGlobal(visualMatrix);
            transformMatrixToLocal(visualMatrix);
            mappedBounds.set(0f, 0f, view.getWidth(), view.getHeight());
            visualMatrix.mapRect(mappedBounds);
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }

    private static Object readMethod(Object target, String name) {
        return invoke(target, findMethod(target, name));
    }

    private static Method findMethod(Object target, String name) {
        try {
            return RuntimeReflection.exact(target.getClass(), name, NO_ARGS, false);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, Method method) {
        try {
            return method == null ? null : method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        try {
            return readField(target, RuntimeReflection.field(target.getClass(), name));
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static Object readField(Object target, Field field) {
        try {
            return field == null ? null : field.get(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean hasVisibleContent(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0.01f) {
            return false;
        }
        if (!(view instanceof ViewGroup group) || group.getChildCount() == 0) {
            return true;
        }
        for (int index = 0; index < group.getChildCount(); index++) {
            if (hasVisibleContent(group.getChildAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static final class State {
        final ViewGroup root;
        final LinearLayout leftLayout;
        final LinearLayout rightLayout;
        final Row[] leftRows;
        final Row[] rightRows;
        final View leftGroup;
        final View dripNotifications;
        final View fullscreenNotifications;
        final List<View> systemChildren;
        final StatusPairView[] telemetryViews = new StatusPairView[TelemetryConfig.COUNT];
        TelemetryConfig telemetryConfig = TelemetryConfig.defaults();
        int telemetryHeight = -1;
        boolean telemetryDoubleRow;
        final int clockId;
        boolean applied;
        StatusBarLayoutPlan appliedPlan;
        FusionConfig appliedConfig;
        int lastMeasuredWidth = -1;
        int lastMeasuredHeight = -1;
        long lastSlowLayoutLogAt;

        State(ViewGroup root, LinearLayout leftLayout, LinearLayout rightLayout,
                Row[] leftRows, Row[] rightRows, View leftGroup,
                View dripNotifications, View fullscreenNotifications,
                List<View> systemChildren) {
            this.root = root;
            this.leftLayout = leftLayout;
            this.rightLayout = rightLayout;
            this.leftRows = leftRows;
            this.rightRows = rightRows;
            this.leftGroup = leftGroup;
            this.dripNotifications = dripNotifications;
            this.fullscreenNotifications = fullscreenNotifications;
            this.systemChildren = systemChildren;
            this.clockId = root.getResources().getIdentifier("clock", "id", "com.android.systemui");
        }

        void apply(FusionConfig config) {
            long startedAt = SystemClock.uptimeMillis();
            ensureColumnParams();
            boolean configChanged = appliedConfig != config;
            StatusBarLayoutPlan plan = appliedPlan;
            if (plan == null || configChanged) {
                plan = StatusBarLayoutPlan.from(config);
            }
            // Initial installation temporarily detaches the native children before placing them.
            if (applied) {
                systemChildren.removeIf(view -> {
                    if (!NotificationHookInstaller.NETWORK_SPEED_CLASS.equals(
                            view.getClass().getName())) return false;
                    View ancestor = view;
                    while (ancestor != null && ancestor != root) {
                        ancestor = ancestor.getParent() instanceof View parent ? parent : null;
                    }
                    return ancestor != root;
                });
            }
            boolean dimensionsChanged = lastMeasuredWidth != root.getMeasuredWidth()
                    || lastMeasuredHeight != root.getMeasuredHeight();
            boolean placementChanged = !applied
                    || appliedPlan == null || !appliedPlan.equals(plan)
                    || !isPlaced(plan);
            boolean layoutChanged = configChanged || dimensionsChanged || placementChanged;
            setVisibilityIfChanged(leftRows[0].frame, View.VISIBLE);
            setVisibilityIfChanged(rightRows[0].frame, View.VISIBLE);
            setVisibilityIfChanged(leftRows[1].frame,
                    config.doubleRow ? View.VISIBLE : View.GONE);
            setVisibilityIfChanged(rightRows[1].frame,
                    config.doubleRow ? View.VISIBLE : View.GONE);
            if (placementChanged) {
                removeTelemetryViews();
                clearManagedViews();
                place(leftGroup, plan.clockSide, plan.clockRow);
                place(dripNotifications, plan.notificationSide, plan.notificationRow);
                place(fullscreenNotifications, plan.notificationSide, plan.notificationRow);
                placeSystemChildren(plan);
                applied = true;
            }
            if (layoutChanged) {
                applyNativeIconScales(config, plan);
                updateFusionReservations(config, plan);
                syncTelemetry(config, plan);
            }
            if (configChanged) {
                TelemetrySampler.configure(root.getContext(), config.telemetry);
            }
            appliedPlan = plan;
            appliedConfig = config;
            lastMeasuredWidth = root.getMeasuredWidth();
            lastMeasuredHeight = root.getMeasuredHeight();
            long elapsed = SystemClock.uptimeMillis() - startedAt;
            if (elapsed >= SLOW_LAYOUT_MS
                    && (lastSlowLayoutLogAt == 0
                    || startedAt - lastSlowLayoutLogAt >= SLOW_LAYOUT_LOG_INTERVAL_MS)) {
                lastSlowLayoutLogAt = startedAt;
                ModuleLog.w("FusionStatusBar", "slow status layout: " + elapsed
                        + "ms root=" + root.getClass().getName()
                        + " doubleRow=" + config.doubleRow
                        + " span=" + plan.spanFusion);
            }
        }

        private void removeTelemetryViews() {
            for (StatusPairView view : telemetryViews) {
                removeFromParent(view);
            }
        }

        private void syncTelemetry(FusionConfig config, StatusBarLayoutPlan plan) {
            TelemetryConfig settings = config.telemetry;
            boolean styleChanged = telemetryConfig != settings
                    || telemetryHeight != config.statusBarHeight
                    || telemetryDoubleRow != plan.doubleRow;
            telemetryConfig = settings;
            telemetryHeight = config.statusBarHeight;
            telemetryDoubleRow = plan.doubleRow;
            for (int metric = 0; metric < TelemetryConfig.COUNT; metric++) {
                StatusPairView view = telemetryViews[metric];
                if (!settings.enabled(metric)) {
                    removeFromParent(view);
                    continue;
                }
                boolean created = view == null;
                if (created) {
                    view = new StatusPairView(root.getContext());
                    telemetryViews[metric] = view;
                }
                int position = settings.position(metric);
                int side = TelemetryConfig.side(position);
                int row = plan.telemetryRow(position);
                StatusSlotLayout target = side == TelemetryConfig.SIDE_LEFT
                        ? leftRows[row].start : rightRows[row].end;
                int width = settings.fixedWidth(metric) > 0
                        ? dp(settings.fixedWidth(metric))
                        : Math.max(1, view.naturalWidth());
                view.setPreferredWidth(width);
                LinearLayout.LayoutParams params =
                        view.getLayoutParams() instanceof LinearLayout.LayoutParams existing
                                ? existing : null;
                int leftMargin = dp(settings.leftMargin(metric));
                int rightMargin = dp(settings.rightMargin(metric));
                boolean move = view.getParent() != target;
                // The slot owns the allocated width and may clip the configured margins.
                if (move || params == null || params.height != ViewGroup.LayoutParams.MATCH_PARENT
                        || view.preferredLeftMargin() != leftMargin
                        || view.preferredRightMargin() != rightMargin) {
                    if (params == null) {
                        params = new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.MATCH_PARENT);
                    }
                    params.width = 0;
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    params.leftMargin = leftMargin;
                    params.rightMargin = rightMargin;
                    if (move) {
                        removeFromParent(view);
                        if (side == TelemetryConfig.SIDE_RIGHT) {
                            target.addView(view, 0, params);
                        } else {
                            target.addView(view, params);
                        }
                    } else {
                        view.setLayoutParams(params);
                    }
                }
                if (metric == TelemetryConfig.NET_SPEED) target.setAlarmGapReadout(view);
                view.setTranslationY(dp(settings.verticalOffset(metric)));
                boolean calibratedRow = plan.spanFusion
                        && plan.fusionSide == FusionConfig.SIDE_RIGHT
                        && side == TelemetryConfig.SIDE_RIGHT && row == 1;
                if (!calibratedRow && view.getTranslationX() != 0f) {
                    view.setTranslationX(0f);
                }
                if (created || styleChanged) {
                    view.setSize(settings.textSizeFor(
                            metric, config.statusBarHeight, config.doubleRow));
                    view.setAlignment(TelemetryConfig.resolvedAlignment(
                            settings.alignment(metric), side));
                    view.setBold(settings.bold(metric));
                    view.setLineSpacing(settings.lineSpacing(metric));
                }
            }
            updateTelemetryText(TelemetrySampler.latest(), false);
        }

        private void updateFusionReservations(FusionConfig config, StatusBarLayoutPlan plan) {
            for (Row row : leftRows) {
                row.start.clearFusionReservation();
                row.end.clearFusionReservation();
            }
            for (Row row : rightRows) {
                row.start.clearFusionReservation();
                row.end.clearFusionReservation();
            }
            View batteryContainer = null;
            for (View child : systemChildren) {
                if (isBatteryContainer(child)) {
                    batteryContainer = child;
                    break;
                }
            }
            if (batteryContainer == null) {
                return;
            }
            boolean spans = plan.spanFusion;
            int iconRow = plan.systemChildRow(true);
            int iconWidth = expectedFusionWidth(config)
                    + batteryContainer.getPaddingLeft() + batteryContainer.getPaddingRight();
            int offsetPx = dp(config.spanOffsetX);
            int inwardOffset = plan.fusionSide == FusionConfig.SIDE_LEFT
                    ? Math.max(0, offsetPx) : Math.max(0, -offsetPx);
            applyFusionInwardMargin(batteryContainer, plan.fusionSide,
                    spans ? inwardOffset : 0);
            Row[] rows = plan.fusionSide == FusionConfig.SIDE_LEFT ? leftRows : rightRows;
            StatusSlotLayout primary = plan.fusionSide == FusionConfig.SIDE_LEFT
                    ? rows[iconRow].start : rows[iconRow].end;
            // The local row gets a real margin so translated pixels cannot overlap telemetry.
            // Do not also count the offset as priorityExtraWidth: onMeasure includes margins.
            primary.setLocalFusionReservation(batteryContainer, iconWidth, 0);
            if (spans) {
                int otherRow = iconRow == 0 ? 1 : 0;
                StatusSlotLayout opposite = plan.fusionSide == FusionConfig.SIDE_LEFT
                        ? rows[otherRow].start : rows[otherRow].end;
                opposite.setExternalFusionReservation(
                        findFusionIcon(batteryContainer), expectedFusionWidth(config),
                        inwardOffset);
            }
        }

        private static View findFusionIcon(View node) {
            if (node instanceof FusionIconView) {
                return node;
            }
            if (node instanceof ViewGroup group) {
                for (int index = 0; index < group.getChildCount(); index++) {
                    View icon = findFusionIcon(group.getChildAt(index));
                    if (icon != null) {
                        return icon;
                    }
                }
            }
            return null;
        }

        private static void applyFusionInwardMargin(View batteryContainer, int side, int inwardOffset) {
            if (!(batteryContainer.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params)) {
                return;
            }
            int leftMargin = fusionInwardMarginLeft(side, inwardOffset);
            int rightMargin = fusionInwardMarginRight(side, inwardOffset);
            if (params.leftMargin == leftMargin && params.rightMargin == rightMargin) {
                return;
            }
            params.leftMargin = leftMargin;
            params.rightMargin = rightMargin;
            batteryContainer.setLayoutParams(params);
        }

        private int expectedFusionWidth(FusionConfig config) {
            int availableHeight;
            if (config.doubleRow && config.spanRows) {
                availableHeight = dp(config.statusBarHeight);
            } else if (config.doubleRow) {
                availableHeight = Math.max(1, dp(config.statusBarHeight) / 2);
            } else {
                availableHeight = Math.min(dp(40), dp(config.statusBarHeight));
            }
            int iconHeight = FusionIconPlacement.iconHeight(availableHeight, config.iconScale);
            return Math.max(1, Math.round(iconHeight
                    * FusionIconView.DESIGN_W / FusionIconView.DESIGN_H));
        }

        private int dp(float value) {
            return Math.round(value * root.getResources().getDisplayMetrics().density);
        }

        private void applyNativeIconScales(FusionConfig config, StatusBarLayoutPlan plan) {
            for (View child : systemChildren) {
                if (isBatteryContainer(child)) {
                    View statusIcons = findClassView(child,
                            NATIVE_ICON_RAIL);
                    if (statusIcons instanceof ViewGroup statusGroup) {
                        applyVisualScale(statusIcons, config.systemIconScale, plan.systemSide);
                        statusGroup.setClipChildren(false);
                        statusGroup.setClipToPadding(false);
                    }
                } else {
                    applyVisualScale(child, config.systemIconScale, plan.systemSide);
                }
            }
            applyVisualScale(dripNotifications, config.notificationIconScale,
                    plan.notificationSide);
            applyVisualScale(fullscreenNotifications, config.notificationIconScale,
                    plan.notificationSide);
        }

        private static View findClassView(View node, String className) {
            if (node == null || className == null) {
                return null;
            }
            if (className.equals(node.getClass().getName())) {
                return node;
            }
            if (node instanceof ViewGroup group) {
                for (int index = 0; index < group.getChildCount(); index++) {
                    View found = findClassView(group.getChildAt(index), className);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        private static void applyVisualScale(View view, int scale, int side) {
            if (view == null) {
                return;
            }
            float factor = Math.max(0.5f, Math.min(1.5f, scale / 100f));
            view.setScaleX(factor);
            view.setScaleY(factor);
            view.setPivotX(side == FusionConfig.SIDE_LEFT ? 0f : view.getMeasuredWidth());
            view.setPivotY(view.getMeasuredHeight() / 2f);
        }

        void updateTelemetryText(TelemetryReadings readings, boolean liveUpdate) {
            boolean visible = TelemetrySampler.isInteractive();
            View clock = clockId == 0 ? null : root.findViewById(clockId);
            int tint = clock instanceof TextView textView
                    ? textView.getCurrentTextColor() : Color.WHITE;
            for (int metric = 0; metric < TelemetryConfig.COUNT; metric++) {
                StatusPairView view = telemetryViews[metric];
                if (view == null || view.getParent() == null || !telemetryConfig.enabled(metric)) {
                    continue;
                }
                if (view.getVisibility() != (visible ? View.VISIBLE : View.GONE)) {
                    view.setVisibility(visible ? View.VISIBLE : View.GONE);
                }
                if (visible) {
                    view.setTint(tint);
                    String text = readings.textFor(metric, telemetryConfig, java.util.Locale.getDefault());
                    boolean autoWidth = telemetryConfig.fixedWidth(metric) == 0;
                    if (liveUpdate && metric == TelemetryConfig.NET_SPEED
                            && view.getParent() instanceof StatusSlotLayout slot) {
                        slot.updateLiveSpeed(view, text, autoWidth);
                    } else {
                        view.setLines(text);
                        if (autoWidth) view.setPreferredWidth(view.naturalWidth());
                    }
                }
            }
        }

        private void ensureColumnParams() {
            setColumnParams(leftLayout);
            setColumnParams(rightLayout);
        }

        private static void setColumnParams(LinearLayout layout) {
            if (!(layout.getLayoutParams() instanceof LinearLayout.LayoutParams params)
                    || params.width != 0
                    || params.height != ViewGroup.LayoutParams.MATCH_PARENT
                    || params.weight != 1f) {
                layout.setLayoutParams(columnParams());
            }
        }

        private static void setVisibilityIfChanged(View view, int visibility) {
            if (view.getVisibility() != visibility) {
                view.setVisibility(visibility);
            }
        }

        private boolean isPlaced(StatusBarLayoutPlan plan) {
            return parentOf(leftGroup, plan.clockSide, plan.clockRow)
                    && parentOf(dripNotifications, plan.notificationSide, plan.notificationRow)
                    && parentOf(fullscreenNotifications, plan.notificationSide,
                    plan.notificationRow)
                    && systemChildren.stream().allMatch(view -> {
                        boolean battery = isBatteryContainer(view);
                        int side = battery ? plan.fusionSide : plan.systemSide;
                        return parentOf(view, side, plan.systemChildRow(battery));
                    });
        }

        private boolean parentOf(View view, int side, int row) {
            if (view == null) {
                return true;
            }
            Row[] targetRows = side == FusionConfig.SIDE_LEFT ? leftRows : rightRows;
            StatusSlotLayout slot = side == FusionConfig.SIDE_LEFT
                    ? targetRows[Math.min(row, 1)].start
                    : targetRows[Math.min(row, 1)].end;
            return view.getParent() == slot;
        }

        private void clearManagedViews() {
            removeFromParent(leftGroup);
            for (View view : systemChildren) {
                removeFromParent(view);
            }
            removeFromParent(dripNotifications);
            removeFromParent(fullscreenNotifications);
        }

        private void place(View view, int side, int row) {
            if (view == null) {
                return;
            }
            Row[] targetRows = side == FusionConfig.SIDE_LEFT ? leftRows : rightRows;
            StatusSlotLayout slot = side == FusionConfig.SIDE_LEFT
                    ? targetRows[Math.min(row, 1)].start
                    : targetRows[Math.min(row, 1)].end;
            slot.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        private void placeSystemChildren(StatusBarLayoutPlan plan) {
            for (View view : systemChildren) {
                boolean fullWidth = isBatteryContainer(view);
                int targetSide = fullWidth ? plan.fusionSide : plan.systemSide;
                int targetRow = plan.systemChildRow(fullWidth);
                Row[] targetRows = targetSide == FusionConfig.SIDE_LEFT ? leftRows : rightRows;
                StatusSlotLayout slot = targetSide == FusionConfig.SIDE_LEFT
                        ? targetRows[Math.min(targetRow, 1)].start
                        : targetRows[Math.min(targetRow, 1)].end;
                slot.addView(view, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }

        private static boolean isBatteryContainer(View view) {
            return "com.android.systemui.statusbar.views.MiuiStatusBatteryContainer"
                    .equals(view.getClass().getName());
        }

        private boolean isNotification(View view) {
            return view == dripNotifications || view == fullscreenNotifications;
        }
    }
}

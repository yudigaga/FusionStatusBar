package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
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
                state.updateTelemetryText(readings);
            }
        }
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
            Log.w("FusionStatusBar", "HyperOS status-bar hierarchy differs; rows not installed");
            return null;
        }
        int statusIconsId = root.getResources().getIdentifier(
                "statusIcons", "id", "com.android.systemui");
        if (statusIconsId == 0 || batteryContainer.findViewById(statusIconsId) == null) {
            Log.w("FusionStatusBar", "Native statusIcons missing from battery container; rows not installed");
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
        Log.i("FusionStatusBar", "rows installed; native system_icons view retained");
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
        private boolean gapLogged;
        private final StatusPairView[] readoutBuffer =
                new StatusPairView[TelemetryConfig.COUNT];
        private final int[] preferredWidths = new int[TelemetryConfig.COUNT];
        private final int[] allocatedWidths = new int[TelemetryConfig.COUNT];
        private final long[] allocationRemainders = new long[TelemetryConfig.COUNT];
        private final int[] iconLocation = new int[2];
        private final int[] slotLocation = new int[2];

        StatusSlotLayout(Context context, int gravity) {
            super(context);
            reserveAtStart = gravity == Gravity.START;
            setOrientation(HORIZONTAL);
            setGravity(gravity | Gravity.CENTER_VERTICAL);
            setClipChildren(false);
            setClipToPadding(false);
        }

        void clearFusionReservation() {
            priorityChild = null;
            priorityFallbackWidth = 0;
            priorityExtraWidth = 0;
            externalPriorityChild = null;
            externalPriorityFallbackWidth = 0;
            externalPriorityExtraWidth = 0;
            requestLayout();
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
            int externalReservation = externalPriorityChild == null
                    && externalPriorityFallbackWidth == 0 ? 0
                    : oppositeRowFusionWidth(externalPriorityChild == null ? 0
                            : externalPriorityChild.getMeasuredWidth(),
                            externalPriorityFallbackWidth, externalPriorityExtraWidth);
            int startPadding = reserveAtStart ? externalReservation : 0;
            int endPadding = reserveAtStart ? 0 : externalReservation;
            if (getPaddingStart() != startPadding || getPaddingEnd() != endPadding) {
                setPaddingRelative(startPadding, getPaddingTop(), endPadding, getPaddingBottom());
            }
            int readoutCount = 0;
            for (int index = 0; index < getChildCount(); index++) {
                View child = getChildAt(index);
                if (child instanceof StatusPairView readout && child.getVisibility() != GONE
                        && readoutCount < readoutBuffer.length) {
                    readoutBuffer[readoutCount++] = readout;
                    ((LinearLayout.LayoutParams) child.getLayoutParams()).width = 0;
                }
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

            for (int index = 0; index < readoutCount; index++) {
                StatusPairView readout = readoutBuffer[index];
                LinearLayout.LayoutParams params =
                        (LinearLayout.LayoutParams) readout.getLayoutParams();
                preferredWidths[index] = readout.preferredWidth()
                        + params.leftMargin + params.rightMargin;
            }
            StatusBarWidthAllocator.allocateInto(
                    telemetryAvailableWidth(getMeasuredWidth(), nativeWidth, 0),
                    preferredWidths, readoutCount, allocatedWidths, allocationRemainders);
            for (int index = 0; index < readoutCount; index++) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                        readoutBuffer[index].getLayoutParams();
                int allocated = allocatedWidths[index];
                int leftMargin = Math.min(params.leftMargin, allocated);
                int rightMargin = Math.min(params.rightMargin,
                        Math.max(0, allocated - leftMargin));
                params.leftMargin = leftMargin;
                params.rightMargin = rightMargin;
                params.width = Math.max(0, allocated - leftMargin - rightMargin);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
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
            if (!gapLogged) {
                gapLogged = true;
                Log.i("FusionStatusBar", "opposite row gap: iconLeft=" + iconVisibleLeft
                        + " readoutRight=" + (slotLocation[0] + readoutRight)
                        + " offset=" + offset + " inset=" + getPaddingEnd());
            }
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
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
                Log.w("FusionStatusBar", "slow status layout: " + elapsed
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
                LinearLayout.LayoutParams params;
                if (view.getLayoutParams() instanceof LinearLayout.LayoutParams existing) {
                    params = existing;
                } else {
                    params = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.MATCH_PARENT);
                }
                params.width = 0;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.leftMargin = dp(settings.leftMargin(metric));
                params.rightMargin = dp(settings.rightMargin(metric));
                if (view.getParent() != target) {
                    removeFromParent(view);
                    if (side == TelemetryConfig.SIDE_RIGHT) {
                        target.addView(view, 0, params);
                    } else {
                        target.addView(view, params);
                    }
                }
                view.setLayoutParams(params);
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
            updateTelemetryText(TelemetrySampler.latest());
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
                            "com.android.systemui.statusbar.views.MiuiStatusIconContainer");
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

        void updateTelemetryText(TelemetryReadings readings) {
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
                    view.setLines(readings.textFor(metric, telemetryConfig,
                            java.util.Locale.getDefault()));
                    if (telemetryConfig.fixedWidth(metric) == 0) {
                        view.setPreferredWidth(view.naturalWidth());
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

package com.xtjm.fusionstatusbar;

import android.content.res.Resources;
import android.content.res.Configuration;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.AbsoluteSizeSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Locale;
import java.util.WeakHashMap;

/** Applies notification-center settings to the OEM big-time view without moving it. */
final class NotificationCenterClockController {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String TAG = "FusionStatusBar";
    private static final ClockState TIME = new ClockState();
    private static final ClockState DATE = new ClockState();
    private static final ClockState HORIZONTAL_TIME = new ClockState();
    private static final WeakHashMap<ViewGroup, Boolean> HEADER_LISTENERS = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, WeakReference<LinearLayout>> CLOCK_STACKS = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> SETTINGS_VISIBILITIES = new WeakHashMap<>();
    private static float lastExpansionProgress = Float.NaN;
    // The ticker retains only weak view references and an application context.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static final VisibleSecondTicker SECOND_TICK = new VisibleSecondTicker(() -> {
        renderIfAttached(TIME);
        renderIfAttached(HORIZONTAL_TIME);
    });

    private static FusionConfig config = FusionConfig.defaults();
    private static boolean headerLogged;
    private static boolean timeAppliedLogged;
    private static boolean dateAppliedLogged;

    private static final class ClockState {
        WeakReference<TextView> view = new WeakReference<>(null);
        String nativeText = "";
        String renderedText;
        float nativeSizePx;
        float nativeTranslationX;
        float nativeTranslationY;
        WeakReference<ViewGroup> nativeParent = new WeakReference<>(null);
        ViewGroup.LayoutParams nativeLayoutParams;
        int nativeIndex = -1;
        int nativeVisibility = View.VISIBLE;
        int nativeMaxLines = Integer.MAX_VALUE;
        boolean nativeSingleLine;
        boolean nativeHorizontallyScrolling;
        float lastExpansionTranslation = Float.NaN;
        float lastExpansionOffsetPx;
        int renderedDateSizeSp = -1;
        boolean renderedTimeCentered;
        boolean renderedDateCentered;
        boolean centerApplied;
        boolean textModeApplied;
        boolean hiddenForCombined;

        void capture(TextView clock, boolean enabled) {
            String observed = clock.getText() == null ? "" : clock.getText().toString();
            if (view.get() != clock) {
                view = new WeakReference<>(clock);
                nativeText = observed;
                renderedText = null;
                nativeSizePx = clock.getTextSize();
                nativeTranslationX = clock.getTranslationX();
                nativeTranslationY = clock.getTranslationY();
                if (clock.getParent() instanceof ViewGroup parent) {
                    nativeParent = new WeakReference<>(parent);
                    nativeLayoutParams = clock.getLayoutParams();
                    nativeIndex = parent.indexOfChild(clock);
                }
                nativeVisibility = clock.getVisibility();
                nativeMaxLines = clock.getMaxLines();
                nativeSingleLine = clock.isSingleLine();
                nativeHorizontallyScrolling = clock.isHorizontallyScrollable();
                lastExpansionTranslation = Float.NaN;
                lastExpansionOffsetPx = 0f;
                renderedDateSizeSp = -1;
                renderedTimeCentered = false;
                renderedDateCentered = false;
                centerApplied = false;
                textModeApplied = false;
                hiddenForCombined = false;
            } else if (!observed.equals(renderedText)) {
                nativeText = observed;
            }
            if (!enabled) {
                nativeSizePx = clock.getTextSize();
                if (!centerApplied) {
                    nativeTranslationX = clock.getTranslationX();
                }
                nativeTranslationY = clock.getTranslationY();
                if (!textModeApplied) {
                    nativeMaxLines = clock.getMaxLines();
                    nativeSingleLine = clock.isSingleLine();
                    nativeHorizontallyScrolling = clock.isHorizontallyScrollable();
                }
                if (!hiddenForCombined) {
                    nativeVisibility = clock.getVisibility();
                }
            }
        }
    }

    private NotificationCenterClockController() {
    }

    static boolean hasAttachedViews() {
        for (ClockState state : new ClockState[] {TIME, DATE, HORIZONTAL_TIME}) {
            TextView view = state.view.get();
            if (view != null && view.isAttachedToWindow()) return true;
        }
        return false;
    }

    static boolean isNotificationView(TextView view) {
        if (view == null) return false;
        if (TIME.view.get() == view || DATE.view.get() == view
                || HORIZONTAL_TIME.view.get() == view) return true;
        ClockState state = stateFor(view);
        if (state == null) return false;
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if ("com.android.systemui.qs.MiuiNotificationHeaderView"
                    .equals(parent.getClass().getName())) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private static ClockState stateFor(TextView view) {
        if (view == null) return null;
        if (TIME.view.get() == view) return TIME;
        if (DATE.view.get() == view) return DATE;
        if (HORIZONTAL_TIME.view.get() == view) return HORIZONTAL_TIME;
        try {
            Resources resources = view.getResources();
            int id = view.getId();
            if (id == 0 || id == View.NO_ID) return null;
            if (id == resources.getIdentifier("big_time", "id", "com.android.systemui")) {
                return TIME;
            }
            if (id == resources.getIdentifier("date_time", "id", "com.android.systemui")) {
                return DATE;
            }
            if (id == resources.getIdentifier(
                    "horizontal_time", "id", "com.android.systemui")) {
                return HORIZONTAL_TIME;
            }
        } catch (Throwable ignored) {
            // Some OEM variants omit one of the notification header views.
        }
        return null;
    }

    static void onHeader(ViewGroup header, FusionConfig next) {
        if (header == null) {
            return;
        }
        config = next == null ? FusionConfig.defaults() : next;
        TextView time = findHeaderClock(header, "mBigTime", "big_time");
        TextView date = findHeaderClock(header, "mDateView", "date_time");
        TextView horizontalTime = findHeaderClock(
                header, "mLandClock", "horizontal_time");
        if (TIME.view.get() != time) {
            lastExpansionProgress = Float.NaN;
        }
        bind(TIME, time);
        bind(DATE, date);
        bind(HORIZONTAL_TIME, horizontalTime);
        installHeaderLayout(header);
        applySettingsShortcut(header, config.notificationClock.hideSettings);
        applyIndependentRows(header, time, date);
        if (!headerLogged && (time != null || date != null || horizontalTime != null)) {
            headerLogged = true;
            android.util.Log.i(TAG, "notification header bound time=" + (time != null)
                    + " date=" + (date != null)
                    + " horizontalTime=" + (horizontalTime != null));
        }
    }

    static void onNativeUpdate(TextView clock, FusionConfig next) {
        ClockState state = stateFor(clock);
        if (state == null) return;
        config = next == null ? FusionConfig.defaults() : next;
        bind(state, clock);
        ViewGroup header = currentHeader();
        if (header != null) {
            applySettingsShortcut(header, config.notificationClock.hideSettings);
            applyIndependentRows(header, TIME.view.get(), DATE.view.get());
        }
    }

    private static void bind(ClockState state, TextView clock) {
        if (clock == null) return;
        state.capture(clock, enabled(state));
        render(state);
        scheduleSeconds();
    }

    static void onConfigChanged(FusionConfig next) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> onConfigChanged(next));
            return;
        }
        config = next == null ? FusionConfig.defaults() : next;
        render(TIME);
        render(DATE);
        render(HORIZONTAL_TIME);
        ViewGroup header = currentHeader();
        if (header != null) {
            applyIndependentRows(header, TIME.view.get(), DATE.view.get());
        }
        scheduleSeconds();
    }

    static void onWeatherChanged() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(NotificationCenterClockController::onWeatherChanged);
            return;
        }
        renderIfAttached(TIME);
        renderIfAttached(HORIZONTAL_TIME);
    }

    static void enforceExpandedTimeSize(int pixels) {
        TextView clock = TIME.view.get();
        if (clock != null && clock.isAttachedToWindow()
                && Math.abs(clock.getTextSize() - pixels) > 0.25f) {
            clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels);
        }
    }

    static void onExpansionChanged(float progress) {
        lastExpansionProgress = progress;
        ViewGroup header = currentHeader();
        if (header != null) {
            applyIndependentRows(header, TIME.view.get(), DATE.view.get());
        }
        if (progress <= 0f || progress > 1f) return;
        NotificationClockConfig settings = config.notificationClock;
        if (settings.enabled && settings.offsetYDp != 0) {
            applyExpandedOffset(TIME, settings.offsetYDp, progress);
        }
    }

    static float minimumHeaderHeight(Context context, float nativeHeight) {
        if (context == null || config == null
                || context.getResources().getConfiguration().orientation
                        != Configuration.ORIENTATION_PORTRAIT) {
            return nativeHeight;
        }
        NotificationClockConfig settings = config.notificationClock;
        if (!settings.enabled || settings.sizeSp == 0) {
            return nativeHeight;
        }
        float density = context.getResources().getDisplayMetrics().density;
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        int nativeTimePx = dimension(context, "shade_header_notification_clock_text_size",
                Math.round(48f * density));
        int targetTimePx = Math.round(settings.sizeSp * scaledDensity);
        int lineCount = 1;
        String pattern = settings.combinedPattern();
        for (int index = 0; index < pattern.length(); index++) {
            if (pattern.charAt(index) == '\n') lineCount++;
        }
        float extra = Math.max(0, (targetTimePx * lineCount) - nativeTimePx);
        extra += Math.max(0, settings.offsetYDp) * density;
        return Math.max(nativeHeight, nativeHeight + extra);
    }

    private static int dimension(Context context, String name, int fallback) {
        try {
            int id = context.getResources().getIdentifier(name, "dimen", "com.android.systemui");
            return id == 0 ? fallback : context.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void installHeaderLayout(ViewGroup header) {
        synchronized (HEADER_LISTENERS) {
            if (HEADER_LISTENERS.containsKey(header)) return;
            HEADER_LISTENERS.put(header, Boolean.TRUE);
        }
        header.setClipChildren(false);
        header.setClipToPadding(false);
        header.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (view instanceof ViewGroup group) {
                applyIndependentRows(group, TIME.view.get(), DATE.view.get());
            }
        });
        header.post(() -> applyIndependentRows(header, TIME.view.get(), DATE.view.get()));
    }

    private static ViewGroup currentHeader() {
        TextView time = TIME.view.get();
        ViewParent parent = time == null ? null : time.getParent();
        while (parent instanceof View && !(parent instanceof ViewGroup
                && "com.android.systemui.qs.MiuiNotificationHeaderView"
                .equals(parent.getClass().getName()))) {
            parent = parent.getParent();
        }
        return parent instanceof ViewGroup ? (ViewGroup) parent : null;
    }

    private static void applyIndependentRows(ViewGroup header, TextView time, TextView date) {
        if (header == null || time == null || date == null || !time.isAttachedToWindow()) return;
        NotificationClockConfig settings = config.notificationClock;
        applySettingsShortcut(header, settings.hideSettings);
        if (!settings.enabled) {
            applyCombinedTextMode(time, HORIZONTAL_TIME.view.get(), date, false);
            restoreNativeClockRows(header, time, date);
            return;
        }
        boolean hadClockStack = CLOCK_STACKS.containsKey(header);
        ViewGroup clockContainer = findClockContainer(header);
        LinearLayout clockStack = ensureSeparateClockRows(header, time, date, clockContainer);
        ViewGroup timeRow = directChild(time, header);
        ViewGroup dateRow = directChild(date, header);
        ViewGroup alignmentRow = clockContainer != null ? clockContainer : timeRow;
        boolean layoutOwnedByModule = !hadClockStack || lastExpansionProgress >= 0.999f;
        if (layoutOwnedByModule) {
            applyTimeCenter(TIME, time, alignmentRow, settings.timeCentered);
            applyDateCenter(DATE, date, alignmentRow, false);
        }
        unclip(time, header);
        unclip(date, header);
        if (timeRow != null) unclip(timeRow, header);
        if (dateRow != null) unclip(dateRow, header);

        applyCombinedTextMode(time, HORIZONTAL_TIME.view.get(), date, settings.enabled);

        // The notification header is part of the shared shade header. Its outer height is
        // also consumed while laying out the control-center header, so keep that dimension
        // native and let the clock/text translations handle the visual adjustment.
    }

    private static void applyTimeCenter(ClockState state, TextView time, ViewGroup row,
            boolean centered) {
        if (!centered) {
            if (state.centerApplied) {
                time.setTranslationX(state.nativeTranslationX);
                state.centerApplied = false;
            }
            return;
        }
        if (row == null || row.getWidth() <= 0 || time.getWidth() <= 0) return;
        float left = leftWithin(time, row);
        if (Float.isNaN(left)) return;
        float target = state.nativeTranslationX
                + (row.getWidth() - time.getWidth()) / 2f - left;
        if (Math.abs(time.getTranslationX() - target) > 0.5f) {
            time.setTranslationX(target);
        }
        state.centerApplied = true;
    }

    private static void applyDateCenter(ClockState state, TextView date, ViewGroup row,
            boolean centered) {
        if (!centered) {
            if (state.centerApplied) {
                date.setTranslationX(state.nativeTranslationX);
                state.centerApplied = false;
            }
            return;
        }
        if (row == null || row.getWidth() <= 0 || date.getWidth() <= 0) return;
        float left = leftWithin(date, row);
        if (Float.isNaN(left)) return;
        float target = state.nativeTranslationX
                + (row.getWidth() - date.getWidth()) / 2f - left;
        if (Math.abs(date.getTranslationX() - target) > 0.5f) {
            date.setTranslationX(target);
        }
        state.centerApplied = true;
    }

    private static void applyCombinedTextMode(TextView time, TextView horizontalTime,
            TextView date, boolean combined) {
        applyMultilineMode(TIME, time, combined);
        applyMultilineMode(HORIZONTAL_TIME, horizontalTime, combined);
        if (date == null) return;
        if (combined) {
            DATE.hiddenForCombined = true;
            if (date.getVisibility() != View.GONE) {
                date.setVisibility(View.GONE);
            }
        } else if (DATE.hiddenForCombined) {
            date.setVisibility(DATE.nativeVisibility);
            DATE.hiddenForCombined = false;
        }
    }

    private static void applyMultilineMode(ClockState state, TextView view, boolean combined) {
        if (view == null) return;
        if (combined) {
            if (!state.textModeApplied) {
                view.setSingleLine(false);
                view.setMaxLines(2);
                view.setHorizontallyScrolling(false);
                state.textModeApplied = true;
            }
            return;
        }
        if (state.textModeApplied) {
            view.setSingleLine(state.nativeSingleLine);
            view.setMaxLines(state.nativeMaxLines);
            view.setHorizontallyScrolling(state.nativeHorizontallyScrolling);
            state.textModeApplied = false;
        }
    }

    private static ViewGroup findClockContainer(ViewGroup header) {
        try {
            int id = header.getResources().getIdentifier(
                    "notification_header_clock_container", "id", "com.android.systemui");
            View view = id == 0 ? null : header.findViewById(id);
            return view instanceof ViewGroup ? (ViewGroup) view : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The OEM puts date and big time beside each other; make them two real rows. */
    private static LinearLayout ensureSeparateClockRows(ViewGroup header, TextView time,
            TextView date, ViewGroup container) {
        if (!(container instanceof LinearLayout parent)
                || time.getParent() != parent || date.getParent() != parent) {
            LinearLayout existing = clockStack(header);
            return existing != null && existing.getParent() != null ? existing : null;
        }
        LinearLayout existing = clockStack(header);
        if (existing != null && existing.getParent() == parent) return existing;

        int timeIndex = parent.indexOfChild(time);
        int dateIndex = parent.indexOfChild(date);
        if (timeIndex < 0 || dateIndex < 0) return null;

        int insertIndex = Math.min(timeIndex, dateIndex);
        parent.removeView(time);
        parent.removeView(date);

        LinearLayout stack = new LinearLayout(header.getContext());
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setGravity(Gravity.CENTER_HORIZONTAL);
        stack.setBaselineAligned(false);
        stack.setClipChildren(false);
        stack.setClipToPadding(false);
        LinearLayout.LayoutParams stackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        LinearLayout.LayoutParams timeParams = clockRowParams(
                header, false, time.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams dateParams = clockRowParams(
                header, true, date.getResources().getDisplayMetrics().density);
        stack.addView(time, timeParams);
        stack.addView(date, dateParams);
        parent.addView(stack, insertIndex, stackParams);
        CLOCK_STACKS.put(header, new WeakReference<>(stack));
        return stack;
    }

    private static LinearLayout clockStack(ViewGroup header) {
        WeakReference<LinearLayout> reference = CLOCK_STACKS.get(header);
        return reference == null ? null : reference.get();
    }

    private static void restoreNativeClockRows(ViewGroup header, TextView time, TextView date) {
        LinearLayout stack = clockStack(header);
        ViewGroup parent = TIME.nativeParent.get();
        if (stack == null || parent == null
                || DATE.nativeParent.get() != parent) return;
        int timeIndex = TIME.nativeIndex;
        int dateIndex = DATE.nativeIndex;
        stack.removeView(time);
        stack.removeView(date);
        parent.removeView(stack);
        if (timeIndex <= dateIndex) {
            parent.addView(time, Math.min(timeIndex, parent.getChildCount()), TIME.nativeLayoutParams);
            parent.addView(date, Math.min(dateIndex, parent.getChildCount()), DATE.nativeLayoutParams);
        } else {
            parent.addView(date, Math.min(dateIndex, parent.getChildCount()), DATE.nativeLayoutParams);
            parent.addView(time, Math.min(timeIndex, parent.getChildCount()), TIME.nativeLayoutParams);
        }
        CLOCK_STACKS.remove(header);
    }

    private static LinearLayout.LayoutParams clockRowParams(ViewGroup header, boolean date,
            float density) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        if (date) {
            int id = header.getResources().getIdentifier(
                    "notification_panel_time_date_space", "dimen", "com.android.systemui");
            params.topMargin = id == 0 ? Math.round(2f * density)
                    : header.getResources().getDimensionPixelSize(id);
        }
        return params;
    }

    private static float leftWithin(View view, ViewGroup ancestor) {
        float left = view.getLeft();
        ViewParent parent = view.getParent();
        while (parent instanceof View && parent != ancestor) {
            View child = (View) parent;
            left += child.getLeft();
            parent = child.getParent();
        }
        return parent == ancestor ? left : Float.NaN;
    }

    private static void setWrapHeight(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            view.setLayoutParams(params);
        }
    }

    private static ViewGroup directChild(View view, ViewGroup root) {
        View current = view;
        ViewParent parent = current.getParent();
        while (parent instanceof View && parent != root) {
            current = (View) parent;
            parent = parent.getParent();
        }
        return parent == root && current instanceof ViewGroup ? (ViewGroup) current : null;
    }

    private static void unclip(View view, ViewGroup stop) {
        ViewParent parent = view.getParent();
        while (parent instanceof ViewGroup group) {
            group.setClipChildren(false);
            group.setClipToPadding(false);
            if (group == stop) break;
            parent = group.getParent();
        }
    }

    private static void applyExpandedOffset(ClockState state, int offsetDp, float progress) {
        TextView clock = state.view.get();
        if (clock == null || !clock.isAttachedToWindow()
                || clock.getResources().getConfiguration().orientation
                        != Configuration.ORIENTATION_PORTRAIT) return;
        float nativeTranslation = clock.getTranslationY();
        if (Math.abs(nativeTranslation - state.lastExpansionTranslation) < 0.25f) {
            nativeTranslation -= state.lastExpansionOffsetPx;
        }
        float offsetPx = NotificationClockConfig.expansionOffsetDp(offsetDp, progress)
                * clock.getResources().getDisplayMetrics().density;
        float translation = nativeTranslation + offsetPx;
        state.lastExpansionTranslation = translation;
        state.lastExpansionOffsetPx = offsetPx;
        if (Math.abs(clock.getTranslationY() - translation) > 0.25f) {
            clock.setTranslationY(translation);
        }
    }

    private static boolean enabled(ClockState state) {
        return state != DATE && config.notificationClock.enabled;
    }

    private static void renderIfAttached(ClockState state) {
        TextView clock = state.view.get();
        if (clock != null && clock.isAttachedToWindow()) render(state);
    }

    private static void render(ClockState state) {
        TextView clock = state.view.get();
        if (clock == null) return;
        NotificationClockConfig settings = config.notificationClock;
        boolean systemOwnsGeometry = !Float.isNaN(lastExpansionProgress)
                && lastExpansionProgress < 0.999f;
        if (!enabled(state)) {
            if (state.nativeText != null && !state.nativeText.contentEquals(clock.getText())) {
                clock.setText(state.nativeText);
            }
            if (!systemOwnsGeometry && state.nativeSizePx > 0
                    && clock.getTextSize() != state.nativeSizePx) {
                clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, state.nativeSizePx);
            }
            if (!systemOwnsGeometry && clock.getTranslationY() != state.nativeTranslationY) {
                clock.setTranslationY(state.nativeTranslationY);
            }
            state.lastExpansionTranslation = Float.NaN;
            state.lastExpansionOffsetPx = 0f;
            state.renderedText = state.nativeText;
            state.renderedDateSizeSp = -1;
            state.renderedTimeCentered = false;
            state.renderedDateCentered = false;
            return;
        }
        int sizeSp = settings.sizeSp;
        if (!systemOwnsGeometry && sizeSp > 0) {
            float targetSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    sizeSp, clock.getResources().getDisplayMetrics());
            if (Math.abs(clock.getTextSize() - targetSize) > 0.25f) {
                clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
            }
        } else if (!systemOwnsGeometry && state.nativeSizePx > 0
                && clock.getTextSize() != state.nativeSizePx) {
            clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, state.nativeSizePx);
        }
        int offsetYDp = settings.offsetYDp;
        float translation = state.nativeTranslationY
                + offsetYDp * clock.getResources().getDisplayMetrics().density;
        if (!systemOwnsGeometry && clock.getTranslationY() != translation) {
            clock.setTranslationY(translation);
        }
        state.lastExpansionTranslation = translation;
        state.lastExpansionOffsetPx = offsetYDp * clock.getResources().getDisplayMetrics().density;
        String pattern = settings.combinedPattern();
        ClockTextFormatter.LunarFields lunar = ClockTextFormatter.needsLunar(pattern)
                ? ChineseClockFields.forDate(new Date()) : ClockTextFormatter.LunarFields.EMPTY;
        String weather = config.showWeather
                ? StatusClockController.weatherText(clock.getContext()) : "";
        String desired = ClockTextFormatter.format(new Date(), Locale.getDefault(),
                pattern, config.showWeather, weather, lunar);
        if (desired == null) {
            desired = state.nativeText;
        }
        int dateSizeSp = effectiveDateSizeSp(settings, pattern);
        if (desired != null && (!desired.contentEquals(clock.getText())
                || state.renderedDateSizeSp != dateSizeSp
                || state.renderedTimeCentered != settings.timeCentered
                || state.renderedDateCentered != settings.dateCentered)) {
            clock.setText(styleCombinedText(clock, desired, dateSizeSp,
                            settings.timeCentered, settings.dateCentered),
                    TextView.BufferType.SPANNABLE);
        }
        state.renderedText = desired;
        state.renderedDateSizeSp = dateSizeSp;
        state.renderedTimeCentered = settings.timeCentered;
        state.renderedDateCentered = settings.dateCentered;
        if (clock.isAttachedToWindow()) {
            if (state != DATE && !timeAppliedLogged) {
                timeAppliedLogged = true;
                android.util.Log.i(TAG, "notification time applied sizeSp=" + sizeSp
                        + " offsetDp=" + offsetYDp + " length="
                        + (desired == null ? 0 : desired.length()));
            }
        }
    }

    private static void scheduleSeconds() {
        boolean timeSeconds = config.notificationClock.enabled
                && ClockTextFormatter.showsSeconds(config.notificationClock.combinedPattern());
        SECOND_TICK.update(timeSeconds, TIME.view.get(), HORIZONTAL_TIME.view.get());
    }

    private static int effectiveDateSizeSp(NotificationClockConfig settings, String pattern) {
        if (pattern == null || pattern.indexOf('\n') < 0) return 0;
        return settings.dateSizeSp > 0 ? settings.dateSizeSp : 16;
    }

    private static CharSequence styleCombinedText(TextView clock, String text, int dateSizeSp,
            boolean timeCentered, boolean dateCentered) {
        int newline = text.indexOf('\n');
        SpannableString styled = new SpannableString(text);
        int firstEnd = newline < 0 ? text.length() : newline + 1;
        styled.setSpan(new AlignmentSpan.Standard(timeCentered
                        ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL),
                0, firstEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (newline >= 0 && newline + 1 < text.length()) {
            styled.setSpan(new AlignmentSpan.Standard(dateCentered
                            ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL),
                    newline + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (dateSizeSp > 0) {
                int pixels = Math.max(1, Math.round(dateSizeSp
                        * clock.getResources().getDisplayMetrics().scaledDensity));
                styled.setSpan(new AbsoluteSizeSpan(pixels, false), newline + 1, text.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return styled;
    }

    private static void applySettingsShortcut(ViewGroup header, boolean hidden) {
        View shortcut = findHeaderView(header, "mShortCut", "notification_shade_shortcut");
        if (shortcut == null) return;
        if (!SETTINGS_VISIBILITIES.containsKey(shortcut)) {
            SETTINGS_VISIBILITIES.put(shortcut, shortcut.getVisibility());
        }
        if (hidden) {
            shortcut.setVisibility(View.GONE);
        } else {
            Integer visibility = SETTINGS_VISIBILITIES.get(shortcut);
            shortcut.setVisibility(visibility == null ? View.VISIBLE : visibility);
        }
    }

    private static View findHeaderView(ViewGroup header, String fieldName, String idName) {
        Object value = field(header, fieldName);
        if (value instanceof View) return (View) value;
        try {
            int id = header.getResources().getIdentifier(idName, "id", "com.android.systemui");
            return id == 0 ? null : header.findViewById(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static TextView findHeaderClock(ViewGroup header, String fieldName, String idName) {
        Object value = field(header, fieldName);
        if (value instanceof TextView) return (TextView) value;
        try {
            int id = header.getResources().getIdentifier(idName, "id", "com.android.systemui");
            View view = id == 0 ? null : header.findViewById(id);
            return view instanceof TextView ? (TextView) view : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object field(Object target, String name) {
        Class<?> type = target == null ? null : target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}

package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Applies clock text on the UI thread and reads Xiaomi weather off the UI thread. */
final class StatusClockController {
    private static final Uri WEATHER_URI = Uri.parse("content://weather/actualWeatherData/1");
    private static final long WEATHER_REFRESH_MS = 5 * 60 * 1000L;
    private static final long WEATHER_CHANGE_REFRESH_MS = 30 * 1000L;
    private static final LongSupplier SYSTEM_WALL_TIME = System::currentTimeMillis;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WEATHER_WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "fusion-weather");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<TextView, ClockState> CLOCKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static LongSupplier wallTime = SYSTEM_WALL_TIME;
    private static FusionConfig config = FusionConfig.defaults();
    private static Context appContext;
    private static String weatherText = "";
    private static long lastWeatherQuery;
    private static boolean weatherLoading;
    private static boolean weatherObserverRegistered;
    private static final AtomicBoolean WEATHER_ERROR_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean RENDER_ERROR_LOGGED = new AtomicBoolean();
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static final VisibleClockTicker CLOCK_TICK = new VisibleClockTicker(
            () -> renderClocks(true), () -> wallTime.getAsLong(), true);

    private StatusClockController() {
    }

    static boolean hasAttachedViews() {
        for (TextView clock : registeredClocks())
            if (clock != null && clock.isAttachedToWindow()) return true;
        return false;
    }

    static void onNativeUpdate(TextView clock, FusionConfig next) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> onNativeUpdate(clock, next));
            return;
        }
        ClockState state = CLOCKS.computeIfAbsent(clock, ClockState::new);
        if (state.lastAppliedTextSizePx < 0f
                || Math.abs(clock.getTextSize() - state.lastAppliedTextSizePx) > 0.5f) {
            state.nativeTextSizePx = clock.getTextSize();
        }
        config = next;
        Context application = clock.getContext().getApplicationContext();
        appContext = application != null ? application : clock.getContext();
        render(clock, state);
        applyTextSize(clock, state);
        updateTicker();
        refreshWeather(false);
    }

    static void onConfigChanged(FusionConfig next) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> onConfigChanged(next));
            return;
        }
        boolean weatherEnabledNow = next.showWeather && !config.showWeather;
        config = next;
        for (TextView clock : registeredClocks()) {
            ClockState state = CLOCKS.get(clock);
            if (state != null) {
                render(clock, state);
                applyTextSize(clock, state);
            }
        }
        updateTicker();
        refreshWeather(weatherEnabledNow);
    }

    static String weatherText(Context context) {
        if (context != null && appContext == null) {
            Context application = context.getApplicationContext();
            appContext = application != null ? application : context;
        }
        if (config.showWeather) {
            refreshWeather(true);
            return weatherText;
        }
        return "";
    }

    static void onWeatherChanged() {
        NotificationCenterClockController.onWeatherChanged();
    }

    private static List<TextView> registeredClocks() {
        synchronized (CLOCKS) { return new ArrayList<>(CLOCKS.keySet()); }
    }

    private static void renderClocks(boolean visibleOnly) {
        for (TextView clock : registeredClocks()) {
            ClockState state = CLOCKS.get(clock);
            if (state != null && (!visibleOnly || VisibleClockTicker.isVisible(clock))) render(clock, state);
        }
    }

    private static void render(TextView clock, ClockState state) {
        try {
            renderSafely(clock, state);
        } catch (Throwable error) {
            if (RENDER_ERROR_LOGGED.compareAndSet(false, true)) {
                ModuleLog.w("FusionStatusBar", "custom clock render failed", error);
            }
        }
    }

    private static void applyTextSize(TextView clock, ClockState state) {
        if (config.statusClockSizeSp > 0) {
            clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,
                    config.statusClockSizeSp);
            state.lastAppliedTextSizePx = clock.getTextSize();
        } else {
            restoreNativeTextSize(clock, state);
        }
    }

    private static void restoreNativeTextSize(TextView clock, ClockState state) {
        if (state.lastAppliedTextSizePx < 0f) return;
        if (Math.abs(clock.getTextSize() - state.lastAppliedTextSizePx) > 0.5f) {
            state.nativeTextSizePx = clock.getTextSize();
        } else if (state.nativeTextSizePx > 0f
                && Math.abs(clock.getTextSize() - state.nativeTextSizePx) > 0.5f) {
            clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, state.nativeTextSizePx);
        }
        state.lastAppliedTextSizePx = -1f;
    }

    private static void renderSafely(TextView clock, ClockState state) {
        String observed = clock.getText().toString();
        if (!observed.equals(state.renderedText)) state.nativeText = observed;
        String desired = state.nativeText;
        if (config.customClock) {
            Date now = new Date(wallTime.getAsLong());
            ClockTextFormatter.LunarFields lunar = ClockTextFormatter.needsLunar(config.clockPattern)
                    ? ChineseClockFields.forDate(now) : ClockTextFormatter.LunarFields.EMPTY;
            String formatted = ClockTextFormatter.format(now, Locale.getDefault(),
                    config.clockPattern, config.showWeather, weatherText, lunar);
            if (formatted != null) {
                desired = formatted;
            }
        } else {
            desired = ClockTextFormatter.appendWeather(state.nativeText,
                    config.showWeather, weatherText);
        }
        if (!desired.contentEquals(clock.getText())) {
            clock.setText(desired);
        }
        state.renderedText = desired;
    }

    private static void updateTicker() {
        if (!config.customClock) {
            CLOCK_TICK.close();
            return;
        }
        long interval = ClockTextFormatter.showsSeconds(config.clockPattern) ? 1_000L : 60_000L;
        CLOCK_TICK.update(interval, registeredClocks().toArray(new TextView[0]));
    }

    static void setWallTimeForTest(LongSupplier source) {
        wallTime = source == null ? SYSTEM_WALL_TIME : source;
    }

    private static void refreshWeather(boolean changed) {
        if (!config.showWeather || appContext == null || weatherLoading) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long interval = changed ? WEATHER_CHANGE_REFRESH_MS : WEATHER_REFRESH_MS;
        if (lastWeatherQuery != 0 && now - lastWeatherQuery < interval) {
            return;
        }
        lastWeatherQuery = now;
        weatherLoading = true;
        observeWeather();
        Context context = appContext;
        WEATHER_WORKER.execute(() -> {
            String updated = readWeather(context);
            MAIN.post(() -> {
                weatherLoading = false;
                if (!Objects.equals(weatherText, updated)) {
                    weatherText = updated;
                    if (config.showWeather) renderClocks(false);
                    onWeatherChanged();
                }
            });
        });
    }

    private static void observeWeather() {
        if (weatherObserverRegistered) {
            return;
        }
        try {
            appContext.getContentResolver().registerContentObserver(WEATHER_URI, true,
                    new ContentObserver(MAIN) {
                        @Override
                        public void onChange(boolean selfChange) {
                            refreshWeather(true);
                        }
                    });
            weatherObserverRegistered = true;
        } catch (Throwable error) {
            logWeatherError(error);
        }
    }

    private static String readWeather(Context context) {
        try (Cursor cursor = context.getContentResolver().query(
                WEATHER_URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return "";
            }
            int conditionColumn = cursor.getColumnIndex("description");
            int temperatureColumn = cursor.getColumnIndex("temperature");
            String condition = conditionColumn < 0 ? "" : cursor.getString(conditionColumn);
            String temperature = temperatureColumn < 0 ? "" : cursor.getString(temperatureColumn);
            return ClockTextFormatter.weatherText(condition, temperature);
        } catch (Throwable error) {
            logWeatherError(error);
            return "";
        }
    }

    private static void logWeatherError(Throwable error) {
        if (WEATHER_ERROR_LOGGED.compareAndSet(false, true)) {
            ModuleLog.w("FusionStatusBar", "weather provider unavailable", error);
        }
    }

    private static final class ClockState {
        String nativeText;
        String renderedText;
        float nativeTextSizePx;
        float lastAppliedTextSizePx = -1f;

        ClockState(TextView clock) {
            nativeText = clock.getText().toString();
            nativeTextSizePx = clock.getTextSize();
        }
    }
}

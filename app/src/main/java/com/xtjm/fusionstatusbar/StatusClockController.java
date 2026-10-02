package com.xtjm.fusionstatusbar;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Applies clock text on the UI thread and reads Xiaomi weather off the UI thread. */
final class StatusClockController {
    private static final Uri WEATHER_URI = Uri.parse("content://weather/actualWeatherData/1");
    private static final long WEATHER_REFRESH_MS = 5 * 60 * 1000L;
    private static final long WEATHER_CHANGE_REFRESH_MS = 30 * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WEATHER_WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "fusion-weather");
        thread.setDaemon(true);
        return thread;
    });

    private static WeakReference<TextView> clockRef = new WeakReference<>(null);
    private static FusionConfig config = FusionConfig.defaults();
    private static Context appContext;
    private static String nativeText = "";
    private static String renderedText;
    private static String weatherText = "";
    private static long lastWeatherQuery;
    private static boolean weatherLoading;
    private static boolean weatherObserverRegistered;
    private static final AtomicBoolean WEATHER_ERROR_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean RENDER_ERROR_LOGGED = new AtomicBoolean();
    // The ticker retains only weak view references and an application context.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static final VisibleSecondTicker SECOND_TICK = new VisibleSecondTicker(() -> {
        TextView clock = clockRef.get();
        if (clock != null && needsSeconds()) render(clock);
    });

    private StatusClockController() {
    }

    static boolean hasAttachedViews() {
        TextView clock = clockRef.get();
        return clock != null && clock.isAttachedToWindow();
    }

    static void onNativeUpdate(TextView clock, FusionConfig next) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> onNativeUpdate(clock, next));
            return;
        }
        TextView previous = clockRef.get();
        String observed = clock.getText().toString();
        if (previous != clock) {
            clockRef = new WeakReference<>(clock);
            nativeText = observed;
            renderedText = null;
        } else if (!observed.equals(renderedText)) {
            nativeText = observed;
        }
        config = next;
        Context application = clock.getContext().getApplicationContext();
        appContext = application != null ? application : clock.getContext();
        render(clock);
        scheduleSeconds();
        refreshWeather(false);
    }

    static void onConfigChanged(FusionConfig next) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> onConfigChanged(next));
            return;
        }
        boolean weatherEnabledNow = next.showWeather && !config.showWeather;
        config = next;
        TextView clock = clockRef.get();
        if (clock != null) {
            render(clock);
        }
        scheduleSeconds();
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

    private static void render(TextView clock) {
        try {
            renderSafely(clock);
        } catch (Throwable error) {
            if (RENDER_ERROR_LOGGED.compareAndSet(false, true)) {
                Log.w("FusionStatusBar", "custom clock render failed", error);
            }
        }
    }

    private static void renderSafely(TextView clock) {
        String desired = nativeText;
        if (config.customClock) {
            Date now = new Date();
            ClockTextFormatter.LunarFields lunar = ClockTextFormatter.needsLunar(config.clockPattern)
                    ? ChineseClockFields.forDate(now) : ClockTextFormatter.LunarFields.EMPTY;
            String formatted = ClockTextFormatter.format(now, Locale.getDefault(),
                    config.clockPattern, config.showWeather, weatherText, lunar);
            if (formatted != null) {
                desired = formatted;
            }
        } else {
            desired = ClockTextFormatter.appendWeather(nativeText,
                    config.showWeather, weatherText);
        }
        if (!desired.contentEquals(clock.getText())) {
            clock.setText(desired);
        }
        renderedText = desired;
    }

    private static boolean needsSeconds() {
        return config.customClock && ClockTextFormatter.showsSeconds(config.clockPattern);
    }

    private static void scheduleSeconds() {
        SECOND_TICK.update(needsSeconds(), clockRef.get());
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
                    TextView clock = clockRef.get();
                    if (clock != null && config.showWeather) {
                        render(clock);
                    }
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
            Log.w("FusionStatusBar", "weather provider unavailable", error);
        }
    }
}

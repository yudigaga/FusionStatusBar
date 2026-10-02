package com.xtjm.fusionstatusbar;

import android.icu.util.ChineseCalendar;
import android.util.Log;

import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uses ICU for lunar conversion and caches its result for the current local day. */
final class ChineseClockFields {
    private static final AtomicBoolean ERROR_LOGGED = new AtomicBoolean();
    private static long cachedDay = Long.MIN_VALUE;
    private static String cachedZone = "";
    private static ClockTextFormatter.LunarFields cached = ClockTextFormatter.LunarFields.EMPTY;

    private ChineseClockFields() {
    }

    static synchronized ClockTextFormatter.LunarFields forDate(Date date) {
        TimeZone zone = TimeZone.getDefault();
        long millis = date.getTime();
        long localDay = Math.floorDiv(millis + zone.getOffset(millis), 86_400_000L);
        if (cachedDay == localDay && cachedZone.equals(zone.getID())) {
            return cached;
        }
        try {
            ChineseCalendar lunar = new ChineseCalendar(date);
            int month = lunar.get(android.icu.util.Calendar.MONTH) + 1;
            int day = lunar.get(android.icu.util.Calendar.DAY_OF_MONTH);
            boolean leap = lunar.get(android.icu.util.Calendar.IS_LEAP_MONTH) == 1;
            cached = new ClockTextFormatter.LunarFields(
                    ChineseDateNames.lunarMonth(month, leap), ChineseDateNames.lunarDay(day));
        } catch (Throwable error) {
            cached = ClockTextFormatter.LunarFields.EMPTY;
            if (ERROR_LOGGED.compareAndSet(false, true)) {
                Log.w("FusionStatusBar", "lunar calendar unavailable", error);
            }
        }
        cachedDay = localDay;
        cachedZone = zone.getID();
        return cached;
    }
}

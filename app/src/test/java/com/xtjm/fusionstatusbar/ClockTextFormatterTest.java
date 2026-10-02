package com.xtjm.fusionstatusbar;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClockTextFormatterTest {
    private static final Date NOW = new Date(1727055600000L);

    @Test
    public void customPatternPlacesWeatherAtToken() {
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(NOW);
        assertEquals(time + " Sunny's 26", ClockTextFormatter.format(
                NOW, Locale.US, "HH:mm tq", true, "Sunny's 26"));
        assertEquals("tq " + time + " Sunny 26", ClockTextFormatter.format(
                NOW, Locale.US, "'tq' HH:mm tq", true, "Sunny 26"));
    }

    @Test
    public void weatherAppendsWithoutTokenAndDisappearsWhenUnavailable() {
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(NOW);
        assertEquals(time + " Sunny 26", ClockTextFormatter.format(
                NOW, Locale.US, "HH:mm", true, "Sunny 26"));
        assertEquals(time, ClockTextFormatter.format(
                NOW, Locale.US, "HH:mm tq", true, ""));
        assertEquals(time, ClockTextFormatter.format(
                NOW, Locale.US, "HH:mm tq", false, "Sunny 26"));
        assertEquals(time + " Sunny 26", ClockTextFormatter.appendWeather(
                time, true, "Sunny 26"));
    }

    @Test
    public void invalidPatternIsRejectedBeforeSystemUiUsesIt() {
        assertFalse(ClockTextFormatter.isValidPattern(""));
        assertFalse(ClockTextFormatter.isValidPattern("HH:mm Q"));
        assertTrue(ClockTextFormatter.isValidPattern("HH:mm\ntq"));
        assertFalse(ClockTextFormatter.isValidPattern("HH:mm\rtq"));
        assertTrue(ClockTextFormatter.isValidPattern("yyyy/MM/dd HH:mm:ss tq"));
        assertTrue(ClockTextFormatter.isValidPattern("yy/M/d N月e E a aaH:mm I时"));
        assertTrue(ClockTextFormatter.showsSeconds("HH:mm:ss"));
        assertFalse(ClockTextFormatter.showsSeconds("HH:mm 'seconds'"));
        assertTrue(ClockTextFormatter.needsLunar("N月e"));
        assertFalse(ClockTextFormatter.needsLunar("yyyy/MM/dd"));
        assertFalse(ClockTextFormatter.isValidPattern("HH:mm 'unfinished"));
    }

    @Test
    public void documentedTokensRenderTheirDefinedValues() {
        Date sample = new GregorianCalendar(2023, Calendar.FEBRUARY, 23, 18, 8, 9).getTime();
        ClockTextFormatter.LunarFields lunar = new ClockTextFormatter.LunarFields("二", "初四");
        assertEquals("2023 23 02 2 二 23 23 初四 周四 下午 傍晚 18 18 06 6 08 09 酉",
                ClockTextFormatter.format(sample, Locale.CHINA,
                        "yyyy yy MM M N dd d e E a aa HH H hh h mm ss I",
                        false, "", lunar));
        assertEquals("E 周四", ClockTextFormatter.format(sample, Locale.CHINA,
                "'E' E", false, "", lunar));
    }

    @Test
    public void mergedTimeAndDatePatternPreservesLineBreak() {
        Date sample = new GregorianCalendar(2023, Calendar.FEBRUARY, 23, 18, 8, 9).getTime();
        assertEquals("18:08\n02/23 周四", ClockTextFormatter.format(
                sample, Locale.CHINA, "HH:mm\nMM/dd E", false, ""));
    }

    @Test
    public void zeroPaddedAndTwelveHourValuesHandleMorningAndMidnight() {
        Date morning = new GregorianCalendar(2023, Calendar.FEBRUARY, 2, 9, 9, 9).getTime();
        Date midnight = new GregorianCalendar(2023, Calendar.FEBRUARY, 2, 0, 9, 9).getTime();
        assertEquals("02 2 02 2 09 9 09 9 09 09 上午 巳",
                ClockTextFormatter.format(morning, Locale.CHINA,
                        "MM M dd d HH H hh h mm ss a I", false, ""));
        assertEquals("00 0 12 12 凌晨 子", ClockTextFormatter.format(midnight,
                Locale.CHINA, "HH H hh h aa I", false, ""));
    }

    @Test
    public void weatherColumnsCanBeMissingWithoutFakeWeather() {
        assertEquals("", ClockTextFormatter.weatherText(null, null));
        assertEquals("晴 26℃", ClockTextFormatter.weatherText(" 晴 ", " 26℃ "));
        assertEquals("多云 转晴 26℃", ClockTextFormatter.weatherText("多云\n转晴", "26℃"));
    }
}

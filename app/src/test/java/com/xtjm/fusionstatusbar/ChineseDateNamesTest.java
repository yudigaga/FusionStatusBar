package com.xtjm.fusionstatusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChineseDateNamesTest {
    @Test
    public void lunarMonthAndDayNamesCoverBoundaries() {
        assertEquals("正", ChineseDateNames.lunarMonth(1, false));
        assertEquals("闰二", ChineseDateNames.lunarMonth(2, true));
        assertEquals("冬", ChineseDateNames.lunarMonth(11, false));
        assertEquals("腊", ChineseDateNames.lunarMonth(12, false));
        assertEquals("初一", ChineseDateNames.lunarDay(1));
        assertEquals("初十", ChineseDateNames.lunarDay(10));
        assertEquals("十九", ChineseDateNames.lunarDay(19));
        assertEquals("二十", ChineseDateNames.lunarDay(20));
        assertEquals("廿九", ChineseDateNames.lunarDay(29));
        assertEquals("三十", ChineseDateNames.lunarDay(30));
    }

    @Test
    public void precisePeriodAndEarthlyHourFollowLocalHour() {
        assertEquals("凌晨", ChineseDateNames.period(3));
        assertEquals("傍晚", ChineseDateNames.period(18));
        assertEquals("卯", ChineseDateNames.earthlyHour(5));
        assertEquals("酉", ChineseDateNames.earthlyHour(18));
        assertEquals("亥", ChineseDateNames.earthlyHour(22));
        assertEquals("子", ChineseDateNames.earthlyHour(23));
    }
}

package com.xtjm.fusionstatusbar;

/** Chinese names layered on calendar fields supplied by the platform. */
final class ChineseDateNames {
    private static final String[] MONTHS = {
            "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"
    };
    private static final String[] DIGITS = {
            "", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"
    };
    private static final String[] WEEKDAYS = {
            "周日", "周一", "周二", "周三", "周四", "周五", "周六"
    };
    private static final String[] BRANCHES = {
            "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"
    };

    private ChineseDateNames() {
    }

    static String lunarMonth(int month, boolean leap) {
        if (month < 1 || month > 12) {
            return "";
        }
        return (leap ? "闰" : "") + MONTHS[month - 1];
    }

    static String lunarDay(int day) {
        if (day < 1 || day > 30) {
            return "";
        }
        if (day <= 10) {
            return "初" + DIGITS[day];
        }
        if (day < 20) {
            return "十" + DIGITS[day - 10];
        }
        if (day == 20) {
            return "二十";
        }
        if (day < 30) {
            return "廿" + DIGITS[day - 20];
        }
        return "三十";
    }

    static String weekday(int dayOfWeek) {
        return dayOfWeek >= 1 && dayOfWeek <= 7 ? WEEKDAYS[dayOfWeek - 1] : "";
    }

    static String period(int hour) {
        if (hour < 0 || hour > 23) {
            return "";
        }
        if (hour < 5) return "凌晨";
        if (hour < 8) return "清晨";
        if (hour < 11) return "上午";
        if (hour < 13) return "中午";
        if (hour < 17) return "下午";
        if (hour < 19) return "傍晚";
        if (hour < 23) return "晚上";
        return "深夜";
    }

    static String earthlyHour(int hour) {
        return hour >= 0 && hour <= 23 ? BRANCHES[((hour + 1) % 24) / 2] : "";
    }
}

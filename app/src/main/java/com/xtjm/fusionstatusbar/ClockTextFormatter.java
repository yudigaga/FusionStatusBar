package com.xtjm.fusionstatusbar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

/** Parses the documented HyperOS clock tokens without treating literal text as a date field. */
final class ClockTextFormatter {
    static final String DEFAULT_PATTERN = "HH:mm";
    static final int MAX_PATTERN_LENGTH = 80;

    static final class LunarFields {
        static final LunarFields EMPTY = new LunarFields("", "");
        final String month;
        final String day;

        LunarFields(String month, String day) {
            this.month = month;
            this.day = day;
        }
    }

    private enum Token {
        YEAR4("yyyy"), YEAR2("yy"), MONTH2("MM"), MONTH("M"),
        LUNAR_MONTH("N"), DAY2("dd"), DAY("d"), LUNAR_DAY("e"), WEEKDAY("E"),
        PERIOD_PRECISE("aa"), PERIOD("a"), HOUR24_2("HH"), HOUR24("H"),
        HOUR12_2("hh"), HOUR12("h"), MINUTE2("mm"), SECOND2("ss"),
        EARTHLY_HOUR("I"), WEATHER("tq");

        final String pattern;

        Token(String pattern) {
            this.pattern = pattern;
        }
    }

    private static final Token[] TOKENS = Token.values();

    private static final class Part {
        final Token token;
        final String literal;

        Part(Token token, String literal) {
            this.token = token;
            this.literal = literal;
        }
    }

    private static final class ParsedPattern {
        final List<Part> parts = new ArrayList<>();
        boolean hasWeather;
        boolean hasSeconds;
        boolean hasLunar;
    }

    private ClockTextFormatter() {
    }

    static boolean isValidPattern(String pattern) {
        return parse(pattern) != null;
    }

    static boolean showsSeconds(String pattern) {
        ParsedPattern parsed = parse(pattern);
        return parsed != null && parsed.hasSeconds;
    }

    static boolean needsLunar(String pattern) {
        ParsedPattern parsed = parse(pattern);
        return parsed != null && parsed.hasLunar;
    }

    static String format(Date now, Locale locale, String pattern,
            boolean showWeather, String weather) {
        return format(now, locale, pattern, showWeather, weather, LunarFields.EMPTY);
    }

    static String format(Date now, Locale locale, String pattern,
            boolean showWeather, String weather, LunarFields lunar) {
        ParsedPattern parsed = parse(pattern);
        if (parsed == null || now == null) {
            return null;
        }
        GregorianCalendar calendar = new GregorianCalendar(
                locale == null ? Locale.getDefault() : locale);
        calendar.setTime(now);
        LunarFields safeLunar = lunar == null ? LunarFields.EMPTY : lunar;
        String weatherText = showWeather && weather != null ? weather.trim() : "";
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        StringBuilder output = new StringBuilder(pattern.length() + weatherText.length());
        for (Part part : parsed.parts) {
            if (part.token == null) {
                output.append(part.literal);
                continue;
            }
            switch (part.token) {
                case YEAR4 -> appendPadded(output, year, 4);
                case YEAR2 -> appendPadded(output, year % 100, 2);
                case MONTH2 -> appendPadded(output, month, 2);
                case MONTH -> output.append(month);
                case LUNAR_MONTH -> output.append(safeLunar.month);
                case DAY2 -> appendPadded(output, day, 2);
                case DAY -> output.append(day);
                case LUNAR_DAY -> output.append(safeLunar.day);
                case WEEKDAY -> output.append(ChineseDateNames.weekday(
                        calendar.get(Calendar.DAY_OF_WEEK)));
                case PERIOD_PRECISE -> output.append(ChineseDateNames.period(hour));
                case PERIOD -> output.append(hour < 12 ? "上午" : "下午");
                case HOUR24_2 -> appendPadded(output, hour, 2);
                case HOUR24 -> output.append(hour);
                case HOUR12_2 -> appendPadded(output, hour % 12 == 0 ? 12 : hour % 12, 2);
                case HOUR12 -> output.append(hour % 12 == 0 ? 12 : hour % 12);
                case MINUTE2 -> appendPadded(output, calendar.get(Calendar.MINUTE), 2);
                case SECOND2 -> appendPadded(output, calendar.get(Calendar.SECOND), 2);
                case EARTHLY_HOUR -> output.append(ChineseDateNames.earthlyHour(hour));
                case WEATHER -> output.append(weatherText);
            }
        }
        if (showWeather && !parsed.hasWeather && !weatherText.isEmpty()) {
            output.append(' ').append(weatherText);
        }
        return output.toString().trim();
    }

    private static ParsedPattern parse(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()
                || pattern.length() > MAX_PATTERN_LENGTH
                || pattern.indexOf('\r') >= 0) {
            return null;
        }
        ParsedPattern parsed = new ParsedPattern();
        StringBuilder literal = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < pattern.length();) {
            char character = pattern.charAt(i);
            if (character == '\'') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') {
                    literal.append('\'');
                    i += 2;
                } else {
                    quoted = !quoted;
                    i++;
                }
                continue;
            }
            if (quoted || !isAsciiLetter(character)) {
                literal.append(character);
                i++;
                continue;
            }
            Token matched = null;
            for (Token token : TOKENS) {
                if (pattern.startsWith(token.pattern, i)) {
                    matched = token;
                    break;
                }
            }
            if (matched == null) {
                return null;
            }
            if (literal.length() > 0) {
                parsed.parts.add(new Part(null, literal.toString()));
                literal.setLength(0);
            }
            parsed.parts.add(new Part(matched, null));
            parsed.hasWeather |= matched == Token.WEATHER;
            parsed.hasSeconds |= matched == Token.SECOND2;
            parsed.hasLunar |= matched == Token.LUNAR_MONTH || matched == Token.LUNAR_DAY;
            i += matched.pattern.length();
        }
        if (quoted) {
            return null;
        }
        if (literal.length() > 0) {
            parsed.parts.add(new Part(null, literal.toString()));
        }
        return parsed;
    }

    private static boolean isAsciiLetter(char character) {
        return character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z';
    }

    private static void appendPadded(StringBuilder output, int value, int minimumWidth) {
        String text = Integer.toString(value);
        for (int i = text.length(); i < minimumWidth; i++) {
            output.append('0');
        }
        output.append(text);
    }

    static String appendWeather(String nativeTime, boolean showWeather, String weather) {
        if (!showWeather || weather == null || weather.trim().isEmpty()) {
            return nativeTime;
        }
        return nativeTime + " " + weather.trim();
    }

    static String weatherText(String description, String temperature) {
        String condition = description == null ? "" : description.replaceAll("\\s+", " ").trim();
        String degree = temperature == null ? "" : temperature.replaceAll("\\s+", " ").trim();
        if (condition.length() > 12) {
            condition = condition.substring(0, 12);
        }
        if (degree.length() > 8) {
            degree = degree.substring(0, 8);
        }
        return (condition + " " + degree).trim();
    }
}
